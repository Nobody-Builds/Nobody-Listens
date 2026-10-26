package com.nobodybuilds.listens.data.gestures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.media3.common.Player
import com.nobodybuilds.listens.data.crash.SafeMode
import com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics
import com.nobodybuilds.listens.presentation.haptics.NobodyListensHapticsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Phone-motion gestures for playback: shake to shuffle, turn face-down to pause (and optionally
 * resume when turned face-up). Both work with the screen off / locked while music plays.
 *
 * Battery: sensors are registered only while a gesture is enabled AND music is playing (plus a
 * short [PAUSE_GRACE_MS] after it stops, or while a face-down pause waits to resume) and are
 * unregistered otherwise. Only the plain accelerometer is used with the screen off (no gyro
 * fusion sensors), at a low rate with hardware batching:
 * - screen on: shake uses linear acceleration (or accelerometer), face-down the accelerometer,
 *   both at SENSOR_DELAY_UI;
 * - screen off: ONE accelerometer registration feeds both detectors: ~50 Hz batched ~1.2 s while
 *   shake is armed, else ~5 Hz batched ~1 s for face-down. The wake-up variant is used when its
 *   FIFO can batch (with audio offload the CPU sleeps during playback, so a non-wake-up sensor
 *   would deliver nothing); it wakes the CPU about once per batch. Detectors use event
 *   timestamps, so batching only delays detection. A cheap proximity sensor runs alongside:
 *   while it reads near (pocket / bag) shake drops the accelerometer entirely until it reads far.
 *   A firmer double shake is required with the screen off.
 * Nothing runs in safe mode. MusicService is a mediaPlayback foreground service while playing,
 * so background sensor access is allowed.
 *
 * Lifecycle: [attach] from MusicService.onCreate, [onIsPlayingChanged] from the player listener,
 * [detach] in onDestroy. Everything runs on the main thread. The constructor does no work.
 */
