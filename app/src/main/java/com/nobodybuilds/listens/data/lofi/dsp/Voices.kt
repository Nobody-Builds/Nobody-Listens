package com.nobodybuilds.listens.data.lofi.dsp

import kotlin.math.cos
import kotlin.math.sin

// Instrument voices for the lofi generator. Allocation-free; trigger() is called from the
// render thread's sequencer, process() once per sample.

/** Two-operator FM electric piano with a short tine partial. */
internal class EpVoice(private val sampleRate: Int) {
    var active = false
        private set
    var amp = 0f
        private set
    var panL = 0.7f
        private set
    var panR = 0.7f
        private set
    var releaseStep = -1L
    var note = 0

    private var freq = 0f
    private var cPhase = 0f
    private var mPhase = 0f
    private var tPhase = 0f
    private var vel = 0f
    private var delay = 0
    private var attackLeft = 0
    private var attackInc = 0f
    private var decay = 1f
    private var releasing = false
    private val releaseCoef = decayCoef(0.28f, sampleRate)
    private var index = 0f
    private var indexFloor = 0f
    private val indexDecay = decayCoef(0.9f, sampleRate)
    private var tine = 0f
    private val tineDecay = decayCoef(0.12f, sampleRate)
    private var tineRatio = 4f

    fun trigger(midi: Int, velocity: Float, delaySamples: Int, pan: Float, brightness: Float, decaySec: Float) {
        note = midi
        freq = midiToHz(midi.toFloat())
        vel = velocity
        delay = delaySamples
        cPhase = 0f; mPhase = 0f; tPhase = 0f
        amp = 0f
        val attackSamples = (sampleRate * 0.004f).toInt().coerceAtLeast(1)
        attackLeft = attackSamples
        attackInc = 1f / attackSamples
        decay = decayCoef(decaySec, sampleRate)
        releasing = false
        index = (0.18f + 0.32f * brightness) * (0.6f + 0.4f * velocity)
        indexFloor = 0.04f + 0.05f * brightness
        tine = 0.22f * velocity * (0.5f + brightness)
        tineRatio = if (midi > 72) 3f else 4f
        val angle = pan.coerceIn(0f, 1f) * 1.5707964f
        panL = cos(angle)
        panR = sin(angle)
        releaseStep = -1L
        active = true
    }

    fun release() {
        if (active) releasing = true
    }

    /** Returns the mono sample; [rate] is the tape-wobble pitch factor. */
    fun process(rate: Float): Float {
        if (!active) return 0f
        if (delay > 0) { delay--; return 0f }
        val inc = freq * rate / sampleRate
        mPhase = wrapPhase(mPhase + inc)
        cPhase = wrapPhase(cPhase + inc)
        tPhase = wrapPhase(tPhase + inc * tineRatio)
        val mod = SineTable.sin(mPhase) * index
        val body = SineTable.sin(cPhase + mod)
        val t = SineTable.sin(tPhase) * tine
        index = indexFloor + (index - indexFloor) * indexDecay
        tine *= tineDecay
        if (tine < 1e-7f) tine = 0f
        if (attackLeft > 0) {
            amp += attackInc
            attackLeft--
        } else {
            amp *= if (releasing) releaseCoef else decay
            if (amp < 0.0004f) { active = false; amp = 0f }
        }
        return (body + t) * amp * vel
    }
}

/** Round sine bass with a little second harmonic and soft saturation. */
internal class BassVoice(private val sampleRate: Int) {
    var amp = 0f
        private set
    private var active = false
    private var freq = 0f
    private var phase = 0f
    private var vel = 0f
    private var attackLeft = 0
    private var attackInc = 0f
    private val decay = decayCoef(1.8f, sampleRate)
    private var releasing = false
    private val releaseCoef = decayCoef(0.09f, sampleRate)
    private var delay = 0

