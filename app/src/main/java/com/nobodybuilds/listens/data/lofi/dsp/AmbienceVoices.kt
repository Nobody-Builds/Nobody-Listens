package com.nobodybuilds.listens.data.lofi.dsp

import kotlin.math.abs
import kotlin.math.max

// Procedural ambience: rain, café, fireplace, waves and vinyl crackle. Each generator writes
// one stereo sample into [l]/[r] per call to process(). They never allocate and keep their
// filters' coefficient updates off the per-sample path (every 32 samples at most).

abstract class StereoVoice {
    var l = 0f
        protected set
    var r = 0f
        protected set
    abstract fun process()
}

/** Vinyl: soft pink hiss, random clicks and the odd low pop. */
class VinylCrackle(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val rng = FastRandom(seed xor 0x5EED)
    private val hissL = PinkNoise(seed + 11)
    private val hissR = PinkNoise(seed + 12)
    private val hissLp = OnePoleLowPass(sampleRate, 5200f)
    private val hissLpR = OnePoleLowPass(sampleRate, 5200f)
    private var clickL = 0f
    private var clickR = 0f
    private val clickDecay = decayCoef(0.0016f, sampleRate)
    private var pop = 0f
    private val popDecay = decayCoef(0.03f, sampleRate)
    private val popLp = OnePoleLowPass(sampleRate, 260f)
    private val clickProb = 9f / sampleRate
    private val popProb = 0.25f / sampleRate

    override fun process() {
        if (rng.nextFloat() < clickProb) {
            val a = rng.nextFloat()
            val amp = a * a * a * 0.9f + 0.05f
            if (rng.nextFloat() < 0.5f) clickL += amp * (if (rng.chance(0.5f)) 1f else -1f)
            else clickR += amp * (if (rng.chance(0.5f)) 1f else -1f)
        }
        if (rng.nextFloat() < popProb) pop += 0.8f
        clickL *= clickDecay
        clickR *= clickDecay
        pop *= popDecay
        // Flush tiny values: denormal floats are very slow on some CPUs.
        if (abs(clickL) < 1e-7f) clickL = 0f
        if (abs(clickR) < 1e-7f) clickR = 0f
        if (pop < 1e-7f) pop = 0f
        val p = popLp.process(pop * rng.bipolar())
        val hl = hissLp.process(hissL.next()) * 0.05f
        val hr = hissLpR.process(hissR.next()) * 0.05f
        l = hl + clickL * 0.35f + p
        r = hr + clickR * 0.35f + p
    }
}

/** Rain: filtered stereo noise with a slow swell and scattered drops. */
class RainVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val rng = FastRandom(seed xor 0xBA1)
    private val nl = PinkNoise(seed + 21)
    private val nr = PinkNoise(seed + 22)
    private val fl = Svf(sampleRate, 2400f, 0.5f)
    private val fr = Svf(sampleRate, 2600f, 0.5f)
    private val bodyL = Svf(sampleRate, 420f, 0.6f)
    private val bodyR = Svf(sampleRate, 400f, 0.6f)
    private var swell = 0.7f
    private var swellTarget = 0.7f
    private var counter = 0
    private val swellCoef = 1f / (sampleRate * 1.5f)

    // Up to four ringing drops.
    private val dropPhase = FloatArray(4)
    private val dropInc = FloatArray(4)
    private val dropAmp = FloatArray(4)
    private val dropPan = FloatArray(4)
    private val dropDecay = decayCoef(0.035f, sampleRate)
    private val dropProb = 14f / sampleRate

    override fun process() {
        if (++counter >= 2048) {
            counter = 0
            if (rng.chance(0.08f)) swellTarget = 0.45f + rng.nextFloat() * 0.55f
        }
        swell += (swellTarget - swell) * swellCoef
        if (rng.nextFloat() < dropProb) {
            val slot = rng.nextInt(4)
            dropPhase[slot] = 0f
            dropInc[slot] = (1200f + rng.nextFloat() * 3200f) / sampleRate
            val a = rng.nextFloat()
            dropAmp[slot] = 0.05f + a * a * 0.22f
            dropPan[slot] = rng.nextFloat()
        }
        var dl = 0f
        var dr = 0f
        for (i in 0 until 4) {
            val amp = dropAmp[i]
            if (amp < 1e-4f) continue
            dropPhase[i] = wrapPhase(dropPhase[i] + dropInc[i])
            dropInc[i] *= 0.99993f // slight downward chirp
            val s = SineTable.sin(dropPhase[i]) * amp
            dl += s * (1f - dropPan[i])
            dr += s * dropPan[i]
            dropAmp[i] = amp * dropDecay
        }
        val wl = nl.next()
        val wr = nr.next()
        fl.process(wl); fr.process(wr)
        bodyL.process(wl); bodyR.process(wr)
        l = (fl.band * 0.9f + bodyL.band * 0.35f) * swell + dl
        r = (fr.band * 0.9f + bodyR.band * 0.35f) * swell + dr
    }
}

