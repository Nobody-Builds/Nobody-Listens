package com.nobodybuilds.listens.data.lofi.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

// Small, allocation-free DSP building blocks for the lofi engine. Everything here is plain
// Kotlin (no Android), so it can be unit tested on the JVM. All processing is per sample and
// none of these classes allocate after construction.

const val TWO_PI_F = (2.0 * PI).toFloat()

/** Sine lookup table; phase is in cycles (any real value, wrapped internally). */
object SineTable {
    private const val SIZE = 4096
    private const val MASK = SIZE - 1
    private val table = FloatArray(SIZE + 1) { i -> sin(2.0 * PI * i / SIZE).toFloat() }

    // Offset keeps the index positive for phases down to -16 cycles without calling floor().
    private const val OFFSET = SIZE * 16f

    fun sin(phase: Float): Float {
        val p = phase * SIZE + OFFSET
        val i = p.toInt()
        val f = p - i
        val idx = i and MASK
        val a = table[idx]
        return a + (table[idx + 1] - a) * f
    }
}

/** Cheap tanh-like soft clipper, exact ±1 beyond ±3. */
fun softClip(x: Float): Float {
    if (x > 3f) return 1f
    if (x < -3f) return -1f
    val x2 = x * x
    return x * (27f + x2) / (27f + 9f * x2)
}

/** Wraps a phase in cycles back into [0, 1). */
fun wrapPhase(phase: Float): Float = if (phase >= 1f) { if (phase < 2f) phase - 1f else phase - floor(phase) } else phase

/** xorshift32 — fast deterministic random numbers. */
class FastRandom(seed: Long) {
    private var state: Int = mix(seed)

    fun reseed(seed: Long) { state = mix(seed) }

    fun nextInt(): Int {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return x
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextInt() ushr 8) * (1f / 16_777_216f)

    /** Uniform in [-1, 1). */
    fun bipolar(): Float = nextFloat() * 2f - 1f

    /** Uniform integer in [0, bound). */
    fun nextInt(bound: Int): Int = if (bound <= 1) 0 else ((nextInt() ushr 1) % bound)

    fun chance(p: Float): Boolean = nextFloat() < p

    private companion object {
        fun mix(seed: Long): Int {
            var z = seed + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            z = z xor (z ushr 31)
            val v = (z xor (z ushr 32)).toInt()
            return if (v == 0) 0x2545F491 else v
        }
    }
}

/** Paul Kellet's economy pink noise filter over white noise. */
class PinkNoise(seed: Long) {
    private val rng = FastRandom(seed)
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f

    fun next(): Float {
        val w = rng.bipolar()
        b0 = 0.99765f * b0 + w * 0.0990460f
        b1 = 0.96300f * b1 + w * 0.2965164f
        b2 = 0.57000f * b2 + w * 1.0526913f
        return (b0 + b1 + b2 + w * 0.1848f) * 0.18f
    }
}

/** One-pole low-pass. Set the cutoff off the per-sample path (it calls exp). */
class OnePoleLowPass(private val sampleRate: Int, cutoffHz: Float) {
    private var coef = 0f
    var state = 0f
        private set

    init { setCutoff(cutoffHz) }

    fun setCutoff(hz: Float) {
        val fc = hz.coerceIn(10f, sampleRate * 0.45f)
        coef = (1.0 - exp(-2.0 * PI * fc / sampleRate)).toFloat()
    }

    fun process(x: Float): Float {
        state += coef * (x - state)
        return state
    }

    fun reset() { state = 0f }
}

/**
 * Topology-preserving state variable filter (Cytomic / Simper). After [process], read
 * [low], [band] and [high].
 */
class Svf(private val sampleRate: Int, cutoffHz: Float, q: Float = 0.707f) {
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var k = 1.414f
    private var ic1 = 0f
    private var ic2 = 0f
    var low = 0f
        private set
    var band = 0f
        private set
    var high = 0f
        private set

    init { set(cutoffHz, q) }

    fun set(cutoffHz: Float, q: Float = 1f / k) {
        val fc = cutoffHz.coerceIn(10f, sampleRate * 0.45f)
        val g = tan(PI * fc / sampleRate).toFloat()
        k = 1f / max(q, 0.05f)
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun process(x: Float) {
        val v3 = x - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2f * v1 - ic1
        ic2 = 2f * v2 - ic2
        low = v2
        band = v1
        high = x - k * v1 - v2
    }

    fun reset() { ic1 = 0f; ic2 = 0f; low = 0f; band = 0f; high = 0f }
}

/**
 * Exponential parameter smoother (one-pole towards [target]). Used for every level the user
 * can change while playing, so changes never click.
 */
class SmoothedValue(initial: Float, timeMs: Float, private val sampleRate: Int) {
    var target: Float = initial
    var value: Float = initial
        private set
    private var coef = 0f

    init { setTime(timeMs) }

    fun setTime(timeMs: Float) {
        val samples = max(1f, timeMs * 0.001f * sampleRate)
        coef = (1.0 - exp(-1.0 / samples)).toFloat()
    }

    fun next(): Float {
        value += (target - value) * coef
        return value
    }

    /** Advances [frames] samples at once (for block-rate parameters). */
    fun advance(frames: Int): Float {
        val keep = Math.pow((1f - coef).toDouble(), frames.toDouble()).toFloat()
        value = target + (value - target) * keep
        return value
    }

    fun snap(v: Float = target) { target = v; value = v }
}

/** Linear ramp to a target over a fixed time; used for fades (start, stop, sleep timer). */
class LinearRamp(initial: Float, private val sampleRate: Int) {
    var value: Float = initial
        private set
    var target: Float = initial
        private set
    private var step = 0f

    val isDone: Boolean get() = value == target

    fun rampTo(newTarget: Float, timeMs: Float) {
        target = newTarget
        val samples = timeMs * 0.001f * sampleRate
        if (samples < 1f) { value = newTarget; step = 0f; return }
        step = (newTarget - value) / samples
    }

    fun next(): Float {
        if (value != target) {
            val v = value + step
            value = if ((step > 0f && v >= target) || (step < 0f && v <= target) || step == 0f) target else v
        }
        return value
    }

    fun snap(v: Float) { value = v; target = v; step = 0f }
}

/** Returns the per-sample multiplier for an exponential decay to -60 dB in [timeSec]. */
fun decayCoef(timeSec: Float, sampleRate: Int): Float =
    exp(-6.9077554 / (max(0.001f, timeSec) * sampleRate)).toFloat()

fun midiToHz(note: Float): Float = (440.0 * Math.pow(2.0, (note - 69.0) / 12.0)).toFloat()
