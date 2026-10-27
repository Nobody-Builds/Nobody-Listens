package com.nobodybuilds.listens.data.gestures

import kotlin.math.abs
import kotlin.math.sqrt

/** Standard gravity in m/s². */
const val STANDARD_GRAVITY = 9.80665f

/** How hard a shake must be. Thresholds are peaks of linear acceleration (gravity removed). */
enum class ShakeSensitivity(val label: String, val thresholdG: Float) {
    LOW("Low", 3.0f),
    MEDIUM("Medium", 2.5f),
    HIGH("High", 2.0f);

    val thresholdMs2: Float get() = thresholdG * STANDARD_GRAVITY

    /** Screen off (phone may be in a pocket): every level needs a firmer shake. */
    val screenOffThresholdMs2: Float get() = (thresholdG + SCREEN_OFF_EXTRA_G) * STANDARD_GRAVITY

    private companion object {
        const val SCREEN_OFF_EXTRA_G = 0.75f
    }
}

/**
 * Pure shake detector. Feed it the magnitude of linear acceleration (m/s², gravity removed).
 *
 * A "peak" is a rising edge above [thresholdMs2]; the detector re-arms once the magnitude falls
 * below [rearmFraction] × threshold, so one long jolt counts once. [requiredPeaks] peaks within
 * [windowMs] fire a shake; after that nothing fires for [cooldownMs].
 */
class ShakeDetector(
    var thresholdMs2: Float = ShakeSensitivity.MEDIUM.thresholdMs2,
    private val requiredPeaks: Int = 2,
    private val windowMs: Long = 600L,
    private val cooldownMs: Long = 2_000L,
    private val rearmFraction: Float = 0.5f,
) {
    private val peaks = ArrayDeque<Long>()
    private var armed = true
    private var lastShakeMs: Long? = null

    /** Returns true exactly when a shake is detected. */
    fun onSample(magnitudeMs2: Float, timeMs: Long): Boolean {
        if (magnitudeMs2.isNaN()) return false
        val inCooldown = lastShakeMs?.let { timeMs - it < cooldownMs } ?: false

        if (armed) {
            if (magnitudeMs2 >= thresholdMs2) {
                armed = false
                if (inCooldown) {
                    peaks.clear()
                    return false
                }
                peaks.addLast(timeMs)
                while (peaks.isNotEmpty() && timeMs - peaks.first() > windowMs) peaks.removeFirst()
                if (peaks.size >= requiredPeaks) {
                    peaks.clear()
                    lastShakeMs = timeMs
                    return true
                }
            }
        } else if (magnitudeMs2 < thresholdMs2 * rearmFraction) {
            armed = true
        }
        return false
    }

    fun reset() {
        peaks.clear()
        armed = true
        // Keep the cooldown: re-registering sensors must not allow an instant double shuffle.
    }
}

/**
 * Low-pass gravity estimate for plain accelerometers (used when the device has no
 * TYPE_LINEAR_ACCELERATION / TYPE_GRAVITY sensor).
 */
class GravityFilter(private val alpha: Float = 0.8f) {
    val gravity = FloatArray(3)
    private var initialized = false

    /** Updates the gravity estimate and returns the linear acceleration magnitude. */
    fun update(x: Float, y: Float, z: Float): Float {
        if (!initialized) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            initialized = true
        } else {
            gravity[0] = alpha * gravity[0] + (1 - alpha) * x
            gravity[1] = alpha * gravity[1] + (1 - alpha) * y
            gravity[2] = alpha * gravity[2] + (1 - alpha) * z
        }
        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        return sqrt(lx * lx + ly * ly + lz * lz)
    }

    fun reset() {
        initialized = false
    }
}

/**
 * Pure screen-off shake detector: a firmer DOUBLE shake. One "shake" is what [ShakeDetector]
 * reports (two peaks within 600 ms); two of those within [windowMs] fire. While the proximity
 * sensor reports near (phone in a pocket or bag) every sample is ignored and pending shakes are
 * forgotten. Unknown proximity (no sensor / no reading yet) is not a reason to ignore.
 */
