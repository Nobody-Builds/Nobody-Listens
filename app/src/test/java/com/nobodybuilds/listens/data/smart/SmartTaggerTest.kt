package com.nobodybuilds.listens.data.smart

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SmartTaggerTest {

    private fun scores(vararg pairs: Pair<Int, Float>): FloatArray =
        FloatArray(SmartModel.NUM_CLASSES).also { arr -> pairs.forEach { (i, v) -> arr[i] = v } }

    @Test
    fun `genre tags take the top two above threshold`() {
        val s = scores(214 to 0.30f, 215 to 0.20f, 211 to 0.10f, 230 to 0.01f)
        assertThat(SmartTagger.genreTags(s)).containsExactly("Rock", "Metal").inOrder()
    }

    @Test
    fun `genre tags are empty when every score is below threshold`() {
        val s = scores(214 to 0.01f, 211 to 0.02f)
        assertThat(SmartTagger.genreTags(s)).isEmpty()
    }

    @Test
    fun `weak second genre is dropped`() {
        val s = scores(232 to 0.5f, 230 to 0.05f)
        assertThat(SmartTagger.genreTags(s)).containsExactly("Classical")
    }

    @Test
    fun `quiet tender classical is calm`() {
        val s = scores(SmartModel.TENDER to 0.15f, 232 to 0.3f)
        val tags = SmartTagger.moodTags(s, rms = 0.05f, bpm = 70f, zcr = 0.03f)
        assertThat(tags.first()).isEqualTo(SmartModel.MOOD_CALM)
    }

    @Test
    fun `loud fast rock is energetic`() {
        val s = scores(SmartModel.EXCITING to 0.2f, 214 to 0.4f)
        val tags = SmartTagger.moodTags(s, rms = 0.3f, bpm = 150f, zcr = 0.12f)
        assertThat(tags.first()).isEqualTo(SmartModel.MOOD_ENERGETIC)
    }

    @Test
    fun `dance music at club tempo is party`() {
        val s = scores(269 to 0.4f, 235 to 0.4f)
        val tags = SmartTagger.moodTags(s, rms = 0.25f, bpm = 124f, zcr = 0.08f)
        assertThat(tags).contains(SmartModel.MOOD_PARTY)
    }

    @Test
    fun `mood tags return one or two tags`() {
        val tags = SmartTagger.moodTags(scores(), rms = 0.1f, bpm = 100f, zcr = 0.05f)
        assertThat(tags.size).isAtLeast(1)
        assertThat(tags.size).isAtMost(2)
    }

    @Test
    fun `tags round trip through the comma list`() {
        val joined = SmartTagger.joinTags(listOf("Calm", "Romantic"))
        assertThat(SmartTagger.splitTags(joined)).containsExactly("Calm", "Romantic").inOrder()
        assertThat(SmartTagger.splitTags("")).isEmpty()
    }

    @Test
    fun `quantisation keeps scores close`() {
        val s = floatArrayOf(0f, 0.001f, 0.05f, 0.5f, 1f)
        val back = SmartVectors.dequantize(SmartVectors.quantize(s))
        s.indices.forEach { i -> assertThat(back[i]).isWithin(0.01f).of(s[i]) }
    }
}