    fun trigger(midi: Int, velocity: Float, delaySamples: Int) {
        freq = midiToHz(midi.toFloat())
        vel = velocity
        delay = delaySamples
        // Keep phase continuous for a click-free retrigger; ramp from current level.
        val attackSamples = (sampleRate * 0.008f).toInt()
        attackLeft = attackSamples
        attackInc = (1f - amp) / attackSamples
        releasing = false
        active = true
    }

    fun release() { releasing = true }

    fun process(rate: Float): Float {
        if (!active) return 0f
        if (delay > 0) {
            delay--
            amp *= releaseCoef
        } else if (attackLeft > 0) {
            amp += attackInc
            attackLeft--
        } else {
            amp *= if (releasing) releaseCoef else decay
            if (amp < 0.0003f) { active = false; amp = 0f; return 0f }
        }
        phase = wrapPhase(phase + freq * rate / sampleRate)
        val s = SineTable.sin(phase) + 0.28f * SineTable.sin(phase * 2f)
        return softClip(s * 1.3f) * amp * vel
    }
}

/** Kick: sine with a fast pitch drop and a tiny click. */
internal class KickVoice(private val sampleRate: Int, private val rng: FastRandom) {
    var amp = 0f
        private set
    private var phase = 0f
    private var pitchEnv = 0f
    private var vel = 0f
    private var click = 0f
    private val pitchDecay = decayCoef(0.06f, sampleRate)
    private val ampDecay = decayCoef(0.5f, sampleRate)
    private val clickDecay = decayCoef(0.004f, sampleRate)

    fun trigger(velocity: Float) {
        vel = velocity
        amp = 1f
        pitchEnv = 1f
        phase = 0f
        click = 0.4f
    }

    fun process(): Float {
        if (amp < 0.0003f) return 0f
        val f = 46f + 88f * pitchEnv
        phase = wrapPhase(phase + f / sampleRate)
        val s = SineTable.sin(phase) * amp + rng.bipolar() * click
        pitchEnv *= pitchDecay
        amp *= ampDecay
        click *= clickDecay
        if (click < 1e-7f) click = 0f
        if (pitchEnv < 1e-7f) pitchEnv = 0f
        return softClip(s * 1.6f) * vel
    }
}

/** Snare: band-passed noise burst plus a short tone. */
internal class SnareVoice(private val sampleRate: Int, private val rng: FastRandom) {
    var amp = 0f
        private set
    private var tone = 0f
    private var phase = 0f
    private var vel = 0f
    private val noiseDecay = decayCoef(0.2f, sampleRate)
    private val toneDecay = decayCoef(0.09f, sampleRate)
    private val bp = Svf(sampleRate, 1800f, 0.8f)

    fun trigger(velocity: Float) {
        vel = velocity
        amp = 1f
        tone = 1f
        phase = 0f
    }

    fun process(): Float {
        if (amp < 0.0003f) return 0f
        bp.process(rng.bipolar())
        phase = wrapPhase(phase + 188f / sampleRate)
        val s = bp.band * amp * 1.4f + SineTable.sin(phase) * tone * 0.45f
        amp *= noiseDecay
        tone *= toneDecay
        if (tone < 1e-7f) tone = 0f
        return s * vel
    }
}

/** Hi-hat: high-passed noise, closed or open. */
internal class HatVoice(private val sampleRate: Int, private val rng: FastRandom) {
    var amp = 0f
        private set
    private var vel = 0f
    private var decay = 1f
    private val closed = decayCoef(0.045f, sampleRate)
    private val open = decayCoef(0.28f, sampleRate)
    private val hp = Svf(sampleRate, 7200f, 0.7f)

    fun trigger(velocity: Float, isOpen: Boolean) {
        vel = velocity
        amp = 1f
        decay = if (isOpen) open else closed
    }

    fun process(): Float {
        if (amp < 0.0003f) return 0f
        hp.process(rng.bipolar())
        val s = hp.high * amp
        amp *= decay
        return s * vel
    }
}