@Singleton
class GestureController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: GesturePreferences,
    private val hapticsEngine: dagger.Lazy<NobodyListensHapticsEngine>,
) {
    /** What the controller needs from the playback service. */
    interface Host {
        /** The player currently published to the media session. */
        fun currentPlayer(): Player?

        /** Turns the app's shuffle on/off the same way the shuffle button does (syncs the UI). */
        fun setShuffleEnabled(enabled: Boolean)
    }

    /** Screen-on registration of one sensor; a change means unregister + register again. */
    private data class Registration(val sensor: Sensor)

    /**
     * The single screen-off accelerometer registration shared by shake and face-down. [shake] /
     * [face] say which detectors its samples feed; any change re-registers it.
     */
    private data class OffRegistration(
        val sensor: Sensor,
        val samplingUs: Int,
        val latencyUs: Int,
        val shake: Boolean,
        val face: Boolean,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var host: Host? = null
    private var settings = GestureSettings()

    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val powerManager: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private val shakeDetector = ShakeDetector()
    private val pocketShakeDetector = PocketShakeDetector()
    private val faceDownDetector = FaceDownDetector()
    private val shakeGravityFilter = GravityFilter()

    private var shakeRegistration: Registration? = null
    private var faceRegistration: Registration? = null
    private var offRegistration: OffRegistration? = null

    /** Whether each detector currently receives samples (from any registration). */
    private var shakeListening = false
    private var faceListening = false
    private var proximityRegistered = false
    private var screenReceiverRegistered = false
    private var screenOn = true
    private var proximityNear: Boolean? = null

    /** Set when we paused because the phone was turned face-down; cleared on any manual play. */
    private var pausedByFaceDownAtMs: Long? = null

    /** When playback last stopped (for the short grace before sensors stop). */
    private var stoppedAtMs: Long? = null

    private val evaluateRunnable = Runnable { evaluate() }

    // ------------------------------------------------------------------------------ lifecycle

    fun attach(host: Host) {
        if (this.host != null) detach()
        this.host = host
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        newScope.launch {
            runCatching {
                preferences.settings.collect { newSettings ->
                    settings = newSettings
                    shakeDetector.thresholdMs2 = newSettings.shakeSensitivity.thresholdMs2
                    pocketShakeDetector.thresholdMs2 = newSettings.shakeSensitivity.screenOffThresholdMs2
                    if (!newSettings.faceDownToPause || !newSettings.resumeWhenFaceUp) pausedByFaceDownAtMs = null
                    evaluate()
                }
            }.onFailure { Timber.tag(TAG).w(it, "Gesture settings unavailable") }
        }
    }

    fun detach() {
        mainHandler.removeCallbacksAndMessages(null)
        scope?.cancel()
        scope = null
        host = null
        pausedByFaceDownAtMs = null
        stoppedAtMs = null
        settings = GestureSettings()
        applySensors(wantShake = false, wantFace = false, keepFaceDown = false)
        unregisterProximity()
        unregisterScreenReceiver()
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            pausedByFaceDownAtMs = null
            stoppedAtMs = null
        } else if (stoppedAtMs == null) {
            stoppedAtMs = SystemClock.elapsedRealtime()
        }
        evaluate()
    }

    // ------------------------------------------------------------------------------ state

    private fun waitingToResume(): Boolean {
        val pausedAt = pausedByFaceDownAtMs ?: return false
        if (!settings.faceDownToPause || !settings.resumeWhenFaceUp) return false
        if (SystemClock.elapsedRealtime() - pausedAt > RESUME_WINDOW_MS) {
            pausedByFaceDownAtMs = null
            return false
        }
        return true
    }

    /** Milliseconds left in the after-pause grace, or 0 when it is over / not stopped. */
    private fun graceRemainingMs(): Long {
        val stoppedAt = stoppedAtMs ?: return 0L
        val remaining = PAUSE_GRACE_MS - (SystemClock.elapsedRealtime() - stoppedAt)
        if (remaining <= 0L) {
            stoppedAtMs = null
            return 0L
        }
        return remaining
    }

    /** Registers / unregisters everything to match the current state. Main thread only. */
    private fun evaluate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(evaluateRunnable)
            return
        }
        runCatching {
            val attached = host != null
            val safe = SafeMode.isActive(context)
            val isPlaying = host?.currentPlayer()?.isPlaying == true
            if (isPlaying) stoppedAtMs = null
            val grace = if (isPlaying) 0L else graceRemainingMs()
            val active = isPlaying || grace > 0L
            val waiting = waitingToResume()
            val couldUseSensors = attached && !safe && settings.anySensorGesture && (active || waiting)

            if (couldUseSensors) registerScreenReceiver() else unregisterScreenReceiver()

            val wantShake = couldUseSensors && settings.shakeToShuffle && active
            // While a face-down pause waits to resume, the accelerometer stays registered (also
            // with the screen off) for at most RESUME_WINDOW_MS.
            val wantFace = couldUseSensors && settings.faceDownToPause && (active || waiting)

            // Proximity: pocket check for screen-off shakes; "screen against something" for face-down.
            if (wantFace || (wantShake && !screenOn)) registerProximity() else unregisterProximity()
            applySensors(wantShake, wantFace, keepFaceDown = waiting)

            mainHandler.removeCallbacks(evaluateRunnable)
            val recheckIn = buildList {
                if (grace > 0L) add(grace)
                if (waiting) {
                    val pausedAt = pausedByFaceDownAtMs ?: 0L
                    add(RESUME_WINDOW_MS - (SystemClock.elapsedRealtime() - pausedAt))
                }
            }.minOrNull()
            // Re-check when the grace / resume window ends, so the sensors stop.
            if (recheckIn != null) mainHandler.postDelayed(evaluateRunnable, recheckIn.coerceAtLeast(500L))
        }.onFailure { Timber.tag(TAG).w(it, "Gesture evaluate failed") }
    }

    // ------------------------------------------------------------------------------ screen

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOn = when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> false
                Intent.ACTION_SCREEN_ON -> true
                else -> screenOn
            }
            evaluate()
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        screenOn = powerManager?.isInteractive ?: true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered = true
        }.onFailure { Timber.tag(TAG).w(it, "Screen receiver failed") }
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { context.unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    // ------------------------------------------------------------------------------ sensors

    /**
     * Matches the sensor registrations to what is wanted.
     * - Screen on: shake uses linear acceleration (or accelerometer) and face-down the
     *   accelerometer, each at SENSOR_DELAY_UI (the screen is on, the CPU is awake anyway).
     * - Screen off: ONE accelerometer registration feeds both detectors, batched ~1.2 s. While
     *   proximity reads near (pocket / bag) shake does not need it at all, so the accelerometer is
     *   dropped (or slowed to the face-down rate) until proximity reads far again.
     * Detectors are reset only when they start or stop listening, never on a rate/mode change, so
     * a face-down placement in progress (proximity turns near as it touches the table) still counts.
     */
    private fun applySensors(wantShake: Boolean, wantFace: Boolean, keepFaceDown: Boolean) {
        val sm = sensorManager
        var shakeNow = false
        var faceNow = false
        if (sm != null && screenOn) {
            unregisterOff()
            if (wantShake) shakeNow = registerShakeOn(sm) else unregisterShakeOn()
            if (wantFace) faceNow = registerFaceOn(sm) else unregisterFaceOn()
        } else {
            unregisterShakeOn()
            unregisterFaceOn()
            val shakeOff = wantShake && proximityNear != true
            if (sm != null && (shakeOff || wantFace)) {
                val ok = registerOff(sm, shake = shakeOff, face = wantFace)
                shakeNow = ok && shakeOff
                faceNow = ok && wantFace
            } else {
                unregisterOff()
            }
        }

        if (shakeNow != shakeListening) {
            shakeGravityFilter.reset()
            shakeDetector.reset()
            pocketShakeDetector.reset()
            shakeListening = shakeNow
        }
        if (faceNow != faceListening) {
            faceDownDetector.reset(keepFaceDown = keepFaceDown)
            faceListening = faceNow
        }
    }

    private fun safeRegister(sm: SensorManager, listener: SensorEventListener, sensor: Sensor, samplingUs: Int, latencyUs: Int) =
        runCatching { sm.registerListener(listener, sensor, samplingUs, latencyUs) }.getOrDefault(false)

    private fun registerShakeOn(sm: SensorManager): Boolean {
        val linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linear ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        val wanted = Registration(sensor)
        if (shakeRegistration == wanted) return true
        unregisterShakeOn()
        if (shakeUsesLinear != (linear != null)) shakeGravityFilter.reset()
        shakeUsesLinear = linear != null
        val ok = safeRegister(sm, shakeListener, sensor, SensorManager.SENSOR_DELAY_UI, 0)
        shakeRegistration = if (ok) wanted else null
        return ok
    }

    private fun unregisterShakeOn() {
        if (shakeRegistration == null) return
        runCatching { sensorManager?.unregisterListener(shakeListener) }
        shakeRegistration = null
    }

    private fun registerFaceOn(sm: SensorManager): Boolean {
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        val wanted = Registration(sensor)
        if (faceRegistration == wanted) return true
        unregisterFaceOn()
        val ok = safeRegister(sm, faceListener, sensor, SensorManager.SENSOR_DELAY_UI, FACE_ON_LATENCY_US)
        faceRegistration = if (ok) wanted else null
        return ok
    }

    private fun unregisterFaceOn() {
        if (faceRegistration == null) return
        runCatching { sensorManager?.unregisterListener(faceListener) }
        faceRegistration = null
    }

    /**
     * Screen off. The wake-up accelerometer is used when its FIFO can hold at least half the
     * wanted batch (with audio offload the CPU sleeps during playback, so a non-wake-up sensor
     * would deliver nothing); it then wakes the CPU about once per batch, never per sample.
     * Otherwise the normal accelerometer (delivers only while the CPU is awake).
     */
    private fun registerOff(sm: SensorManager, shake: Boolean, face: Boolean): Boolean {
        val samplingUs = if (shake) SHAKE_OFF_SAMPLING_US else FACE_OFF_SAMPLING_US
        val desiredLatencyUs = if (shake) SHAKE_OFF_LATENCY_US else FACE_OFF_LATENCY_US
        val wakeUp = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        val wakeUpMaxLatencyUs = wakeUp?.let { (it.fifoMaxEventCount - FIFO_MARGIN_EVENTS).toLong() * samplingUs } ?: 0L
        val sensor: Sensor
        val latencyUs: Int
        if (wakeUp != null && wakeUpMaxLatencyUs >= desiredLatencyUs / 2) {
            sensor = wakeUp
            latencyUs = minOf(desiredLatencyUs.toLong(), wakeUpMaxLatencyUs).toInt()
        } else {
            sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
            latencyUs = desiredLatencyUs
        }
        val wanted = OffRegistration(sensor, samplingUs, latencyUs, shake, face)
        if (offRegistration == wanted) return true
        unregisterOff()
        val ok = safeRegister(sm, offListener, sensor, samplingUs, latencyUs)
        offRegistration = if (ok) wanted else null
        return ok
    }

    private fun unregisterOff() {
        if (offRegistration == null) return
        runCatching { sensorManager?.unregisterListener(offListener) }
        offRegistration = null
    }

    /** Screen-off samples (possibly a whole batch at once; times come from event.timestamp). */
    private val offListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            if (v.size < 3) return
            val registration = offRegistration ?: return
            val timeMs = event.timestamp / 1_000_000L
            if (registration.shake) {
                val magnitude = shakeGravityFilter.update(v[0], v[1], v[2])
                if (pocketShakeDetector.onSample(magnitude, timeMs, proximityNear)) onShake(screenOffShake = true)
            }
            if (registration.face) onFaceSample(v[0], v[1], v[2], timeMs)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    // ------------------------------------------------------------------------------ shake

    private var shakeUsesLinear = true

    /** Screen-on shake samples. */
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            if (v.size < 3) return
            if (shakeRegistration == null) return
            val magnitude = if (shakeUsesLinear) {
                sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            } else {
                shakeGravityFilter.update(v[0], v[1], v[2])
            }
            if (shakeDetector.onSample(magnitude, event.timestamp / 1_000_000L)) onShake(screenOffShake = false)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun onShake(screenOffShake: Boolean) {
        // A late sample from the other mode (screen just turned on/off) must not count.
        if (screenOffShake == screenOn) return
        if (screenOffShake && proximityNear == true) return
        val player = host?.currentPlayer() ?: return
        if (!player.isPlaying || player.mediaItemCount < 2) return
        // Felt with the screen off too (MusicService is a foreground service while playing).
        haptics().celebration()
        if (!screenOffShake) showToast("Shuffled")
        if (!player.shuffleModeEnabled) {
            host?.setShuffleEnabled(true)
            // Give the app a moment to reorder the queue before jumping.
            mainHandler.postDelayed({ skipToRandom() }, SHUFFLE_SETTLE_MS)
        } else {
            skipToRandom()
        }
    }

    private fun skipToRandom() {
        runCatching {
            val player = host?.currentPlayer() ?: return
            val count = player.mediaItemCount
            if (count < 2) return
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                val current = player.currentMediaItemIndex
                var target = Random.nextInt(count - 1)
                if (target >= current) target++
                player.seekTo(target.coerceIn(0, count - 1), 0L)
            }
        }.onFailure { Timber.tag(TAG).w(it, "Shake skip failed") }
    }

    // ------------------------------------------------------------------------------ face-down

    /** Screen-on face-down samples. */
    private val faceListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            if (v.size < 3) return
            if (faceRegistration == null) return
            onFaceSample(v[0], v[1], v[2], event.timestamp / 1_000_000L)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Raw accelerometer: the detector itself requires the vector to be still (≈ gravity). */
    private fun onFaceSample(x: Float, y: Float, z: Float, timeMs: Long) {
        when (faceDownDetector.onSample(x, y, z, timeMs, proximityNear)) {
            FaceDownDetector.Event.FACE_DOWN -> onFaceDown()
            FaceDownDetector.Event.FACE_UP -> onFaceUp()
            null -> Unit
        }
    }

    private fun onFaceDown() {
        val player = host?.currentPlayer() ?: return
        if (!player.isPlaying) return
        // A clear double tap, so it is felt through a table with the screen off.
        haptics().success()
        // Mark before pausing: pause() can call back into onIsPlayingChanged synchronously.
        if (settings.resumeWhenFaceUp) pausedByFaceDownAtMs = SystemClock.elapsedRealtime()
        player.pause()
        evaluate()
    }

    private fun onFaceUp() {
        if (!waitingToResume()) return
        pausedByFaceDownAtMs = null
        val player = host?.currentPlayer() ?: return
        if (!player.isPlaying) {
            haptics().toggleOn()
            player.play()
        }
        evaluate()
    }

    // ------------------------------------------------------------------------------ proximity

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val distance = event.values.firstOrNull() ?: return
            val range = event.sensor.maximumRange
            val near = distance < range && distance < PROXIMITY_NEAR_CM
            if (near) pocketShakeDetector.reset()
            val changed = proximityNear != near
            proximityNear = near
            // Screen off: near drops the shake accelerometer, far brings it back.
            if (changed && !screenOn) evaluate()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun registerProximity() {
        if (proximityRegistered) return
        val sm = sensorManager ?: return
        proximityNear = null
        // Prefer the non-wake-up variant; the default one is wake-up but on-change, so still cheap.
        val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, false)
            ?: sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            ?: return
        proximityRegistered = runCatching {
            sm.registerListener(proximityListener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)
    }

    private fun unregisterProximity() {
        if (!proximityRegistered) return
        runCatching { sensorManager?.unregisterListener(proximityListener) }
        proximityRegistered = false
        proximityNear = null
    }

    // ------------------------------------------------------------------------------ helpers

    private fun haptics(): NobodyListensHaptics =
        NobodyListensHapticsEngine.current() ?: runCatching<NobodyListensHaptics> { hapticsEngine.get() }.getOrElse { NobodyListensHaptics.get() }

    private fun showToast(text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val TAG = "GestureController"
        const val SHUFFLE_SETTLE_MS = 650L
        // Only resume if flipped back soon; after this the user presses play themselves.
        const val RESUME_WINDOW_MS = 2 * 60 * 1000L
        // Keep listening briefly after playback stops (track changes, buffering).
        const val PAUSE_GRACE_MS = 3_000L
        const val PROXIMITY_NEAR_CM = 5f
        const val FACE_ON_LATENCY_US = 100_000
        const val FACE_OFF_SAMPLING_US = 200_000 // ~5 Hz, like SENSOR_DELAY_NORMAL
        const val FACE_OFF_LATENCY_US = 1_000_000
        const val SHAKE_OFF_SAMPLING_US = 20_000 // 50 Hz
        // ~1 CPU wake-up per 1.2 s; a double shake is noticed up to this late.
        const val SHAKE_OFF_LATENCY_US = 1_200_000
        const val FIFO_MARGIN_EVENTS = 10
    }
}
