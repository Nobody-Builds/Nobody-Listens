package com.twilighty.lmusic.data.lofi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.noise.NoiseController
import com.twilighty.lmusic.data.noise.NoiseEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service for lofi: either the on-device generator ([LofiEngine]) or a radio
 * stream (its own ExoPlayer, separate from the main MusicService player). Holds audio focus:
 * when the main player (or any other app) takes focus, lofi stops.
 *
 * No MediaSession on purpose, so it never competes with the main player's session; the
 * notification carries play/pause, new tune and stop.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class LofiService : Service(), LofiServiceHandle {

    @Inject lateinit var controller: LofiController
    @Inject lateinit var engine: LofiEngine
    @Inject lateinit var radioRepository: LofiRadioRepository
    @Inject lateinit var noise: NoiseController
    @Inject lateinit var noiseEngine: NoiseEngine

    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var source: LofiSource? = null
    private var playing = false
    private var buffering = false
    private var station: RadioStation? = null
    private var nowPlaying: String? = null
    private var error: String? = null

    private var radio: ExoPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnGain = false
    private var ducked = false

    private var sleepJob: Job? = null
    private var sleepEndsAt: Long? = null
    private var idleJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        controller.service = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always satisfy the startForegroundService() contract first.
        if (!goForeground()) {
            controller.publish(LofiState(error = "Couldn't start lofi"))
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_GENERATE -> playGenerator()
            ACTION_NOISE -> playNoise()
            ACTION_RADIO -> {
                val st = intent.getStringExtra(EXTRA_STATION)?.let {
                    runCatching { json.decodeFromString(RadioStation.serializer(), it) }.getOrNull()
                }
                if (st != null) playRadio(st) else if (source == null) stopAll()
            }
            ACTION_TOGGLE -> if (playing) pause() else resume()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_NEW_TUNE -> {
                controller.newTune()
                if (source != LofiSource.Generator || !playing) playGenerator()
            }
            ACTION_STOP -> stopAll()
            else -> if (source == null) stopAll()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (controller.service === this) controller.service = null
        sleepJob?.cancel()
        idleJob?.cancel()
        releaseRadio()
        engine.stop(300f)
        // Only noise this service played (under-music noise runs without it).
        if (source == LofiSource.Noise) noiseEngine.stop(300f)
        abandonFocus()
        releaseWake()
        scope.cancel()
        controller.publish(LofiState(error = controller.state.value.error))
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing like the main player does; the notification has a stop button.
        super.onTaskRemoved(rootIntent)
    }

    // ---------------------------------------------------------------- playback

    private fun playGenerator() {
        releaseRadio()
        if (source == LofiSource.Noise) noiseEngine.stop(300f)
        source = LofiSource.Generator
        station = null
        nowPlaying = null
        error = null
        if (!requestFocus()) {
            error = "Another app is using audio"
            stopAll()
            return
        }
        engine.start(controller.settings.value)
        engine.setDucked(false)
        playing = true
        acquireWake()
        cancelIdle()
        applyPendingSleep()
        publish()
    }

    /** The noise generator (settings from [NoiseController]). */
    private fun playNoise() {
        releaseRadio()
        engine.stop(300f)
        source = LofiSource.Noise
        station = null
        nowPlaying = null
        error = null
        if (!requestFocus()) {
            error = "Another app is using audio"
            stopAll()
            return
        }
        noise.ensureLoaded()
        noiseEngine.start(noise.settings.value)
        noiseEngine.setDucked(false)
        playing = true
        acquireWake()
        cancelIdle()
        applyPendingSleep()
        publish()
    }

    private fun applyPendingSleep() {
        controller.takePendingSleep()?.let { (minutes, fadeMs) -> setSleepTimer(minutes, fadeMs) }
    }

    private fun playRadio(st: RadioStation) {
        engine.stop(300f)
        if (source == LofiSource.Noise) noiseEngine.stop(300f)
        releaseWake()
        source = LofiSource.Radio
        station = st
        nowPlaying = null
        error = null
        if (!requestFocus()) {
            error = "Another app is using audio"
            stopAll()
            return
        }
        val player = radio ?: buildRadioPlayer().also { radio = it }
        player.setMediaItem(MediaItem.fromUri(st.url))
        player.volume = 1f
        player.prepare()
        player.play()
        playing = true
        buffering = true
        cancelIdle()
        applyPendingSleep()
        publish()
        scope.launch { radioRepository.reportClick(st) }
    }

    override fun pause() = pauseInternal(transient = false)

    private fun pauseInternal(transient: Boolean) {
        if (source == null) return
        resumeOnGain = transient
        when (source) {
            LofiSource.Generator -> engine.stop(500f)
            LofiSource.Radio -> radio?.pause()
            LofiSource.Noise -> noiseEngine.stop(500f)
            null -> Unit
        }
        playing = false
        buffering = false
        releaseWake()
        if (!transient) {
            abandonFocus()
            scheduleIdleStop()
        }
        publish()
    }

    override fun resume() {
        val src = source ?: return
        if (!requestFocus()) return
        resumeOnGain = false
        when (src) {
            LofiSource.Generator -> {
                engine.start(controller.settings.value, 800f)
                acquireWake()
            }
            LofiSource.Noise -> {
                noiseEngine.start(noise.settings.value, 800f)
                acquireWake()
            }
            LofiSource.Radio -> {
                val p = radio
                if (p == null) station?.let { playRadio(it); return } else {
                    if (p.playbackState == Player.STATE_IDLE) p.prepare()
                    p.play()
                }
            }
        }
        playing = true
        error = null
        cancelIdle()
        publish()
    }

    override fun stopAll() = stopWithFade(600L)

    override fun stopWithFade(fadeMs: Long) {
        sleepJob?.cancel(); sleepJob = null; sleepEndsAt = null
        sleepLevel = 1f
        cancelIdle()
        engine.stop(fadeMs.toFloat())
        if (source == LofiSource.Noise) noiseEngine.stop(fadeMs.toFloat())
        releaseRadio()
        abandonFocus()
        releaseWake()
        source = null
        playing = false
        buffering = false
        station = null
        nowPlaying = null
        controller.publish(LofiState(error = error))
        error = null
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        stopSelf()
    }

    override fun setSleepTimer(minutes: Int?) = setSleepTimer(minutes, SLEEP_FADE_MS)

    override fun setSleepTimer(minutes: Int?, fadeMs: Long) {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndsAt = null
        if (sleepLevel < 1f) applySleepLevel(1f)
        if (minutes != null && minutes > 0) {
            val total = minutes * 60_000L
            sleepEndsAt = System.currentTimeMillis() + total
            sleepJob = scope.launch {
                val fade = fadeMs.coerceIn(1_000L, total)
                delay(total - fade)
                if (fade <= SLEEP_FADE_MS && source == LofiSource.Generator) {
                    engine.stop(fade.toFloat())
                    delay(fade)
                } else {
                    // Long fades: step the level down (a gentle curve), a few seconds per step.
                    val steps = (fade / 3_000L).toInt().coerceIn(10, 400)
                    for (i in steps downTo 0) {
                        val f = i / steps.toFloat()
                        applySleepLevel(f * f)
                        delay(fade / steps)
                    }
                }
                sleepEndsAt = null
                stopAll()
            }
        }
        publish()
    }

    /** 0..1 on top of the normal level, for the sleep fade. */
    private var sleepLevel = 1f

    private fun applySleepLevel(level: Float) {
        sleepLevel = level
        when (source) {
            LofiSource.Generator -> engine.update(
                controller.settings.value.let { it.copy(masterVolume = it.masterVolume * level) }
            )
            LofiSource.Radio -> radio?.volume = (if (ducked) 0.3f else 1f) * level
            LofiSource.Noise -> noiseEngine.setLevel(level)
            null -> Unit
        }
    }

    private fun buildRadioPlayer(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(LofiRadioRepository.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        val dataSource = DefaultDataSource.Factory(this, http)
        val attrs = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(attrs, /* handleAudioFocus = */ false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply { addListener(radioListener) }
    }

    private val radioListener = object : Player.Listener {
        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) entry.title?.trim()?.takeIf { it.isNotEmpty() }?.let { nowPlaying = it }
            }
            publish()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            buffering = playbackState == Player.STATE_BUFFERING
            publish()
        }

        override fun onPlayerError(e: PlaybackException) {
            Timber.tag(TAG).w(e, "Radio error")
            error = "Couldn't play this station"
            playing = false
            buffering = false
            releaseWake()
            abandonFocus()
            scheduleIdleStop()
            publish()
        }
    }

    private fun releaseRadio() {
        radio?.let {
            it.removeListener(radioListener)
            runCatching { it.stop(); it.release() }
        }
        radio = null
    }

    // ---------------------------------------------------------------- audio focus

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Main music (or another app) started: lofi steps aside.
                focusRequest = null
                stopAll()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playing) pauseInternal(transient = true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> setDuck(true)
            AudioManager.AUDIOFOCUS_GAIN -> {
                setDuck(false)
                if (resumeOnGain && !playing) resume()
            }
        }
    }

    private fun setDuck(value: Boolean) {
        ducked = value
        engine.setDucked(value)
        noiseEngine.setDucked(value)
        radio?.volume = (if (value) 0.3f else 1f) * sleepLevel
    }

    private fun requestFocus(): Boolean {
        if (focusRequest != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) focusRequest = request
        return granted
    }

    private fun abandonFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    // ---------------------------------------------------------------- housekeeping

    private fun acquireWake() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LMusic:lofi").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWake() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    /** Paused for a while: let the service go. */
    private fun scheduleIdleStop() {
        cancelIdle()
        idleJob = scope.launch {
            delay(IDLE_STOP_MS)
            // A focus session owns lofi (paused / on a break): stay up; released -> onFocusHoldReleased.
            if (!playing && !controller.holdForFocus) stopAll()
        }
    }

    override fun onFocusHoldReleased() {
        if (source != null && !playing) scheduleIdleStop()
    }

    private fun cancelIdle() { idleJob?.cancel(); idleJob = null }

    private fun publish() {
        controller.publish(
            LofiState(
                source = source,
                isPlaying = playing,
                isBuffering = buffering && playing,
                station = station,
                nowPlaying = nowPlaying,
                sleepTimerEndsAt = sleepEndsAt,
                error = error,
            )
        )
        if (source != null) updateNotification()
    }

    /** Returns false (and the caller stops) when the service couldn't enter the foreground. */
    private fun goForeground(): Boolean {
        val notification = buildNotification()
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            inForeground = true
        }.onFailure { Timber.tag(TAG).w(it, "startForeground failed, stopping") }
        return inForeground
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val settings = controller.settings.value
        val noiseSettings = if (source == LofiSource.Noise) noise.settings.value else null
        val title = when (source) {
            LofiSource.Radio -> station?.name ?: "Lofi radio"
            LofiSource.Noise -> "Noise · ${noiseSettings?.presetName ?: noiseSettings?.summary ?: ""}"
            else -> "Lofi · ${settings.mood.label}"
        }
        val text = when {
            error != null -> error
            source == LofiSource.Radio -> nowPlaying ?: if (buffering) "Connecting…" else "Lofi radio"
            source == LofiSource.Noise ->
                if (noiseSettings?.presetName != null) "${noiseSettings.summary} · made on your phone" else "Made on your phone"
            else -> "${settings.keyName} · ${settings.tempoBpm} BPM · made on your phone"
        }
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.twilighty.lmusic.shared.R.drawable.nl_mark_mono)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(playing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
        // Swiping away stops lofi only while paused; while playing the notification is ongoing.
        if (!playing) builder.setDeleteIntent(action(ACTION_STOP, 4))
        builder.addAction(
            if (playing) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24,
            if (playing) "Pause" else "Play",
            action(ACTION_TOGGLE, 1)
        )
        val compact = if (source == LofiSource.Generator) {
            builder.addAction(R.drawable.rounded_shuffle_24, "New tune", action(ACTION_NEW_TUNE, 2))
            intArrayOf(0, 1, 2)
        } else intArrayOf(0, 1)
        builder.addAction(R.drawable.rounded_close_24, "Stop", action(ACTION_STOP, 3))
        builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(*compact))
        return builder.build()
    }

    private fun action(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this, requestCode,
            Intent(this, LofiService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Lofi", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Lofi generator and radio controls"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val TAG = "LofiService"
        const val CHANNEL_ID = "lmusic_lofi"
        private const val NOTIFICATION_ID = 0x10F1
        const val ACTION_GENERATE = "com.twilighty.lmusic.lofi.GENERATE"
        const val ACTION_NOISE = "com.twilighty.lmusic.lofi.NOISE"
        const val ACTION_RADIO = "com.twilighty.lmusic.lofi.RADIO"
        const val ACTION_TOGGLE = "com.twilighty.lmusic.lofi.TOGGLE"
        const val ACTION_PAUSE = "com.twilighty.lmusic.lofi.PAUSE"
        const val ACTION_RESUME = "com.twilighty.lmusic.lofi.RESUME"
        const val ACTION_NEW_TUNE = "com.twilighty.lmusic.lofi.NEW_TUNE"
        const val ACTION_STOP = "com.twilighty.lmusic.lofi.STOP"
        const val EXTRA_STATION = "station"
        private const val SLEEP_FADE_MS = 30_000L
        private const val IDLE_STOP_MS = 10 * 60_000L
        private val json = Json { ignoreUnknownKeys = true }
    }
}
