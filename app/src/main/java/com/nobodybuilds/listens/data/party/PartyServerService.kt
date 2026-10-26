package com.nobodybuilds.listens.data.party

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.nobodybuilds.listens.MainActivity
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.MusicService
import com.nobodybuilds.listens.utils.MediaItemBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import javax.inject.Inject

/**
 * Foreground service that runs the party web server on the Wi-Fi interface only, and feeds
 * approved party requests into the player through a Media3 [MediaController] connected to
 * [MusicService]'s session (the same path "Play next" uses). Runs only while a party is on.
 */
@AndroidEntryPoint
class PartyServerService : Service() {

    @Inject lateinit var manager: PartyManager
    @Inject lateinit var musicRepository: MusicRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    /** Guards [server] + [stopping] together, so a server that finishes starting after stop is stopped too. */
    private val serverLock = Any()
    @Volatile private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var partyNetwork: Network? = null
    @Volatile private var hostIp: String? = null
    @Volatile private var stopping = false
    private var started = false

    // Player bridge bookkeeping (main thread only).
    private var transitionCount = 0L
    private var stagedAtTransition = 0L

    private val libraryMutex = Mutex()
    @Volatile private var library: PartyLibraryIndex? = null
    @Volatile private var librarySongs: Map<String, Song> = emptyMap()
    @Volatile private var libraryLoadedAt = 0L
    private val artCache = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }
    private var pageHtml: String = ""

    private val endRunnable = Runnable { stopParty("Party time is up") }

    /** Wall-clock end of the party (0 = not running). Uptime timers stall in deep sleep, so this is the source of truth. */
    @Volatile private var endsAt = 0L
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            transitionCount++
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            ) reconcile()
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) publishNowPlaying()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!enterForeground(0)) return START_NOT_STICKY
                if (!started) {
                    started = true
                    val duration = intent.getLongExtra(EXTRA_DURATION_MS, DEFAULT_DURATION_MS)
                        .coerceIn(10 * 60_000L, 4 * 3_600_000L)
                    startParty(duration)
                }
            }
            ACTION_END -> stopParty(null)
            ACTION_TIME_UP -> if (started) {
                if (!checkExpired()) scheduleEndAlarm() // fired early (inexact): re-arm
            } else {
                enterForeground(0)
                stopParty(null)
            }
            else -> {
                // Restarted without a session (process death) — nothing to resume.
                enterForeground(0)
                stopParty(null)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (!stopping) stopParty(null)
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- lifecycle

    private fun startParty(durationMs: Long) {
        val wifi = findWifiAddress()
        if (wifi == null) {
            manager.onServerFailed("Connect to Wi-Fi to start a party")
            stopSelfQuietly()
            return
        }
        partyNetwork = wifi.first
        hostIp = wifi.second
        pageHtml = runCatching { assets.open("party/index.html").bufferedReader().use { it.readText() } }
            .getOrElse { "<!doctype html><title>Party</title><p>Party page missing.</p>" }

        scope.launch {
            val ip = wifi.second
            val port = pickPort(ip)
            val created = try {
                embeddedServer(CIO, port = port, host = ip) { routing { partyRoutes() } }
                    .start(wait = false)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Party server failed to start")
                main.post {
                    manager.onServerFailed("Couldn't start the party server")
                    stopSelfQuietly()
                }
                return@launch
            }
            // Party ended while the server was starting: stop it right away instead of leaking it.
            val kept = synchronized(serverLock) {
                if (stopping) false else { server = created; true }
            }
            if (!kept) {
                runCatching { created.stop(200, 800) }
                return@launch
            }
            val now = System.currentTimeMillis()
            main.post {
                if (stopping) return@post
                endsAt = now + durationMs
                manager.onServerStarted(ip, port, now, endsAt)
                main.postDelayed(endRunnable, durationMs)
                scheduleEndAlarm()
                acquireLocks(durationMs + LOCK_MARGIN_MS)
                registerWifiWatch()
                connectController()
                manager.queueListener = { main.post { reconcile() } }
            }
            loadLibrary(force = true)
        }

        scope.launch {
            manager.state.map { it.guests.size to it.status }.distinctUntilChanged().collect { (guests, status) ->
                if (status == PartyStatus.Running && !stopping) {
                    runCatching {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(guests))
                    }
                }
            }
        }
    }

    private fun stopParty(reason: String?) {
        val s = synchronized(serverLock) {
            if (stopping) return
            stopping = true
            server.also { server = null }
        }
        manager.queueListener = null
        main.removeCallbacks(endRunnable)
        endsAt = 0L
        cancelEndAlarm()
        releaseLocks()
        networkCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        if (s != null) Thread({ runCatching { s.stop(200, 800) } }, "party-server-stop").start()
        releaseController()
        artCache.evictAll()
        library = null
        librarySongs = emptyMap()
        manager.onStopped(reason)
        stopSelfQuietly()
    }

    /** True (and ends the party) once the wall-clock [endsAt] has passed. Any thread. */
    private fun checkExpired(): Boolean {
        val e = endsAt
        if (e <= 0L || System.currentTimeMillis() < e) return false
        main.post { stopParty("Party time is up") }
        return true
    }

    /** Wakes the device at [endsAt] even in deep sleep, where the postDelayed uptime timer doesn't advance. */
    private fun scheduleEndAlarm() {
        val at = endsAt.takeIf { it > 0L } ?: return
        runCatching {
            val am = getSystemService(AlarmManager::class.java) ?: return
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endAlarmIntent())
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endAlarmIntent())
        }.onFailure { Timber.tag(TAG).w(it, "Could not schedule party end alarm") }
    }

    private fun cancelEndAlarm() {
        runCatching { getSystemService(AlarmManager::class.java)?.cancel(endAlarmIntent()) }
    }

    private fun endAlarmIntent(): PendingIntent = PendingIntent.getService(
        this, REQUEST_END_ALARM,
        Intent(this, PartyServerService::class.java).setAction(ACTION_TIME_UP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Keeps Wi-Fi responsive and the CPU awake for guests' requests while the party runs. */
    private fun acquireLocks(timeoutMs: Long) {
        runCatching {
            wifiLock = getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "NobodyListens:Party")
                ?.apply { setReferenceCounted(false); acquire() }
        }.onFailure { Timber.tag(TAG).w(it, "No Wi-Fi lock for party") }
        runCatching {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NobodyListens:Party")
                ?.apply { setReferenceCounted(false); acquire(timeoutMs) }
        }.onFailure { Timber.tag(TAG).w(it, "No wake lock for party") }
    }

    private fun releaseLocks() {
        wifiLock?.let { l -> runCatching { if (l.isHeld) l.release() } }
        wifiLock = null
        wakeLock?.let { l -> runCatching { if (l.isHeld) l.release() } }
        wakeLock = null
    }

    private fun stopSelfQuietly() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    // ---------------------------------------------------------- notification

    private fun enterForeground(guests: Int): Boolean = runCatching {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Party queue", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while friends can add songs from your library" }
            )
        }
        startForeground(NOTIFICATION_ID, buildNotification(guests), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        true
    }.getOrElse {
        Timber.tag(TAG).e(it, "Party service couldn't enter foreground")
        manager.onServerFailed("Couldn't start the party")
        stopSelf()
        false
    }

    private fun buildNotification(guests: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val end = PendingIntent.getService(
            this, 1,
            Intent(this, PartyServerService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val guestText = if (guests == 1) "1 guest" else "$guests guests"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.nobodybuilds.listens.shared.R.drawable.nl_mark_mono)
            .setContentTitle("Party queue is on · $guestText")
            .setContentText(manager.state.value.baseUrl?.let { "Join at $it · PIN ${manager.state.value.pin}" } ?: "Starting…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "End", end)
            .build()
    }

    // ---------------------------------------------------------------- network

    /** Best Wi-Fi (or Ethernet) IPv4 address — the server binds to it and nothing else. */
    private fun findWifiAddress(): Pair<Network, String>? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val active = cm.activeNetwork
        @Suppress("DEPRECATION")
        val candidates = cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) return@mapNotNull null
            val ip = cm.getLinkProperties(network)?.let(::ipv4Of) ?: return@mapNotNull null
            Triple(network, ip, network == active)
        }
        return candidates.sortedByDescending { it.third }.firstOrNull()?.let { it.first to it.second }
    }

    private fun ipv4Of(lp: LinkProperties): String? = lp.linkAddresses
        .map { it.address }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        ?.hostAddress

    private fun registerWifiWatch() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (network == partyNetwork) main.post { stopParty("Wi-Fi disconnected, party ended") }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network == partyNetwork && ipv4Of(linkProperties) != hostIp) {
                    main.post { stopParty("Wi-Fi address changed, party ended") }
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, cb); networkCallback = cb }
            .onFailure { Timber.tag(TAG).w(it, "Couldn't watch Wi-Fi") }
    }

    private fun pickPort(ip: String): Int {
        fun free(port: Int): Boolean = runCatching {
            ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress(ip, port)); true }
        }.getOrDefault(false)
        if (free(PREFERRED_PORT)) return PREFERRED_PORT
        return runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(PREFERRED_PORT + 1)
    }

    // ---------------------------------------------------------------- player

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).setApplicationLooper(Looper.getMainLooper()).buildAsync()
        controllerFuture = future
        future.addListener({
            if (stopping) {
                runCatching { future.get().release() }
                return@addListener
            }
            runCatching { future.get() }
                .onSuccess { c ->
                    controller = c
                    c.addListener(playerListener)
                    publishNowPlaying()
                    reconcile()
                }
                .onFailure { Timber.tag(TAG).e(it, "Party couldn't connect to the player") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun releaseController() {
        val c = controller
        controller = null
        c?.removeListener(playerListener)
        runCatching { c?.release() }
        controllerFuture?.let { f -> if (!f.isDone) f.cancel(true) }
        controllerFuture = null
    }

    /**
     * Keeps exactly one party song staged right after the current song: once it starts
     * playing, the next most-voted request is inserted. Event-driven, no polling.
     */
    private fun reconcile() {
        val c = controller ?: return
        if (stopping || !manager.isRunning) return
        val currentIndex = c.currentMediaItemIndex
        val currentId = c.currentMediaItem?.mediaId
        val staged = manager.staged()
        if (staged != null) {
            when {
                currentId == staged.song.id && transitionCount > stagedAtTransition -> {
                    manager.markPlayed(staged.id)
                    publishNowPlaying()
                }
                isAfterCurrent(c, staged.song.id) -> return
                currentId == staged.song.id -> return
                else -> manager.markDropped(staged.id) // host removed or skipped past it
            }
        }
        val next = manager.nextToStage() ?: return
        val song = librarySongs[next.song.id]
        if (song == null) {
            scope.launch {
                val fetched = runCatching { musicRepository.getSong(next.song.id).first() }.getOrNull()
                main.post {
                    if (fetched == null) manager.markDropped(next.id)
                    else librarySongs = librarySongs + (fetched.id to fetched)
                    reconcile()
                }
            }
            return
        }
        val item = MediaItemBuilder.build(song)
        if (c.mediaItemCount == 0 || currentIndex == C.INDEX_UNSET) {
            c.setMediaItem(item)
            c.prepare()
            c.play()
            stagedAtTransition = transitionCount - 1 // already current: counts as played on next pass
        } else {
            val insertAt = (currentIndex + 1).coerceAtMost(c.mediaItemCount)
            c.addMediaItem(insertAt, item)
            stagedAtTransition = transitionCount
            if (c.playbackState == Player.STATE_ENDED) {
                // The host's queue had run out: start the party song right away.
                c.seekTo(insertAt, 0L)
                c.play()
            }
        }
        manager.markStaged(next.id)
        if (c.mediaItemCount == 1) reconcile()
    }

    private fun isAfterCurrent(c: MediaController, songId: String): Boolean {
        val start = c.currentMediaItemIndex + 1
        for (i in start until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == songId) return true
        }
        return false
    }

    private fun publishNowPlaying() {
        val c = controller ?: return
        val item = c.currentMediaItem
        if (item == null) {
            manager.setNowPlaying(null)
            return
        }
        val md = c.mediaMetadata
        manager.setNowPlaying(
            PartyNowPlaying(
                songId = item.mediaId,
                title = md.title?.toString().orEmpty().ifBlank { librarySongs[item.mediaId]?.title.orEmpty() },
                artist = md.artist?.toString().orEmpty(),
                album = md.albumTitle?.toString().orEmpty(),
                artUri = librarySongs[item.mediaId]?.albumArtUriString ?: md.artworkUri?.toString(),
                durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                positionAt = System.currentTimeMillis(),
                isPlaying = c.isPlaying,
                addedBy = null,
            )
        )
    }

    // --------------------------------------------------------------- library

    private suspend fun loadLibrary(force: Boolean = false): PartyLibraryIndex? = libraryMutex.withLock {
        val now = System.currentTimeMillis()
        val current = library
        if (!force && current != null && now - libraryLoadedAt < LIBRARY_TTL_MS) return current
        val songs = runCatching { musicRepository.getAllSongsOnce() }.getOrElse {
            Timber.tag(TAG).w(it, "Party library load failed")
            return current
        }
        val index = PartyLibraryIndex(songs.map { PartyLibraryIndex.Entry(it.toPartySong(), it.dateAdded) })
        librarySongs = librarySongs + songs.associateBy { it.id }
        library = index
        libraryLoadedAt = now
        index
    }

    private fun Song.toPartySong() = PartySong(
        id = id,
        title = title.ifBlank { "Unknown song" },
        artist = displayArtist.ifBlank { "Unknown artist" },
        album = album,
        durationMs = duration,
    )

    // ------------------------------------------------------------------- art

    private fun artJpeg(song: Song, size: Int): ByteArray? {
        val key = "${song.id}@$size"
        artCache.get(key)?.let { return it }
        val bitmap = decodeArt(song, size) ?: return null
        val out = ByteArrayOutputStream()
        val scaled = scaleToFit(bitmap, size)
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return out.toByteArray().also { artCache.put(key, it) }
    }

    private fun decodeArt(song: Song, size: Int): Bitmap? {
        val artUri = song.albumArtUriString?.takeIf { it.isNotBlank() }?.toUri()
        if (artUri != null && artUri.scheme != "http" && artUri.scheme != "https") {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(artUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth > 0) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
                    contentResolver.openInputStream(artUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                } else null
            }.getOrNull()?.let { return it }
        }
        val uri = song.contentUriString.takeIf { it.startsWith("content://") || it.startsWith("file://") }?.toUri()
            ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        var s = 1
        while (w / (s * 2) >= target && h / (s * 2) >= target) s *= 2
        return s
    }

    private fun scaleToFit(b: Bitmap, size: Int): Bitmap {
        val side = minOf(b.width, b.height)
        val x = (b.width - side) / 2
        val y = (b.height - side) / 2
        val square = if (b.width != b.height) Bitmap.createBitmap(b, x, y, side, side) else b
        if (side <= size) return square
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        if (square !== b && square !== scaled) square.recycle()
        return scaled
    }

    // ---------------------------------------------------------------- routes

    private fun io.ktor.server.routing.Routing.partyRoutes() {
        get("/") { call.respondPage() }
        get("/tv") { call.respondPage() }
        // Site icons from the Nobody Listens kit; favicon.svg follows the guest's light or dark mode.
        get("/favicon.svg") { call.respondAsset("party/favicon.svg", ContentType.Image.SVG) }
        get("/apple-touch-icon.png") { call.respondAsset("party/apple-touch-icon.png", ContentType.Image.PNG) }

        post("/api/pin") {
            if (checkExpired()) {
                call.respondError(HttpStatusCode.Unauthorized, "ended", "The party has ended"); return@post
            }
            val params = runCatching { call.receiveParameters() }.getOrNull()
            when (val r = manager.exchangePin(params?.get("pin"), call.request.origin.remoteHost)) {
                is PartyManager.PinResult.Ok -> call.respondJson(json.encodeToString(TokenDto.serializer(), TokenDto(r.token)))
                PartyManager.PinResult.Wrong -> call.respondError(HttpStatusCode.Forbidden, "wrong_pin", "That PIN doesn't match")
                is PartyManager.PinResult.Locked -> call.respondError(
                    HttpStatusCode.TooManyRequests, "locked", "Too many tries. Wait ${r.retryInMs / 1000 + 1} s"
                )
            }
        }

        post("/api/join") {
            if (!call.checkToken()) return@post
            val params = runCatching { call.receiveParameters() }.getOrNull()
            val name = params?.get("name").orEmpty()
            when (val r = manager.join(name, call.request.origin.remoteHost)) {
                is PartyManager.JoinResult.Ok -> call.respondJson(
                    json.encodeToString(JoinDto.serializer(), JoinDto(r.guest.id, r.guest.name, r.key))
                )
                PartyManager.JoinResult.Banned -> call.respondError(HttpStatusCode.Forbidden, "kicked", "The host removed you from this party")
                PartyManager.JoinResult.Full -> call.respondError(HttpStatusCode.Forbidden, "full", "This party is full")
            }
        }

        get("/api/state") {
            if (!call.checkToken()) return@get
            val guest = call.optionalGuest()
            if (guest === KICKED) return@get
            call.respondJson(json.encodeToString(StateDto.serializer(), buildState(guest)))
        }

        get("/api/search") {
            if (!call.checkToken()) return@get
            call.requireGuest() ?: return@get
            val q = call.request.queryParameters["q"].orEmpty()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val index = loadLibrary()
            if (index == null) {
                call.respondJson(json.encodeToString(SearchDto.serializer(), SearchDto(emptyList(), 0, false, 0)))
                return@get
            }
            val result = index.search(q, page, 20)
            val active = manager.read { it.activeSongIds() }.orEmpty()
            call.respondJson(
                json.encodeToString(
                    SearchDto.serializer(),
                    SearchDto(
                        items = result.items.map { SongDto(it.id, it.title, it.artist, it.album, it.durationMs, it.id in active) },
                        page = result.page,
                        hasMore = result.hasMore,
                        total = result.total,
                    )
                )
            )
        }

        post("/api/add") {
            if (!call.checkToken()) return@post
            val guest = call.requireGuest() ?: return@post
            val songId = runCatching { call.receiveParameters() }.getOrNull()?.get("songId").orEmpty()
            val song = loadLibrary()?.get(songId)
            if (song == null) {
                call.respondError(HttpStatusCode.NotFound, "not_found", "That song isn't in the library")
                return@post
            }
            when (val r = manager.add(guest.id, song)) {
                is PartyAddResult.Added -> call.respondJson(
                    json.encodeToString(
                        AddDto.serializer(),
                        AddDto(if (r.request.status == PartyRequestStatus.AwaitingApproval) "awaiting" else "added", "Added ${song.title}")
                    )
                )
                is PartyAddResult.Voted -> call.respondJson(
                    json.encodeToString(AddDto.serializer(), AddDto("voted", "Already requested — you voted for it"))
                )
                is PartyAddResult.TooSoon -> call.respondError(
                    HttpStatusCode.TooManyRequests, "too_soon", "Wait ${r.retryInMs / 1000 + 1} s before adding another"
                )
                PartyAddResult.TooManyPending -> call.respondError(
                    HttpStatusCode.TooManyRequests, "too_many", "You have 5 songs waiting. Let some play first"
                )
                PartyAddResult.PartyFull -> call.respondError(HttpStatusCode.TooManyRequests, "full", "The queue is full")
                PartyAddResult.UnknownGuest -> call.respondError(HttpStatusCode.Unauthorized, "no_guest", "Join the party first")
            }
        }

        post("/api/vote") {
            if (!call.checkToken()) return@post
            val guest = call.requireGuest() ?: return@post
            val requestId = runCatching { call.receiveParameters() }.getOrNull()?.get("requestId").orEmpty()
            val r = manager.toggleVote(guest.id, requestId)
            if (r == null) call.respondError(HttpStatusCode.NotFound, "not_found", "That request is gone")
            else call.respondJson("""{"ok":true,"votes":${r.votes}}""")
        }

        get("/api/art/{songId}") {
            if (!call.checkToken()) return@get
            val songId = call.parameters["songId"].orEmpty()
            val size = (call.request.queryParameters["s"]?.toIntOrNull() ?: 160).coerceIn(48, 640)
            // Only library songs or what's playing — never arbitrary ids/paths.
            val song = librarySongs[songId]?.takeIf {
                library?.get(songId) != null || manager.nowPlaying()?.songId == songId
            }
            val bytes = song?.let { runCatching { artJpeg(it, size) }.getOrNull() }
            if (bytes == null) {
                call.response.header("Cache-Control", "private, max-age=300")
                call.respondText("", ContentType.Text.Plain, HttpStatusCode.NotFound)
                return@get
            }
            call.response.header("Cache-Control", "private, max-age=3600")
            call.respondBytes(bytes, ContentType.Image.JPEG)
        }

        get("/api/qr.svg") {
            if (!call.checkToken()) return@get
            val url = manager.state.value.joinUrl ?: return@get call.respondError(HttpStatusCode.NotFound, "gone", "Party ended")
            call.response.header("Cache-Control", "private, max-age=3600")
            call.respondText(PartyQr.svg(url), ContentType.Image.SVG)
        }
    }

    private fun buildState(guest: PartyGuest?): StateDto {
        val now = System.currentTimeMillis()
        val s = manager.state.value
        val np = manager.nowPlaying()
        val data = manager.read { q ->
            Triple(
                q.upNext(),
                guest?.let { g -> q.awaitingApproval().filter { it.guestId == g.id } }.orEmpty(),
                guest?.let { g -> q.pendingCountFor(g.id) to q.cooldownMs(g.id, now) },
            )
        }
        fun PartyRequest.dto() = RequestDto(
            id = id, songId = song.id, title = song.title, artist = song.artist, durationMs = song.durationMs,
            addedBy = guestName, votes = votes, votedByMe = guest != null && guest.id in voters,
            mine = guest != null && guest.id == guestId, staged = status == PartyRequestStatus.Staged,
        )
        return StateDto(
            running = s.status == PartyStatus.Running,
            endsAt = s.endsAt,
            now = now,
            guests = s.guests.size,
            approveEach = s.approveEach,
            nowPlaying = np?.let {
                NowPlayingDto(
                    songId = it.songId, title = it.title, artist = it.artist, album = it.album,
                    durationMs = it.durationMs, positionMs = it.positionMs, positionAt = it.positionAt,
                    isPlaying = it.isPlaying, addedBy = it.addedBy,
                    hasArt = it.songId != null && librarySongs.containsKey(it.songId),
                )
            },
            upNext = data?.first.orEmpty().take(50).map { it.dto() },
            awaiting = data?.second.orEmpty().map { it.dto() },
            me = guest?.let { g ->
                MeDto(g.id, g.name, data?.third?.first ?: 0, data?.third?.second ?: 0L)
            },
        )
    }

    // ------------------------------------------------------- call helpers

    private suspend fun ApplicationCall.respondPage() {
        noStore()
        response.header("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
        respondText(pageHtml, ContentType.Text.Html.withParameter("charset", "utf-8"))
    }

    private suspend fun ApplicationCall.respondAsset(path: String, type: ContentType) {
        val bytes = runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
            ?: return respondText("", ContentType.Text.Plain, HttpStatusCode.NotFound)
        response.header("Cache-Control", "public, max-age=86400")
        respondBytes(bytes, type)
    }

    private suspend fun ApplicationCall.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        noStore()
        respondText(body, ContentType.Application.Json, status)
    }

    private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
        respondJson(json.encodeToString(ErrorDto.serializer(), ErrorDto(code, message)), status)

    private fun ApplicationCall.noStore() {
        response.header("Cache-Control", "no-store")
        response.header("Referrer-Policy", "no-referrer")
        response.header("X-Content-Type-Options", "nosniff")
    }

    private suspend fun ApplicationCall.checkToken(): Boolean {
        // Every API call (incl. the state poll) checks the wall-clock end first.
        if (checkExpired()) {
            respondError(HttpStatusCode.Unauthorized, "ended", "The party has ended")
            return false
        }
        val t = request.headers["X-Party-Token"] ?: request.queryParameters["t"]
        if (manager.checkToken(t)) return true
        respondError(HttpStatusCode.Unauthorized, if (manager.isRunning) "bad_token" else "ended",
            if (manager.isRunning) "This link isn't valid. Ask the host for the QR code" else "The party has ended")
        return false
    }

    private fun ApplicationCall.guestKey(): String? = request.headers["X-Party-Guest"] ?: request.queryParameters["g"]

    /** Guest if a valid key was sent; null for anonymous (TV) callers; [KICKED] after responding 403. */
    private suspend fun ApplicationCall.optionalGuest(): PartyGuest? {
        val key = guestKey() ?: return null
        return when (val a = manager.authenticateGuest(key)) {
            is PartyManager.GuestAuth.Ok -> a.guest
            PartyManager.GuestAuth.Kicked -> {
                respondError(HttpStatusCode.Forbidden, "kicked", "The host removed you from this party"); KICKED
            }
            PartyManager.GuestAuth.Unknown -> null
        }
    }

    private suspend fun ApplicationCall.requireGuest(): PartyGuest? {
        val key = guestKey()
        return when (val a = manager.authenticateGuest(key)) {
            is PartyManager.GuestAuth.Ok -> a.guest
            PartyManager.GuestAuth.Kicked -> {
                respondError(HttpStatusCode.Forbidden, "kicked", "The host removed you from this party"); null
            }
            PartyManager.GuestAuth.Unknown -> {
                respondError(HttpStatusCode.Unauthorized, "no_guest", "Join the party first"); null
            }
        }
    }

    companion object {
        private const val TAG = "PartyServer"
        const val ACTION_START = "com.nobodybuilds.listens.party.START"
        const val ACTION_END = "com.nobodybuilds.listens.party.END"
        private const val ACTION_TIME_UP = "com.nobodybuilds.listens.party.TIME_UP"
        private const val REQUEST_END_ALARM = 0x9A47
        private const val LOCK_MARGIN_MS = 5 * 60_000L
        const val EXTRA_DURATION_MS = "duration_ms"
        private const val DEFAULT_DURATION_MS = 2 * 3_600_000L
        private const val CHANNEL_ID = "lmusic_party"
        private const val NOTIFICATION_ID = 1007
        private const val PREFERRED_PORT = 8765
        private const val LIBRARY_TTL_MS = 10 * 60_000L
        private val KICKED = PartyGuest("", "", 0L)
    }
}
