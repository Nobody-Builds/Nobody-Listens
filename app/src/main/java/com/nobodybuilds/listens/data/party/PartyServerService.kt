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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.nobodybuilds.listens.MainActivity
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.MusicService
import com.nobodybuilds.listens.utils.MediaItemBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
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

    private val libraryCache by lazy { PartyLibraryCache(musicRepository) }
    private val artRenderer = PartyArtRenderer(this)
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
            val routes = PartyRoutes(
                assets = assets,
                manager = manager,
                libraryCache = libraryCache,
                artRenderer = artRenderer,
                pageHtml = pageHtml,
                checkExpired = { checkExpired() },
            )
            val port = pickPort(ip)
            val created = try {
                embeddedServer(CIO, port = port, host = ip) { routing { routes.install(this) } }
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
            libraryCache.loadLibrary(force = true)
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
        artRenderer.clear()
        libraryCache.clear()
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
        val song = libraryCache.librarySongs[next.song.id]
        if (song == null) {
            scope.launch {
                val fetched = runCatching { musicRepository.getSong(next.song.id).first() }.getOrNull()
                main.post {
                    if (fetched == null) manager.markDropped(next.id)
                    else libraryCache.librarySongs = libraryCache.librarySongs + (fetched.id to fetched)
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
                title = md.title?.toString().orEmpty().ifBlank { libraryCache.librarySongs[item.mediaId]?.title.orEmpty() },
                artist = md.artist?.toString().orEmpty(),
                album = md.albumTitle?.toString().orEmpty(),
                artUri = libraryCache.librarySongs[item.mediaId]?.albumArtUriString ?: md.artworkUri?.toString(),
                durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                positionAt = System.currentTimeMillis(),
                isPlaying = c.isPlaying,
                addedBy = null,
            )
        )
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
    }
}
