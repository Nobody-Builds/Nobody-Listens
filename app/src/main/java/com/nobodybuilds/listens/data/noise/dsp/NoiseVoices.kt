package com.nobodybuilds.listens.data.noise.dsp

import com.nobodybuilds.listens.data.lofi.dsp.CafeVoice
import com.nobodybuilds.listens.data.lofi.dsp.FastRandom
import com.nobodybuilds.listens.data.lofi.dsp.FireplaceVoice
import com.nobodybuilds.listens.data.lofi.dsp.OnePoleLowPass
import com.nobodybuilds.listens.data.lofi.dsp.RainVoice
import com.nobodybuilds.listens.data.lofi.dsp.SineTable
import com.nobodybuilds.listens.data.lofi.dsp.StereoVoice
import com.nobodybuilds.listens.data.lofi.dsp.Svf
import com.nobodybuilds.listens.data.lofi.dsp.WavesVoice
import com.nobodybuilds.listens.data.lofi.dsp.decayCoef
import com.nobodybuilds.listens.data.lofi.dsp.wrapPhase
import com.nobodybuilds.listens.data.noise.NoiseKind
import kotlin.math.abs

// Noise colours and everyday sounds for the noise generator. Plain Kotlin (no Android), so they
// can be tested on the JVM. Like the lofi ambience voices, process() writes one stereo sample into
// l/r, never allocates and only recomputes filter coefficients every 32+ samples.
//
// Rain, ocean waves, fireplace and café reuse the lofi ambience voices.

/** Creates the voice for [kind]. Allocates: call off the audio thread. */
fun createNoiseVoice(kind: NoiseKind, sampleRate: Int, seed: Long): StereoVoice = when (kind) {
    NoiseKind.White -> WhiteVoice(seed)
    NoiseKind.Pink -> PinkVoice(seed)
    NoiseKind.Brown -> BrownVoice(seed)
    NoiseKind.Grey -> GreyVoice(sampleRate, seed)
    NoiseKind.Blue -> BlueVoice(seed)
    NoiseKind.Violet -> VioletVoice(seed)
    NoiseKind.Fan -> FanVoice(sampleRate, seed)
    NoiseKind.Rain -> ScaledVoice(RainVoice(sampleRate, seed), 2.2f)
    NoiseKind.Ocean -> ScaledVoice(WavesVoice(sampleRate, seed), 0.9f)
    NoiseKind.Stream -> StreamVoice(sampleRate, seed)
    NoiseKind.Wind -> WindVoice(sampleRate, seed)
    NoiseKind.Fireplace -> ScaledVoice(FireplaceVoice(sampleRate, seed), 0.55f)
    NoiseKind.Cafe -> ScaledVoice(CafeVoice(sampleRate, seed), 1.5f)
    NoiseKind.Train -> TrainVoice(sampleRate, seed)
    NoiseKind.Airplane -> AirplaneVoice(sampleRate, seed)
}

/** Wraps a lofi ambience voice with a level trim. */
class ScaledVoice(private val inner: StereoVoice, private val gain: Float) : StereoVoice() {
    override fun process() {
        inner.process()
        l = inner.l * gain
        r = inner.r * gain
    }
}

// ---------------------------------------------------------------- noise colours (mono sources)

/**
 * Voss–McCartney pink noise: 16 white "rows", row k refreshed every 2^(k+1) samples, plus a white
 * row every sample. Falls ~3 dB per octave from about 0.3 Hz to Nyquist. Output roughly ±1, RMS ~0.14.
 */
class VossPink(seed: Long) {
    private val rng = FastRandom(seed)
    private val rows = FloatArray(ROWS)
    private var sum = 0f
    private var counter = 0

    init {
        for (i in 0 until ROWS) { rows[i] = rng.bipolar(); sum += rows[i] }
    }

    fun next(): Float {
        counter = (counter + 1) and MASK
        if (counter != 0) {
            val k = Integer.numberOfTrailingZeros(counter)
            sum -= rows[k]
            val v = rng.bipolar()
            rows[k] = v
            sum += v
        }
        return (sum + rng.bipolar()) * (1f / (ROWS + 1))
    }

    private companion object {
        const val ROWS = 16
        const val MASK = (1 shl ROWS) - 1
    }
}

/** Brown (red) noise: leaky integrated white noise, ~6 dB per octave down above ~30 Hz. RMS ~0.17. */
class BrownNoise(seed: Long) {
    private val rng = FastRandom(seed)
    private var y = 0f

