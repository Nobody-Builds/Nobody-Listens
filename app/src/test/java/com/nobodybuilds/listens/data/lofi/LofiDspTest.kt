package com.nobodybuilds.listens.data.lofi

import com.nobodybuilds.listens.data.lofi.dsp.AmbienceMixer
import com.nobodybuilds.listens.data.lofi.dsp.ChimeSynth
import com.nobodybuilds.listens.data.lofi.dsp.FastRandom
import com.nobodybuilds.listens.data.lofi.dsp.LinearRamp
import com.nobodybuilds.listens.data.lofi.dsp.LofiSynth
import com.nobodybuilds.listens.data.lofi.dsp.SineTable
import com.nobodybuilds.listens.data.lofi.dsp.SmoothedValue
import com.nobodybuilds.listens.data.lofi.dsp.softClip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sin

class LofiDspTest {
    private val sr = 44_100

    @Test
    fun `smoothed value approaches target without overshoot or jumps`() {
        val v = SmoothedValue(0f, 50f, sr)
        v.target = 1f
        var prev = 0f
        var maxStep = 0f
        repeat(sr / 2) {
            val x = v.next()
            assertTrue(x >= prev && x <= 1f)
            maxStep = maxOf(maxStep, x - prev)
            prev = x
        }
        assertTrue(prev > 0.999f, "should settle within 10 time constants, was $prev")
        assertTrue(maxStep < 0.001f, "per-sample step must be tiny (no clicks), was $maxStep")
    }

    @Test
    fun `smoothed value block advance matches per-sample stepping`() {
        val a = SmoothedValue(0f, 20f, sr).apply { target = 1f }
        val b = SmoothedValue(0f, 20f, sr).apply { target = 1f }
        repeat(64) { a.next() }
        b.advance(64)
        assertEquals(a.value, b.value, 1e-4f)
    }

    @Test
    fun `linear ramp reaches target exactly in the given time`() {
        val r = LinearRamp(1f, sr)
        r.rampTo(0f, 100f)
        var n = 0
        while (!r.isDone && n < sr) { r.next(); n++ }
        assertEquals(0f, r.value)
        assertTrue(abs(n - sr / 10) <= 2, "took $n samples")
    }

    @Test
    fun `sine table matches sin and handles negative phase`() {
        for (i in -200..200) {
            val phase = i / 37f
            assertEquals(sin(phase * 2 * Math.PI).toFloat(), SineTable.sin(phase), 2e-3f)
        }
    }

    @Test
    fun `soft clip is bounded and near-linear for small signals`() {
        assertEquals(1f, softClip(10f))
        assertEquals(-1f, softClip(-10f))
        assertEquals(0.1f, softClip(0.1f), 0.005f)
    }

    @Test
    fun `random is deterministic per seed`() {
        val a = FastRandom(42); val b = FastRandom(42); val c = FastRandom(43)
        val sa = List(10) { a.nextInt() }
        assertEquals(sa, List(10) { b.nextInt() })
        assertNotEquals(sa, List(10) { c.nextInt() })
        repeat(1000) { val f = a.nextFloat(); assertTrue(f >= 0f && f < 1f) }
    }

    private fun renderHash(settings: LofiSettings, blocks: Int): Pair<Int, Float> {
        val synth = LofiSynth(sr, settings)
        synth.fadeIn(10f)
        val l = FloatArray(512); val r = FloatArray(512)
        var hash = 0
        var peak = 0f
        repeat(blocks) {
            synth.render(l, r, 512)
            for (i in 0 until 512) {
                assertFalse(l[i].isNaN() || r[i].isNaN())
                peak = maxOf(peak, abs(l[i]), abs(r[i]))
            }
            hash = 31 * hash + l.contentHashCode()
        }
        return hash to peak
    }

    @Test
    fun `generator is deterministic from the seed and never clips`() {
        val s = LofiSettings.forMood(LofiMood.Jazzy, seed = 7L).copy(rain = 1f, cafe = 1f, crackle = 1f, masterVolume = 1f)
        val (h1, peak) = renderHash(s, 600)
        val (h2, _) = renderHash(s, 600)
        val (h3, _) = renderHash(s.copy(seed = 8L), 600)
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
        assertTrue(peak <= 1f, "peak $peak")
        assertTrue(peak > 0.05f, "should be audible, peak $peak")
    }

    @Test
    fun `fade out finishes the source`() {
        val synth = LofiSynth(sr, LofiSettings())
        synth.fadeIn(10f)
        val l = FloatArray(512); val r = FloatArray(512)
        repeat(50) { assertTrue(synth.render(l, r, 512)) }
        synth.fadeOut(100f)
        var blocks = 0
        while (synth.render(l, r, 512)) { blocks++; assertTrue(blocks < 100) }
        assertTrue(abs(l[511]) < 1e-3f && abs(r[511]) < 1e-3f)
    }

    @Test
    fun `settings changes while playing do not click`() {
        val synth = LofiSynth(sr, LofiSettings(drumsOn = false, melodyOn = false, crackle = 0f))
        synth.fadeIn(10f)
        val l = FloatArray(512); val r = FloatArray(512)
        repeat(200) { synth.render(l, r, 512) }
        var last = l[511]
        synth.submit(LofiSettings(drumsOn = false, melodyOn = false, crackle = 0f, masterVolume = 0.1f))
        synth.render(l, r, 512)
        // The first sample after a big volume drop must stay continuous.
        assertTrue(abs(l[0] - last) < 0.05f, "jump ${abs(l[0] - last)}")
        last = l[511]
    }

    @Test
    fun `ambience layers render bounded audio and silence at zero`() {
        val m = AmbienceMixer(sr)
        m.fadeIn(10f)
        val l = FloatArray(512); val r = FloatArray(512)
        repeat(20) { m.render(l, r, 512) }
        assertTrue(l.all { abs(it) < 1e-6f })
        AmbienceKind.entries.forEach { m.setVolume(it, 1f) }
        var peak = 0f
        repeat(400) { m.render(l, r, 512); l.forEach { peak = maxOf(peak, abs(it)) } }
        assertTrue(peak in 0.01f..1f, "peak $peak")
    }

    @Test
    fun `chime is short soft and bounded`() {
        val c = ChimeSynth.render(sr)
        val peak = c.maxOf { abs(it) }
        assertTrue(peak in 0.1f..0.9f)
        assertTrue(abs(c.last()) < 1e-3f)
    }
}