class PocketShakeDetector(
    thresholdMs2: Float = ShakeSensitivity.MEDIUM.screenOffThresholdMs2,
    private val windowMs: Long = 1_200L,
    private val cooldownMs: Long = 2_000L,
) {
    private val single = ShakeDetector(thresholdMs2 = thresholdMs2, cooldownMs = 0L)
    private var firstShakeMs: Long? = null
    private var lastFireMs: Long? = null

    var thresholdMs2: Float
        get() = single.thresholdMs2
        set(value) { single.thresholdMs2 = value }

    /** Returns true exactly when a double shake is detected. */
    fun onSample(magnitudeMs2: Float, timeMs: Long, proximityNear: Boolean? = null): Boolean {
        if (magnitudeMs2.isNaN()) return false
        if (proximityNear == true) {
            single.reset()
            firstShakeMs = null
            return false
        }
        firstShakeMs?.let { if (timeMs - it > windowMs) firstShakeMs = null }
        if (!single.onSample(magnitudeMs2, timeMs)) return false

        if (lastFireMs?.let { timeMs - it < cooldownMs } == true) {
            firstShakeMs = null
            return false
        }
        if (firstShakeMs == null) {
            firstShakeMs = timeMs
            return false
        }
        firstShakeMs = null
        lastFireMs = timeMs
        return true
    }

    fun reset() {
        single.reset()
        firstShakeMs = null
        // Keep the cooldown, like ShakeDetector.
    }
}

/**
 * Pure face-down debouncer. Feed it the acceleration / gravity vector (m/s²; z = +9.8 screen up,
 * −9.8 screen down) and, when a proximity sensor exists, whether it reports "near".
 *
 * "Face-down" means lying flat and still with the screen down: z below [faceDownZ], the
 * horizontal part sqrt(x² + y²) below [maxTiltMs2], the overall magnitude close to gravity, and
 * the vector moving less than [stableToleranceMs2] from where the hold started. A phone standing
 * upright in a pocket (z ≈ 0) can never qualify. Proximity "near" is fine (that's the table);
 * proximity "far" blocks (the screen is not against anything).
 *
 * - [Event.FACE_DOWN] fires once the phone stays face-down for [holdMs]. It only fires after the
 *   phone was seen not face-down first (so registering while already face-down never pauses), and
 *   fires once per placement.
 * - [Event.FACE_UP] fires after a FACE_DOWN, once z stays above [faceUpZ] for [upHoldMs].
 */
class FaceDownDetector(
    private val faceDownZ: Float = -8.8f,
    private val maxTiltMs2: Float = 2.5f,
    private val maxMagnitudeErrorMs2: Float = 1.5f,
    private val stableToleranceMs2: Float = 1.2f,
    private val holdMs: Long = 800L,
    private val faceUpZ: Float = 3.0f,
    private val upHoldMs: Long = 400L,
    private val rearmZ: Float = -5.0f,
) {
    enum class Event { FACE_DOWN, FACE_UP }

    private var armed = false
    private var faceDown = false
    private var downSinceMs: Long? = null
    private var upSinceMs: Long? = null
    private val holdStart = FloatArray(3)

    val isFaceDown: Boolean get() = faceDown

    fun onSample(x: Float, y: Float, z: Float, timeMs: Long, proximityNear: Boolean? = null): Event? {
        if (x.isNaN() || y.isNaN() || z.isNaN()) return null

        if (z > rearmZ) {
            armed = true
            downSinceMs = null
        }

        if (faceDown) {
            if (z > faceUpZ) {
                val since = upSinceMs ?: timeMs.also { upSinceMs = it }
                if (timeMs - since >= upHoldMs) {
                    faceDown = false
                    upSinceMs = null
                    return Event.FACE_UP
                }
            } else {
                upSinceMs = null
            }
            return null
        }

        val flatDown = z < faceDownZ &&
            sqrt(x * x + y * y) < maxTiltMs2 &&
            abs(sqrt(x * x + y * y + z * z) - STANDARD_GRAVITY) < maxMagnitudeErrorMs2
        if (!armed || !flatDown || proximityNear == false) {
            downSinceMs = null
            return null
        }

        val since = downSinceMs
        if (since == null) {
            downSinceMs = timeMs
            holdStart[0] = x; holdStart[1] = y; holdStart[2] = z
            return null
        }
        val dx = x - holdStart[0]
        val dy = y - holdStart[1]
        val dz = z - holdStart[2]
        if (sqrt(dx * dx + dy * dy + dz * dz) > stableToleranceMs2) {
            // Still moving: restart the hold from here.
            downSinceMs = timeMs
            holdStart[0] = x; holdStart[1] = y; holdStart[2] = z
            return null
        }
        if (timeMs - since >= holdMs) {
            armed = false
            faceDown = true
            downSinceMs = null
            upSinceMs = null
            return Event.FACE_DOWN
        }
        return null
    }

    /**
     * Sensors were unregistered. Forgets timers; the next placement must start face-up again.
     * With [keepFaceDown] a pending FACE_UP (for "resume when face-up") survives the gap.
     */
    fun reset(keepFaceDown: Boolean = false) {
        armed = false
        if (!keepFaceDown) faceDown = false
        downSinceMs = null
        upSinceMs = null
    }
}