    fun next(): Float {
        y = y * LEAK + rng.bipolar() * GAIN
        if (abs(y) < 1e-20f) y = 0f
        return y
    }

    private companion object {
        const val LEAK = 0.996f
        const val GAIN = 0.026f
    }
}

class WhiteVoice(seed: Long) : StereoVoice() {
    private val a = FastRandom(seed)
    private val b = FastRandom(seed + 1)
    override fun process() {
        l = a.bipolar() * 0.3f
        r = b.bipolar() * 0.3f
    }
}

class PinkVoice(seed: Long) : StereoVoice() {
    private val a = VossPink(seed)
    private val b = VossPink(seed + 1)
    override fun process() {
        l = a.next() * 1.2f
        r = b.next() * 1.2f
    }
}

class BrownVoice(seed: Long) : StereoVoice() {
    private val a = BrownNoise(seed)
    private val b = BrownNoise(seed + 1)
    override fun process() {
        l = a.next()
        r = b.next()
    }
}

/**
 * Grey noise: pink noise shaped by a rough inverse equal-loudness curve, so it sounds about
 * equally loud at every pitch — lifted lows, a dip around 3–4 kHz, a little air on top.
 */
class GreyVoice(sampleRate: Int, seed: Long) : StereoVoice() {
    private val a = VossPink(seed)
    private val b = VossPink(seed + 1)
    private val lowL = Svf(sampleRate, 180f, 0.6f)
    private val lowR = Svf(sampleRate, 180f, 0.6f)
    private val dipL = Svf(sampleRate, 3500f, 0.7f)
    private val dipR = Svf(sampleRate, 3500f, 0.7f)
    private val airL = Svf(sampleRate, 10_000f, 0.7f)
    private val airR = Svf(sampleRate, 10_000f, 0.7f)

    private fun shape(x: Float, low: Svf, dip: Svf, air: Svf): Float {
        low.process(x); dip.process(x); air.process(x)
        // Band output peaks at Q (0.7): 0.7 / 0.7 = 1 → about -6 dB at 3.5 kHz with the 0.5 factor.
        return x + low.low * 2.2f - dip.band * (0.5f / 0.7f) + air.high * 0.5f
    }

    override fun process() {
        l = shape(a.next(), lowL, dipL, airL) * 0.8f
        r = shape(b.next(), lowR, dipR, airR) * 0.8f
    }
}

/** Blue noise: differentiated pink, rising ~3 dB per octave. */
class BlueVoice(seed: Long) : StereoVoice() {
    private val a = VossPink(seed)
    private val b = VossPink(seed + 1)
    private var pa = 0f
    private var pb = 0f
    override fun process() {
        val x = a.next(); val y = b.next()
        l = (x - pa) * 0.9f
        r = (y - pb) * 0.9f
        pa = x; pb = y
    }
}

/** Violet noise: differentiated white, rising ~6 dB per octave. */
class VioletVoice(seed: Long) : StereoVoice() {
    private val a = FastRandom(seed)
    private val b = FastRandom(seed + 1)
    private var pa = 0f
    private var pb = 0f
    override fun process() {
        val x = a.bipolar(); val y = b.bipolar()
        l = (x - pa) * 0.22f
        r = (y - pb) * 0.22f
        pa = x; pb = y
    }
}

// ---------------------------------------------------------------- everyday sounds

/** Fan: low-passed noise with a faint blade flutter, plus a soft motor hum. */
class FanVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val nl = VossPink(seed)
    private val nr = VossPink(seed + 1)
    private val bl = BrownNoise(seed + 2)
    private val br = BrownNoise(seed + 3)
    private val lpL = Svf(sampleRate, 1100f, 0.55f)
    private val lpR = Svf(sampleRate, 1000f, 0.55f)
    private var humPhase = 0f
    private var hum2Phase = 0f
    private var flutterPhase = 0f
    private val humInc = 98f / sampleRate
    private val hum2Inc = 196.4f / sampleRate
    private val flutterInc = 17.5f / sampleRate

    override fun process() {
        humPhase = wrapPhase(humPhase + humInc)
        hum2Phase = wrapPhase(hum2Phase + hum2Inc)
        flutterPhase = wrapPhase(flutterPhase + flutterInc)
        val flutter = 1f + SineTable.sin(flutterPhase) * 0.07f
        lpL.process(nl.next()); lpR.process(nr.next())
        val hum = SineTable.sin(humPhase) * 0.018f + SineTable.sin(hum2Phase) * 0.008f
        l = (lpL.low * 1.5f * flutter + bl.next() * 0.5f) + hum
        r = (lpR.low * 1.5f * flutter + br.next() * 0.5f) + hum
    }
}

