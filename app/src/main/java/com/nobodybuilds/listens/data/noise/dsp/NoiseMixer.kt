package com.nobodybuilds.listens.data.noise.dsp

import com.nobodybuilds.listens.data.lofi.dsp.LinearRamp
import com.nobodybuilds.listens.data.lofi.dsp.LevelFollower
import com.nobodybuilds.listens.data.lofi.dsp.OnePoleLowPass
import com.nobodybuilds.listens.data.lofi.dsp.SineTable
import com.nobodybuilds.listens.data.lofi.dsp.SmoothedValue
import com.nobodybuilds.listens.data.lofi.dsp.StereoSource
import com.nobodybuilds.listens.data.lofi.dsp.StereoVoice
import com.nobodybuilds.listens.data.lofi.dsp.softClip
import com.nobodybuilds.listens.data.lofi.dsp.wrapPhase
import com.nobodybuilds.listens.data.noise.NoiseKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Mixes up to [MAX_LAYERS] noise voices with per-layer volume, a darker ↔ brighter tilt, an
 * optional slow swell, optional binaural tones, a master level and fades.
 *
 * Threading: the control thread calls the setters (they only publish small immutable objects or
 * volatile floats); the audio thread calls [render]. The audio thread never allocates: voices are
 * created by [setLayers] on the caller's thread, and a removed layer fades out in a spare channel.
 */
class NoiseMixer(private val sampleRate: Int = 44_100, private val seed: Long = 11L) : StereoSource {

    private class Spec(val kinds: Array<NoiseKind>, val volumes: FloatArray, val voices: Array<StereoVoice>)

    private class Channel(sampleRate: Int) {
        var kind: NoiseKind? = null
        var voice: StereoVoice? = null
        val gain = SmoothedValue(0f, 450f, sampleRate)
        var swellA = 0f
        var swellB = 0f
        var swellIncA = 0f
        var swellIncB = 0f
        var swellPrev = 1f
    }

    private val voiceCache = HashMap<NoiseKind, StereoVoice>()
    @Volatile private var spec: Spec = Spec(emptyArray(), FloatArray(0), emptyArray())
    private var appliedSpec: Spec? = null
    private val channels = Array(MAX_LAYERS * 2) { Channel(sampleRate) }

    // Block-rate parameters (volatile: written by the control thread).
    @Volatile private var masterTarget = 0.7f
    @Volatile private var toneTarget = 0f
    @Volatile private var swellOn = false
    @Volatile private var duckTarget = 1f
    @Volatile private var levelTarget = 1f
    @Volatile private var binauralLevelTarget = 0f
    @Volatile private var binauralCarrier = 200f
    @Volatile private var binauralBeat = 10f

    private val master = SmoothedValue(0.7f, 250f, sampleRate)
    private val duck = SmoothedValue(1f, 300f, sampleRate)
    private val level = SmoothedValue(1f, 1500f, sampleRate)
    private val swellDepth = SmoothedValue(0f, 2000f, sampleRate)
    private val binLevel = SmoothedValue(0f, 1500f, sampleRate)
    private val lowGain = SmoothedValue(1f, 200f, sampleRate)
    private val highGain = SmoothedValue(1f, 200f, sampleRate)
    private var appliedTone = Float.NaN
    private val tiltL = OnePoleLowPass(sampleRate, TILT_HZ)
    private val tiltR = OnePoleLowPass(sampleRate, TILT_HZ)
    private var binPhaseL = 0f
    private var binPhaseR = 0f

    private val fade = LinearRamp(0f, sampleRate)
    @Volatile private var fadeTarget = 0f
    @Volatile private var fadeMs = 800f
    @Volatile private var fadeSerial = 0
    private var appliedFade = -1
    @Volatile private var stopWhenSilent = false
    private val meter = LevelFollower(0.9f)

    /** Peak output level, 0..1 (for visuals). */
    val levelMeter: Float get() = meter.level

    /**
     * Sets the layers (at most [MAX_LAYERS]; extra entries are ignored). New voices are created
     * here, so call it off the audio thread. Not thread-safe against itself: call from one thread.
     */
    @Synchronized
    fun setLayers(layers: List<Pair<NoiseKind, Float>>) {
        val list = layers.distinctBy { it.first }.take(MAX_LAYERS)
        val kinds = Array(list.size) { list[it].first }
        val volumes = FloatArray(list.size) { list[it].second.coerceIn(0f, 1f) }
        val voices = Array(list.size) { i ->
            val k = kinds[i]
            voiceCache.getOrPut(k) { createNoiseVoice(k, sampleRate, seed + k.ordinal * 101L) }
        }
        spec = Spec(kinds, volumes, voices)
    }

    fun setMaster(volume: Float) { masterTarget = volume.coerceIn(0f, 1f) }

    /** -1 = darker, 0 = flat, +1 = brighter (about ±7 dB of tilt each side of ~700 Hz). */
    fun setTone(tone: Float) { toneTarget = tone.coerceIn(-1f, 1f) }

    /** Gentle slow swell: each layer drifts ±15 % over tens of seconds. */
    fun setSwell(on: Boolean) { swellOn = on }

    fun setDucked(ducked: Boolean) { duckTarget = if (ducked) 0.3f else 1f }

    /** Extra level for long fades driven from outside (sleep timer, focus breaks). 0..1. */
    fun setLevel(value: Float) { levelTarget = value.coerceIn(0f, 1f) }

