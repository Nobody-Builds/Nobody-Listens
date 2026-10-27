package com.nobodybuilds.listens.data.lofi.dsp

import com.nobodybuilds.listens.data.lofi.AmbienceKind
import kotlin.math.abs
import kotlin.math.max

/**
 * Ambient layers (rain / café / fireplace / waves) mixed on their own, for playing over
 * regular music. Volumes ramp; a layer at zero costs nothing.
 */
class AmbienceMixer(private val sampleRate: Int = 44_100, seed: Long = 7L) : StereoSource {
    private val voices: Array<StereoVoice> = arrayOf(
        RainVoice(sampleRate, seed),
        CafeVoice(sampleRate, seed + 1),
        FireplaceVoice(sampleRate, seed + 2),
        WavesVoice(sampleRate, seed + 3),
    )
    private val gains = Array(voices.size) { SmoothedValue(0f, 300f, sampleRate) }
    /** Loudness trims so every layer sits at a similar level at the same volume. */
    private val trims = floatArrayOf(2.2f, 1.3f, 0.4f, 0.7f)
    private val targets = FloatArray(voices.size)
    @Volatile private var targetSerial = 0
    private var appliedSerial = -1

    private val fade = LinearRamp(0f, sampleRate)
    @Volatile private var fadeTarget = 0f
    @Volatile private var fadeMs = 800f
    @Volatile private var fadeSerial = 0
    private var appliedFade = -1
    @Volatile private var stopWhenSilent = false
    private val meter = LevelFollower(0.9f)

    val level: Float get() = meter.level

    fun setVolume(kind: AmbienceKind, volume: Float) {
        targets[kind.ordinal] = volume.coerceIn(0f, 1f)
        targetSerial++
    }

    fun fadeIn(ms: Float = 800f) { stopWhenSilent = false; fadeTarget = 1f; fadeMs = ms; fadeSerial++ }
    fun fadeOut(ms: Float = 600f) { stopWhenSilent = true; fadeTarget = 0f; fadeMs = ms; fadeSerial++ }

    override fun render(left: FloatArray, right: FloatArray, frames: Int): Boolean {
        if (targetSerial != appliedSerial) {
            appliedSerial = targetSerial
            for (i in gains.indices) gains[i].target = targets[i]
        }
        if (fadeSerial != appliedFade) {
            appliedFade = fadeSerial
            fade.rampTo(fadeTarget, fadeMs)
        }
        var peak = 0f
        for (n in 0 until frames) {
            var l = 0f
            var r = 0f
            for (i in voices.indices) {
                val g = gains[i].next()
                if (g < 0.0005f && gains[i].target == 0f) continue
                val v = voices[i]
                v.process()
                val t = g * trims[i]
                l += v.l * t
                r += v.r * t
            }
            val f = fade.next()
            l = softClip(l * f * 0.9f)
            r = softClip(r * f * 0.9f)
            left[n] = l
            right[n] = r
            val p = max(abs(l), abs(r))
            if (p > peak) peak = p
        }
        meter.push(peak)
        return !(stopWhenSilent && fade.isDone && fade.value <= 0f)
    }
}

/** A soft two-note bell chime, rendered once into a buffer (mono). */
object ChimeSynth {
    fun render(sampleRate: Int = 44_100, seconds: Float = 3.2f): FloatArray {
        val n = (sampleRate * seconds).toInt()
        val out = FloatArray(n)
        // Bell partial ratios and their decay times.
        val ratios = floatArrayOf(1f, 2.0f, 2.76f, 5.4f, 8.93f)
        val levels = floatArrayOf(1f, 0.5f, 0.35f, 0.18f, 0.08f)
        val decays = floatArrayOf(2.6f, 1.6f, 1.1f, 0.5f, 0.25f)
        addBell(out, sampleRate, 0, midiToHz(76f), ratios, levels, decays, 0.32f)        // E5
        addBell(out, sampleRate, (sampleRate * 0.18f).toInt(), midiToHz(83f), ratios, levels, decays, 0.22f) // B5
        // Gentle attack to avoid a hard click.
        val attack = (sampleRate * 0.004f).toInt()
        for (i in 0 until attack.coerceAtMost(n)) out[i] *= i.toFloat() / attack
        // Fade the last 150 ms.
        val tail = (sampleRate * 0.15f).toInt().coerceAtMost(n)
        for (i in 0 until tail) out[n - 1 - i] *= i.toFloat() / tail
        return out
    }

    private fun addBell(
        out: FloatArray, sampleRate: Int, start: Int, freq: Float,
        ratios: FloatArray, levels: FloatArray, decays: FloatArray, gain: Float,
    ) {
        for (p in ratios.indices) {
            val inc = freq * ratios[p] / sampleRate
            val coef = decayCoef(decays[p], sampleRate)
            var amp = levels[p] * gain
            var phase = 0f
            var i = start
            while (i < out.size && amp > 1e-5f) {
                phase = wrapPhase(phase + inc)
                out[i] += SineTable.sin(phase) * amp
                amp *= coef
                i++
            }
        }
    }
}