/** Stream: babbling band noise with a jittering centre, a low body and scattered bubbles. */
class StreamVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val rng = FastRandom(seed xor 0x57AE)
    private val nl = VossPink(seed)
    private val nr = VossPink(seed + 1)
    private val bandL = Svf(sampleRate, 1800f, 1.1f)
    private val bandR = Svf(sampleRate, 2000f, 1.1f)
    private val bodyL = Svf(sampleRate, 320f, 0.6f)
    private val bodyR = Svf(sampleRate, 300f, 0.6f)
    private var cl = 1800f
    private var cr = 2000f
    private var tl = 1800f
    private var tr = 2000f
    private var counter = 0
    private var amp = 0.8f
    private var ampTarget = 0.8f

    private val bubblePhase = FloatArray(3)
    private val bubbleInc = FloatArray(3)
    private val bubbleAmp = FloatArray(3)
    private val bubblePan = FloatArray(3)
    private val bubbleDecay = decayCoef(0.03f, sampleRate)
    private val bubbleProb = 7f / sampleRate

    override fun process() {
        if (++counter >= 32) {
            counter = 0
            if (rng.chance(0.25f)) tl = 1100f + rng.nextFloat() * 2200f
            if (rng.chance(0.25f)) tr = 1100f + rng.nextFloat() * 2200f
            if (rng.chance(0.02f)) ampTarget = 0.6f + rng.nextFloat() * 0.4f
            cl += (tl - cl) * 0.18f
            cr += (tr - cr) * 0.18f
            amp += (ampTarget - amp) * 0.01f
            bandL.set(cl, 1.1f)
            bandR.set(cr, 1.1f)
        }
        if (rng.nextFloat() < bubbleProb) {
            val s = rng.nextInt(3)
            bubblePhase[s] = 0f
            bubbleInc[s] = (500f + rng.nextFloat() * 900f) / sampleRate
            bubbleAmp[s] = 0.02f + rng.nextFloat() * 0.05f
            bubblePan[s] = rng.nextFloat()
        }
        var bl = 0f
        var br = 0f
        for (i in 0 until 3) {
            val a = bubbleAmp[i]
            if (a < 1e-4f) continue
            bubblePhase[i] = wrapPhase(bubblePhase[i] + bubbleInc[i])
            bubbleInc[i] *= 1.00006f // a bubble's pitch rises as it closes
            val s = SineTable.sin(bubblePhase[i]) * a
            bl += s * (1f - bubblePan[i])
            br += s * bubblePan[i]
            bubbleAmp[i] = a * bubbleDecay
        }
        val xl = nl.next()
        val xr = nr.next()
        bandL.process(xl); bandR.process(xr)
        bodyL.process(xl); bodyR.process(xr)
        l = (bandL.band * 1.6f + bodyL.low * 0.5f) * amp + bl
        r = (bandR.band * 1.6f + bodyR.low * 0.5f) * amp + br
    }
}

/** Wind: noise through a resonant band-pass that sweeps slowly, with gusts. */
class WindVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val rng = FastRandom(seed xor 0x3191D)
    private val nl = VossPink(seed)
    private val nr = VossPink(seed + 1)
    private val bandL = Svf(sampleRate, 500f, 2.6f)
    private val bandR = Svf(sampleRate, 560f, 2.6f)
    private val rumbleL = OnePoleLowPass(sampleRate, 140f)
    private val rumbleR = OnePoleLowPass(sampleRate, 140f)
    private var centre = 500f
    private var centreTarget = 500f
    private var gust = 0.6f
    private var gustTarget = 0.6f
    private var hold = 0
    private var counter = 0
    private val glide = 32f / (sampleRate * 1.6f)

    override fun process() {
        if (--hold <= 0) {
            hold = (sampleRate * (1.5f + rng.nextFloat() * 3f)).toInt()
            centreTarget = 260f + rng.nextFloat() * 900f
            gustTarget = 0.3f + rng.nextFloat() * 0.7f
        }
        if (++counter >= 32) {
            counter = 0
            centre += (centreTarget - centre) * glide
            gust += (gustTarget - gust) * glide
            bandL.set(centre, 2.6f)
            bandR.set(centre * 1.12f, 2.6f)
        }
        val xl = nl.next()
        val xr = nr.next()
        bandL.process(xl); bandR.process(xr)
        val g = gust * gust
        l = bandL.band * 1.3f * g + rumbleL.process(xl) * 0.9f * (0.4f + g)
        r = bandR.band * 1.3f * g + rumbleR.process(xr) * 0.9f * (0.4f + g)
    }
}

