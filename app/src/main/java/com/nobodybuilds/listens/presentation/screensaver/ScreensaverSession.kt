package com.nobodybuilds.listens.presentation.screensaver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.nobodybuilds.listens.data.screensaver.ScreensaverPreferences
import com.nobodybuilds.listens.data.screensaver.ScreensaverSettings
import com.nobodybuilds.listens.data.screensaver.ScreensaverWeatherRepository
import com.nobodybuilds.listens.data.screensaver.WeatherSnapshot
import com.nobodybuilds.listens.data.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the screensaver shows for the current song. Position is a snapshot; the UI extrapolates. */
data class ScreensaverNowPlaying(
    val title: String,
    val artist: String,
    val artwork: Any?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val snapshotAtElapsed: Long,
) {
    fun positionAt(elapsedRealtime: Long): Long {
        val p = if (isPlaying) positionMs + (elapsedRealtime - snapshotAtElapsed) else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** True when the device is plugged in (sticky ACTION_BATTERY_CHANGED, no receiver needed). */
fun isDeviceCharging(context: Context): Boolean = runCatching {
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}.getOrDefault(false)

/**
 * Charging watcher: reads the sticky battery state, then listens for power connected / disconnected.
 * Register only while in the foreground ([start] in onStart, [stop] in onStop).
 */
class ChargingWatcher(private val context: Context, private val onChange: (Boolean) -> Unit) {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> onChange(true)
                Intent.ACTION_POWER_DISCONNECTED -> onChange(false)
            }
        }
    }

    fun start() {
        if (registered) return
        onChange(isDeviceCharging(context))
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}

/**
 * Everything the screensaver needs while it is on screen: charging state, the media session,
 * settings and weather. Shared by [ScreensaverActivity] and [NobodyListensDreamService].
 * Call [start] when visible and [stop] when hidden; nothing runs in between.
 */
class ScreensaverSession(
    private val context: Context,
    private val preferences: ScreensaverPreferences,
    private val weatherRepository: ScreensaverWeatherRepository,
    private val scope: CoroutineScope,
) {
    private val _charging = MutableStateFlow(isDeviceCharging(context))
    val charging: StateFlow<Boolean> = _charging.asStateFlow()

    private val _nowPlaying = MutableStateFlow<ScreensaverNowPlaying?>(null)
    val nowPlaying: StateFlow<ScreensaverNowPlaying?> = _nowPlaying.asStateFlow()

    private val _settings = MutableStateFlow(ScreensaverSettings())
    val settings: StateFlow<ScreensaverSettings> = _settings.asStateFlow()

    private val _weather = MutableStateFlow<WeatherSnapshot?>(null)
    val weather: StateFlow<WeatherSnapshot?> = _weather.asStateFlow()

    private val chargingWatcher = ChargingWatcher(context) { _charging.value = it }
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var jobs = mutableListOf<Job>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun start() {
        chargingWatcher.start()
        connect()
        jobs += scope.launch {
            preferences.settings.collect { _settings.value = it }
        }
        jobs += scope.launch {
            preferences.settings
                .map { it.showWeather to it.location }
                .distinctUntilChanged()
                .collectLatest {
                    // Weather refresh while visible: on open, on setting change, then every 30 min
                    // (about 5 min after a failure). A cancelled refresh keeps the last value.
                    while (true) {
                        val s = preferences.current()
                        if (!s.showWeather) { _weather.value = null; break }
                        val result = weatherRepository.refresh(s)
                        _weather.value = result.snapshot
                        delay(result.nextRefreshMs)
                    }
                }
        }
    }

    fun stop() {
        chargingWatcher.stop()
        jobs.forEach { it.cancel() }
        jobs.clear()
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun connect() {
        if (controllerFuture != null) return
        runCatching {
            val token = SessionToken(context, ComponentName(context, MusicService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                val c = runCatching { future.get() }.getOrNull() ?: return@addListener
                if (controllerFuture !== future) { runCatching { c.release() }; return@addListener }
                controller = c
                c.addListener(listener)
                publish(c)
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        if (item == null) { _nowPlaying.value = null; return }
        val md = player.mediaMetadata
        _nowPlaying.value = ScreensaverNowPlaying(
            title = md.title?.toString() ?: md.displayTitle?.toString() ?: "Unknown song",
            artist = md.artist?.toString() ?: md.albumArtist?.toString() ?: "",
            artwork = md.artworkUri ?: md.artworkData,
            isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING),
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
            snapshotAtElapsed = SystemClock.elapsedRealtime(),
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
}