/** Café: two murmuring "talkers" (band noise with syllable-rate envelopes) and the odd clink. */
class CafeVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val rng = FastRandom(seed xor 0xCAFE)
    private val n1 = PinkNoise(seed + 31)
    private val n2 = PinkNoise(seed + 32)
    private val f1 = Svf(sampleRate, 520f, 1.4f)
    private val f2 = Svf(sampleRate, 680f, 1.4f)
    private val f1b = Svf(sampleRate, 1400f, 2f)
    private val f2b = Svf(sampleRate, 1700f, 2f)
    private var env1 = 0f
    private var env2 = 0f
    private var target1 = 0.5f
    private var target2 = 0.5f
    private var hold1 = 0
    private var hold2 = 0
    private val envCoef = 1f / (sampleRate * 0.04f)
    private var counter = 0
    private val room = Svf(sampleRate, 300f, 0.5f)

    // Clinks: inharmonic partials of a cup / spoon.
    private val clinkPhase = FloatArray(3)
    private val clinkInc = FloatArray(3)
    private var clinkAmp = 0f
    private var clinkPan = 0.5f
    private val clinkDecay = decayCoef(0.35f, sampleRate)
    private val clinkProb = 0.18f / sampleRate

    override fun process() {
        if (--hold1 <= 0) {
            hold1 = (sampleRate * (0.08f + rng.nextFloat() * 0.22f)).toInt()
            target1 = if (rng.chance(0.25f)) 0.05f else 0.3f + rng.nextFloat() * 0.7f
        }
        if (--hold2 <= 0) {
            hold2 = (sampleRate * (0.09f + rng.nextFloat() * 0.25f)).toInt()
            target2 = if (rng.chance(0.3f)) 0.05f else 0.3f + rng.nextFloat() * 0.7f
        }
        env1 += (target1 - env1) * envCoef
        env2 += (target2 - env2) * envCoef
        if (++counter >= 64) {
            counter = 0
            // Formant-ish drift so the murmur doesn't sound like plain noise.
            f1.set(450f + env1 * 300f, 1.4f)
            f2.set(560f + env2 * 320f, 1.4f)
        }
        if (rng.nextFloat() < clinkProb) {
            val base = 2300f + rng.nextFloat() * 1600f
            clinkInc[0] = base / sampleRate
            clinkInc[1] = base * 1.73f / sampleRate
            clinkInc[2] = base * 2.41f / sampleRate
            clinkAmp = 0.05f + rng.nextFloat() * 0.06f
            clinkPan = rng.nextFloat()
        }
        var clink = 0f
        if (clinkAmp > 1e-4f) {
            for (i in 0 until 3) {
                clinkPhase[i] = wrapPhase(clinkPhase[i] + clinkInc[i])
                clink += SineTable.sin(clinkPhase[i]) * (if (i == 0) 1f else 0.5f / i)
            }
            clink *= clinkAmp
            clinkAmp *= clinkDecay
        }
        val a = n1.next()
        val b = n2.next()
        f1.process(a); f1b.process(a)
        f2.process(b); f2b.process(b)
        room.process((a + b) * 0.5f)
        val t1 = (f1.band + f1b.band * 0.35f) * env1
        val t2 = (f2.band + f2b.band * 0.35f) * env2
        val bed = room.low * 0.5f
        l = (t1 * 0.75f + t2 * 0.35f) * 0.9f + bed + clink * (1f - clinkPan)
        r = (t1 * 0.35f + t2 * 0.75f) * 0.9f + bed + clink * clinkPan
    }
}

