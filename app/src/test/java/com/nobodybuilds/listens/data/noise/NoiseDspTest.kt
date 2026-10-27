package com.nobodybuilds.listens.data.noise

import com.nobodybuilds.listens.data.lofi.dsp.StereoVoice
import com.nobodybuilds.listens.data.noise.dsp.BrownNoise
import com.nobodybuilds.listens.data.noise.dsp.NoiseMixer
import com.nobodybuilds.listens.data.noise.dsp.VossPink
import com.nobodybuilds.listens.data.noise.dsp.createNoiseVoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class NoiseDspTest {
    private val sr = 44_100

    // ---------------------------------------------------------------- spectrum helpers

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val vr = re[b] * cr - im[b] * ci
                    val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Average power density (dB) in octave bands centred on [centres], from Welch-averaged FFTs. */
    private fun octaveBands(samples: FloatArray, centres: List<Double>): List<Double> {
        val n = 8192
        val psd = DoubleArray(n / 2)
        var segments = 0
        var start = 0
        while (start + n <= samples.size) {
            val re = DoubleArray(n) { samples[start + it] * (0.5 - 0.5 * cos(2 * PI * it / n)) }
            val im = DoubleArray(n)
            fft(re, im)
            for (b in 0 until n / 2) psd[b] += re[b] * re[b] + im[b] * im[b]
            segments++
            start += n
        }
        return centres.map { f ->
            val lo = (f / sqrt(2.0) / sr * n).toInt().coerceAtLeast(1)
            val hi = (f * sqrt(2.0) / sr * n).toInt().coerceAtMost(n / 2 - 1)
            var p = 0.0
            for (b in lo..hi) p += psd[b]
            10 * log10(p / (hi - lo + 1) / segments)
        }
    }

    private fun render(next: () -> Float, seconds: Int = 8): FloatArray {
        repeat(sr) { next() } // settle
        return FloatArray(sr * seconds) { next() }
    }

    /** Slope in dB per octave from 250 Hz to 8 kHz (5 octaves). */
    private fun slope(samples: FloatArray): Double {
        val bands = octaveBands(samples, listOf(250.0, 8000.0))
        return (bands[1] - bands[0]) / 5.0
    }

    // ---------------------------------------------------------------- noise colours

    @Test
    fun `pink noise falls about 3 dB per octave`() {
        val pink = VossPink(3L)
        val s = slope(render({ pink.next() }))
        assertTrue(s in -4.0..-2.0, "pink slope was $s dB/oct")
    }

    @Test
    fun `brown noise falls about 6 dB per octave`() {
        val brown = BrownNoise(3L)
        val s = slope(render({ brown.next() }))
        assertTrue(s in -7.0..-5.0, "brown slope was $s dB/oct")
    }

    @Test
    fun `white noise is flat and blue and violet rise`() {
        fun voiceSlope(kind: NoiseKind): Double {
            val v = createNoiseVoice(kind, sr, 9L)
            return slope(render({ v.process(); v.l }))
        }
        val white = voiceSlope(NoiseKind.White)
        val blue = voiceSlope(NoiseKind.Blue)
        val violet = voiceSlope(NoiseKind.Violet)
        assertTrue(abs(white) < 0.7, "white slope was $white")
        assertTrue(blue in 1.5..4.0, "blue slope was $blue")
        assertTrue(violet in 4.5..7.0, "violet slope was $violet")
    }

    @Test
    fun `grey noise has more low end than pink and a dip in the upper mids`() {
        val grey = createNoiseVoice(NoiseKind.Grey, sr, 4L)
        val pink = createNoiseVoice(NoiseKind.Pink, sr, 4L)
        val centres = listOf(63.0, 1000.0, 4000.0)
        val g = octaveBands(render({ grey.process(); grey.l }), centres)
        val p = octaveBands(render({ pink.process(); pink.l }), centres)
        // Relative to 1 kHz: lows lifted, ~4 kHz lowered compared with plain pink.
        assertTrue((g[0] - g[1]) - (p[0] - p[1]) > 4.0, "grey low lift too small: $g vs $p")
        assertTrue((g[2] - g[1]) - (p[2] - p[1]) < -1.5, "grey upper-mid dip missing: $g vs $p")
    }

    // ---------------------------------------------------------------- every sound

    @ParameterizedTest
    @EnumSource(NoiseKind::class)
    fun `every sound is finite, bounded and not silent`(kind: NoiseKind) {
        val v: StereoVoice = createNoiseVoice(kind, sr, 21L)
        var sum = 0.0
        var peak = 0f
        val n = sr * 6
        for (i in 0 until n) {
            v.process()
            assertFalse(v.l.isNaN() || v.r.isNaN() || v.l.isInfinite() || v.r.isInfinite(), "$kind produced NaN/Inf at $i")
            sum += v.l * v.l
            peak = max(peak, max(abs(v.l), abs(v.r)))
        }
        val rms = sqrt(sum / n) * kind.trim
        assertTrue(rms > 0.03, "$kind is too quiet: rms $rms")
        assertTrue(rms < 0.4, "$kind is too loud: rms $rms")
        assertTrue(peak < 3f, "$kind peak $peak")
    }

    // ---------------------------------------------------------------- mixer

    @Test
    fun `mixer output stays within -1 to 1 and has no NaN at full volume`() {
        val m = NoiseMixer(sr)
        m.setLayers(listOf(NoiseKind.Brown to 1f, NoiseKind.Train to 1f, NoiseKind.Airplane to 1f))
        m.setMaster(1f)
        m.setTone(1f)
        m.setSwell(true)
        m.setBinaural(200f, 14f)
        m.fadeIn(10f)
        val l = FloatArray(512)
        val r = FloatArray(512)
        var peak = 0f
        repeat(800) {
            m.render(l, r, 512)
            for (i in 0 until 512) {
                assertFalse(l[i].isNaN() || r[i].isNaN())
                peak = max(peak, max(abs(l[i]), abs(r[i])))
            }
        }
        assertTrue(peak <= 1f, "peak $peak")
        assertTrue(peak > 0.1f, "mix should be audible, peak $peak")
    }

    @Test
    fun `mixer keeps at most three layers and fades out to a stop`() {
        val m = NoiseMixer(sr)
        m.setLayers(NoiseKind.entries.map { it to 0.8f })
        m.setMaster(1f)
        m.fadeIn(10f)
        val l = FloatArray(512)
        val r = FloatArray(512)
        repeat(50) { assertTrue(m.render(l, r, 512)) }
        m.fadeOut(200f)
        var blocks = 0
        while (m.render(l, r, 512)) {
            blocks++
            assertTrue(blocks < 100, "fade-out should finish")
        }
        assertEquals(0f, l.last(), 1e-6f)
    }

    @Test
    fun `changing layers while playing never jumps`() {
        val m = NoiseMixer(sr)
        m.setLayers(listOf(NoiseKind.Pink to 0.7f))
        m.setMaster(0.8f)
        m.fadeIn(10f)
        val l = FloatArray(256)
        val r = FloatArray(256)
        repeat(40) { m.render(l, r, 256) }
        var prev = l.last()
        var maxStep = 0f
        m.setLayers(listOf(NoiseKind.Brown to 0.7f, NoiseKind.Rain to 0.5f))
        repeat(200) {
            m.render(l, r, 256)
            for (x in l) { maxStep = max(maxStep, abs(x - prev)); prev = x }
        }
        // Noise itself moves sample to sample; a hard switch would show a much larger step.
        assertTrue(maxStep < 0.6f, "max step $maxStep")
    }

    @Test
    fun `binaural tones differ between ears by the beat frequency`() {
        val m = NoiseMixer(sr)
        m.setLayers(emptyList())
        m.setMaster(1f)
        m.setBinaural(200f, 10f)
        m.fadeIn(10f)
        val n = sr * 4
        val left = FloatArray(n)
        val right = FloatArray(n)
        val l = FloatArray(441)
        val r = FloatArray(441)
        var k = 0
        while (k < n) {
            m.render(l, r, 441)
            System.arraycopy(l, 0, left, k, 441)
            System.arraycopy(r, 0, right, k, 441)
            k += 441
        }
        fun zeroCrossings(x: FloatArray, from: Int): Int {
            var c = 0
            for (i in from + 1 until x.size) if (x[i - 1] <= 0f && x[i] > 0f) c++
            return c
        }
        val from = sr * 2 // after the level ramp
        val seconds = (n - from).toDouble() / sr
        val fl = zeroCrossings(left, from) / seconds
        val fr = zeroCrossings(right, from) / seconds
        assertEquals(195.0, fl, 2.0)
        assertEquals(205.0, fr, 2.0)
    }

    @Test
    fun `presets are valid and the defaults exist`() {
        val names = NoiseDefaults.all.map { it.name }
        assertEquals(listOf("Deep work", "Calm desk", "Sleep"), names)
        assertEquals(listOf(NoiseKind.Brown, NoiseKind.Rain), NoiseDefaults.DeepWork.settings.layers.map { it.kind })
        assertEquals(listOf(NoiseKind.Pink, NoiseKind.Cafe), NoiseDefaults.CalmDesk.settings.layers.map { it.kind })
        assertEquals(listOf(NoiseKind.Brown, NoiseKind.Ocean), NoiseDefaults.Sleep.settings.layers.map { it.kind })
        val tooMany = NoiseSettings(layers = NoiseKind.entries.map { NoiseLayer(it, 2f) }).normalized()
        assertEquals(NoiseMixer.MAX_LAYERS, tooMany.layers.size)
        assertTrue(tooMany.layers.all { it.volume <= 1f })
    }
}
