package com.nobodybuilds.listens.data.lofi

import com.nobodybuilds.listens.data.lofi.dsp.ChordQuality
import com.nobodybuilds.listens.data.lofi.dsp.ChordStep
import com.nobodybuilds.listens.data.lofi.dsp.Progressions
import com.nobodybuilds.listens.data.lofi.dsp.VoiceLeading
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LofiMusicTheoryTest {

    @Test
    fun `ii-V-I in C has the expected pitch classes`() {
        val key = 0
        val (ii, v, i) = Progressions.iiVII.chords
        assertEquals(setOf(2, 5, 9, 0), VoiceLeading.pitchClasses(key, ii, ChordColour.Simple, false).toSet()) // Dm7
        assertEquals(setOf(7, 11, 2, 5), VoiceLeading.pitchClasses(key, v, ChordColour.Simple, false).toSet()) // G7
        assertEquals(setOf(0, 4, 7, 11), VoiceLeading.pitchClasses(key, i, ChordColour.Simple, false).toSet()) // Cmaj7
    }

    @Test
    fun `jazzy colour adds the ninth and rootless voicing drops the root`() {
        val dm9 = VoiceLeading.pitchClasses(0, ChordStep(2, ChordQuality.Min7), ChordColour.Jazzy, dropRoot = false)
        assertTrue(4 in dm9, "Dm9 should contain E (the ninth)")
        val rootless = VoiceLeading.pitchClasses(0, ChordStep(2, ChordQuality.Min7), ChordColour.Jazzy, dropRoot = true)
        assertTrue(2 !in rootless, "rootless Dm9 should leave D to the bass")
        assertEquals(4, rootless.size)
    }

    @Test
    fun `voicing keeps pitch classes and range`() {
        for (prog in Progressions.forMood(LofiMood.Jazzy) + Progressions.forMood(LofiMood.Rainy)) {
            var prev: IntArray? = null
            for (colour in ChordColour.entries) for (key in 0 until 12) for (step in prog.chords) {
                val pcs = VoiceLeading.pitchClasses(key, step, colour, dropRoot = colour != ChordColour.Simple)
                val voicing = VoiceLeading.voice(pcs, prev)
                assertEquals(pcs.toSet(), voicing.map { ((it % 12) + 12) % 12 }.toSet())
                assertTrue(voicing.first() >= VoiceLeading.DEFAULT_LOW, "too low: ${voicing.toList()}")
                assertTrue(voicing.last() <= VoiceLeading.DEFAULT_HIGH + 2, "too high: ${voicing.toList()}")
                assertArrayEquals(voicing.sortedArray(), voicing)
                prev = voicing
            }
        }
    }

    @Test
    fun `voice leading moves less than root position chords`() {
        val key = 5
        var prevLed: IntArray? = null
        var prevRoot: IntArray? = null
        var led = 0f
        var naive = 0f
        repeat(4) {
            for (step in Progressions.iiVIvi.chords) {
                val pcs = VoiceLeading.pitchClasses(key, step, ChordColour.Jazzy, dropRoot = true)
                val v = VoiceLeading.voice(pcs, prevLed)
                // Root position: stacked from the chord's first tone at/above the low end.
                val rootPos = VoiceLeading.voice(intArrayOf(pcs[0]), null).let { base ->
                    var n = base[0]
                    pcs.map { pc -> while (((n % 12) + 12) % 12 != pc) n++; n }.toIntArray()
                }
                if (prevLed != null) led += VoiceLeading.movement(prevLed, v)
                if (prevRoot != null) naive += VoiceLeading.movement(prevRoot, rootPos)
                prevLed = v
                prevRoot = rootPos
            }
        }
        assertTrue(led < naive, "voice-led movement $led should be below root-position movement $naive")
        assertTrue(led / 15f <= 4f, "average movement per change should be small, was ${led / 15f}")
    }

    @Test
    fun `G7 to Cmaj7 resolves by step`() {
        val g7 = VoiceLeading.voice(VoiceLeading.pitchClasses(0, ChordStep(7, ChordQuality.Dom7), ChordColour.Simple, false), null)
        val c = VoiceLeading.voice(VoiceLeading.pitchClasses(0, ChordStep(0, ChordQuality.Maj7), ChordColour.Simple, false), g7)
        assertTrue(VoiceLeading.movement(g7, c) <= 3f, "G7 ${g7.toList()} -> Cmaj7 ${c.toList()}")
    }

    @ParameterizedTest
    @EnumSource(LofiMood::class)
    fun `every mood has progressions and a pentatonic scale`(mood: LofiMood) {
        assertTrue(Progressions.forMood(mood).size >= 2)
        Progressions.forMood(mood).forEach { assertEquals(4, it.chords.size) }
        assertEquals(5, Progressions.melodyScale(mood).size)
    }

    @Test
    fun `settings normalise out-of-range values`() {
        val s = LofiSettings(tempoBpm = 200, key = -1, swing = 2f, masterVolume = -1f).normalized()
        assertEquals(LofiSettings.MAX_BPM, s.tempoBpm)
        assertEquals(11, s.key)
        assertEquals(LofiSettings.MAX_SWING, s.swing)
        assertEquals(0f, s.masterVolume)
    }

    @Test
    fun `mood preset keeps seed and user layer choices`() {
        val s = LofiSettings(seed = 99, drumsOn = false, melodyVolume = 0.2f).withMood(LofiMood.Jazzy)
        assertEquals(LofiMood.Jazzy, s.mood)
        assertEquals(99, s.seed)
        assertEquals(false, s.drumsOn)
        assertEquals(0.2f, s.melodyVolume)
        assertEquals(LofiSettings.forMood(LofiMood.Jazzy).tempoBpm, s.tempoBpm)
    }
}