    /** Binaural tones; [beatHz] ≤ 0 turns them off. Very quiet, sine only. */
    fun setBinaural(carrierHz: Float, beatHz: Float) {
        if (beatHz <= 0f) {
            binauralLevelTarget = 0f
        } else {
            binauralCarrier = carrierHz.coerceIn(80f, 500f)
            binauralBeat = beatHz.coerceIn(1f, 40f)
            binauralLevelTarget = BINAURAL_LEVEL
        }
    }

    fun fadeIn(ms: Float = 800f) { stopWhenSilent = false; fadeTarget = 1f; fadeMs = ms; fadeSerial++ }
    fun fadeOut(ms: Float = 600f) { stopWhenSilent = true; fadeTarget = 0f; fadeMs = ms; fadeSerial++ }

    // ---------------------------------------------------------------- audio thread

    private fun applySpec(s: Spec) {
        appliedSpec = s
        // Layers no longer wanted fade out.
        for (c in channels) {
            val k = c.kind ?: continue
            var idx = -1
            for (i in s.kinds.indices) if (s.kinds[i] == k) { idx = i; break }
            c.gain.target = if (idx >= 0) s.volumes[idx] * k.trim else 0f
        }
        // New layers take a free channel (or the quietest fading one).
        for (i in s.kinds.indices) {
            val k = s.kinds[i]
            var found = false
            for (c in channels) if (c.kind == k) { found = true; break }
            if (found) continue
            var slot: Channel? = null
            for (c in channels) if (c.kind == null) { slot = c; break }
            if (slot == null) {
                var best = Float.MAX_VALUE
                for (c in channels) if (c.gain.target == 0f && c.gain.value < best) { best = c.gain.value; slot = c }
            }
            val c = slot ?: continue
            c.kind = k
            c.voice = s.voices[i]
            c.gain.snap(0f)
            c.gain.target = s.volumes[i] * k.trim
            // Each channel swells on its own slow, unrelated cycle (periods 19–47 s).
            val r = (k.ordinal * 0.37f + i * 0.21f) % 1f
            c.swellA = r
            c.swellB = (r * 1.7f) % 1f
            c.swellIncA = 1f / ((19f + r * 13f) * sampleRate)
            c.swellIncB = 1f / ((31f + r * 16f) * sampleRate)
        }
    }

    private fun applyTone(t: Float) {
        appliedTone = t
        val db = t * TILT_DB
        lowGain.target = 10f.pow(-db / 20f)
        highGain.target = 10f.pow(db / 20f)
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int): Boolean {
        val s = spec
        if (s !== appliedSpec) applySpec(s)
        if (fadeSerial != appliedFade) {
            appliedFade = fadeSerial
            fade.rampTo(fadeTarget, fadeMs)
        }
        val t = toneTarget
        if (t != appliedTone) applyTone(t)
        master.target = masterTarget
        duck.target = duckTarget
        level.target = levelTarget
        swellDepth.target = if (swellOn) SWELL_DEPTH else 0f
        binLevel.target = binauralLevelTarget
        val incL = (binauralCarrier - binauralBeat * 0.5f) / sampleRate
        val incR = (binauralCarrier + binauralBeat * 0.5f) / sampleRate

        // Swell at block rate, interpolated per sample.
        val depth = swellDepth.advance(frames)
        val frameScale = 1f / frames

        var peak = 0f
        for (n in 0 until frames) { left[n] = 0f; right[n] = 0f }
        for (c in channels) {
            val v = c.voice ?: continue
            if (c.gain.target == 0f && c.gain.value < 1e-4f) {
                // Fully faded out: free the channel.
                c.kind = null
                c.voice = null
                continue
            }
            c.swellA = wrapPhase(c.swellA + c.swellIncA * frames)
            c.swellB = wrapPhase(c.swellB + c.swellIncB * frames)
            val swell = 1f - depth * (0.5f + 0.3f * SineTable.sin(c.swellA) + 0.2f * SineTable.sin(c.swellB))
            val s0 = c.swellPrev
            val ds = (swell - s0) * frameScale
            c.swellPrev = swell
            for (n in 0 until frames) {
                v.process()
                val g = c.gain.next() * (s0 + ds * n)
                left[n] += v.l * g
                right[n] += v.r * g
            }
        }
        for (n in 0 until frames) {
            val m = master.next() * duck.next() * level.next() * fade.next()
            var l = left[n]
            var r = right[n]
            // Tilt: split at ~700 Hz, darker lifts lows / cuts highs and the other way round.
            val lg = lowGain.next()
            val hg = highGain.next()
            val ll = tiltL.process(l)
            val lr = tiltR.process(r)
            l = ll * lg + (l - ll) * hg
            r = lr * lg + (r - lr) * hg
            val b = binLevel.next()
            if (b > 1e-5f) {
                binPhaseL = wrapPhase(binPhaseL + incL)
                binPhaseR = wrapPhase(binPhaseR + incR)
                l += SineTable.sin(binPhaseL) * b
                r += SineTable.sin(binPhaseR) * b
            }
            l = softClip(l * m * 0.9f)
            r = softClip(r * m * 0.9f)
            left[n] = l
            right[n] = r
            val p = max(abs(l), abs(r))
            if (p > peak) peak = p
        }
        meter.push(peak)
        return !(stopWhenSilent && fade.isDone && fade.value <= 0f)
    }

    companion object {
        const val MAX_LAYERS = 3
        private const val TILT_HZ = 700f
        private const val TILT_DB = 7f
        private const val SWELL_DEPTH = 0.3f
        private const val BINAURAL_LEVEL = 0.06f
    }
}