/** Train cabin: low rumble, gentle sway and the clack-clack of rail joints. */
class TrainVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val rng = FastRandom(seed xor 0x72A1)
    private val bl = BrownNoise(seed)
    private val br = BrownNoise(seed + 1)
    private val pl = VossPink(seed + 2)
    private val rumbleL = Svf(sampleRate, 150f, 0.6f)
    private val rumbleR = Svf(sampleRate, 140f, 0.6f)
    private val bodyL = Svf(sampleRate, 420f, 0.8f)
    private val clackF = Svf(sampleRate, 1700f, 4f)
    private val thumpF = OnePoleLowPass(sampleRate, 90f)
    private var clack = 0f
    private val clackDecay = decayCoef(0.03f, sampleRate)
    private var thump = 0f
    private val thumpDecay = decayCoef(0.08f, sampleRate)
    private var swayPhase = 0f
    private val swayInc = 0.23f / sampleRate

    // Rail-joint pattern: two axles of one car, then two of the next, then a gap.
    private val pattern = floatArrayOf(0f, 0.11f, 0.58f, 0.69f)
    private var cycle = (sampleRate * 1.7f).toInt()
    private var pos = 0
    private var next = 0

    override fun process() {
        if (pos == (pattern[next] * sampleRate).toInt()) {
            val a = 0.5f + rng.nextFloat() * 0.5f
            clack += a
            thump += a
            next++
            if (next >= pattern.size) next = pattern.size - 1
        }
        if (++pos >= cycle) {
            pos = 0
            next = 0
            cycle = (sampleRate * (1.6f + rng.nextFloat() * 0.25f)).toInt()
        }
        clack *= clackDecay
        thump *= thumpDecay
        if (clack < 1e-7f) clack = 0f
        if (thump < 1e-7f) thump = 0f
        clackF.process(rng.bipolar() * clack)
        val t = thumpF.process(thump * rng.bipolar() * 0.5f + thump * 0.5f)
        swayPhase = wrapPhase(swayPhase + swayInc)
        val sway = 1f + SineTable.sin(swayPhase) * 0.12f
        val p = pl.next()
        bodyL.process(p)
        rumbleL.process(bl.next()); rumbleR.process(br.next())
        val c = clackF.band * 0.18f + t * 0.5f
        l = (rumbleL.low * 2.6f + bodyL.band * 0.35f) * sway + c
        r = (rumbleR.low * 2.6f + bodyL.band * 0.3f) * sway + c * 0.85f
    }
}

/** Airplane cabin: steady broadband roar, low-passed, with a faint engine drone. */
class AirplaneVoice(private val sampleRate: Int, seed: Long) : StereoVoice() {
    private val bl = BrownNoise(seed)
    private val br = BrownNoise(seed + 1)
    private val pl = VossPink(seed + 2)
    private val pr = VossPink(seed + 3)
    private val lpL = Svf(sampleRate, 650f, 0.6f)
    private val lpR = Svf(sampleRate, 620f, 0.6f)
    private val hissL = OnePoleLowPass(sampleRate, 3800f)
    private val hissR = OnePoleLowPass(sampleRate, 3800f)
    private var d1 = 0f
    private var d2 = 0f
    private val d1Inc = 92f / sampleRate
    private val d2Inc = 93.3f / sampleRate

    override fun process() {
        d1 = wrapPhase(d1 + d1Inc)
        d2 = wrapPhase(d2 + d2Inc)
        val drone = (SineTable.sin(d1) + SineTable.sin(d2)) * 0.008f
        val xl = pl.next()
        val xr = pr.next()
        lpL.process(xl + bl.next() * 1.5f)
        lpR.process(xr + br.next() * 1.5f)
        l = lpL.low * 1.3f + hissL.process(xl) * 0.25f + drone
        r = lpR.low * 1.3f + hissR.process(xr) * 0.25f + drone
    }
}