/** Fireplace: low rumbling roar with flicker plus pops and crackles. */
class FireplaceVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val rng = FastRandom(seed xor 0xF12E)
    private var brownL = 0f
    private var brownR = 0f
    private val lpL = OnePoleLowPass(sampleRate, 380f)
    private val lpR = OnePoleLowPass(sampleRate, 420f)
    private var flicker = 0.6f
    private var flickerTarget = 0.6f
    private var hold = 0
    private val flickerCoef = 1f / (sampleRate * 0.12f)
    private val crackleF = Svf(sampleRate, 3200f, 1.2f)
    private var crack = 0f
    private var crackPan = 0.5f
    private val crackDecay = decayCoef(0.006f, sampleRate)
    private var burst = 0
    private val crackProb = 1.6f / sampleRate
    private val burstProb = 40f / sampleRate

    override fun process() {
        if (--hold <= 0) {
            hold = (sampleRate * (0.05f + rng.nextFloat() * 0.3f)).toInt()
            flickerTarget = 0.35f + rng.nextFloat() * 0.65f
        }
        flicker += (flickerTarget - flicker) * flickerCoef
        brownL = (brownL + rng.bipolar() * 0.04f) * 0.995f
        brownR = (brownR + rng.bipolar() * 0.04f) * 0.995f
        val roarL = lpL.process(brownL) * 2.2f * flicker
        val roarR = lpR.process(brownR) * 2.2f * flicker
        if (burst <= 0) {
            if (rng.nextFloat() < crackProb) burst = 1 + rng.nextInt(5)
        } else if (rng.nextFloat() < burstProb) {
            crack = 0.15f + rng.nextFloat() * 0.6f
            crackPan = rng.nextFloat()
            burst--
        }
        crack *= crackDecay
        if (crack < 1e-7f) crack = 0f
        crackleF.process(rng.bipolar() * crack + 1e-9f)
        val c = crackleF.band * 1.6f
        l = roarL + c * (1f - crackPan)
        r = roarR + c * crackPan
    }
}

/** Waves: noise through a filter that opens and closes with each swell. */
class WavesVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val rng = FastRandom(seed xor 0x0CEA)
    private val nl = PinkNoise(seed + 41)
    private val nr = PinkNoise(seed + 42)
    private val fl = Svf(sampleRate, 400f, 0.6f)
    private val fr = Svf(sampleRate, 400f, 0.6f)
    private var phase = 0f
    private var period = 9f
    private var phaseR = 0.08f
    private var counter = 0
    private var envL = 0f
    private var envR = 0f

    private fun shape(p: Float): Float {
        // Slow rise (40 %) then a longer fall.
        return if (p < 0.4f) {
            val t = p / 0.4f
            t * t * (3f - 2f * t)
        } else {
            val t = (p - 0.4f) / 0.6f
            val f = 1f - t
            f * f
        }
    }

    override fun process() {
        if (++counter >= 32) {
            counter = 0
            val inc = 32f / (period * sampleRate)
            phase += inc
            phaseR += inc
            if (phase >= 1f) {
                phase -= 1f
                period = 7f + rng.nextFloat() * 6f
            }
            if (phaseR >= 1f) phaseR -= 1f
            envL = shape(phase)
            envR = shape(phaseR)
            fl.set(220f + envL * envL * 2600f, 0.6f)
            fr.set(220f + envR * envR * 2600f, 0.6f)
        }
        fl.process(nl.next())
        fr.process(nr.next())
        l = fl.low * (0.25f + envL * 0.95f)
        r = fr.low * (0.25f + envR * 0.95f)
    }
}

/** Keeps a level meter: fast attack, slow release, block rate. */
class LevelFollower(private val releasePerBlock: Float = 0.85f) {
    var level = 0f
        private set

    fun push(peak: Float) {
        level = max(peak, level * releasePerBlock)
    }

    fun reset() { level = 0f }
}
