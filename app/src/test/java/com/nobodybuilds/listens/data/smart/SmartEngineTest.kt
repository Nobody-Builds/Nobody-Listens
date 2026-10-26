package com.nobodybuilds.listens.data.smart

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SmartEngineTest {

    private fun vec(id: String, artist: String, vararg v: Float, energy: Float = 0.5f, tempo: Float = 110f, moods: List<String> = emptyList()) =
        SongVector(id, artist, SmartVectors.normalized(v), energy, tempo, 0.1f, moods, emptyList())

    @Test
    fun `taste centroid follows positive plays and away from skips`() {
        val a = vec("a", "x", 1f, 0f)
        val b = vec("b", "y", 0f, 1f)
        val now = 1_000_000_000L
        val taste = SmartEngine.tasteCentroid(
            mapOf("a" to a, "b" to b),
            listOf(PlaySignal("a", now, 1f), PlaySignal("a", now, 1f), PlaySignal("b", now, -0.7f)),
            now
        )!!
        assertThat(SmartVectors.dot(taste, a.vec)).isGreaterThan(SmartVectors.dot(taste, b.vec))
    }

    @Test
    fun `recency decay halves after one half life`() {
        assertThat(SmartEngine.decay(SmartEngine.HALF_LIFE_MS)).isWithin(1e-4f).of(0.5f)
        assertThat(SmartEngine.decay(0)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `similar songs exclude the seed and rank the nearest first`() {
        val seed = vec("s", "a", 1f, 0f, 0f)
        val near = vec("n", "b", 0.9f, 0.1f, 0f)
        val far = vec("f", "c", 0f, 0f, 1f)
        val result = SmartEngine.similar(seed, listOf(seed, near, far), limit = 2)
        assertThat(result.map { it.songId }).containsExactly("n", "f").inOrder()
    }

    @Test
    fun `mmr never puts three songs by one artist in a row when others exist`() {
        val songs = (0 until 6).map { vec("a$it", "same", 1f, it * 0.01f) } +
            (0 until 3).map { vec("b$it", "other", 0.5f, 0.5f) }
        val ordered = SmartEngine.mmr(songs, { it.vec[0] }, limit = 9)
        ordered.windowed(3).forEach { w ->
            val othersLeft = ordered.drop(ordered.indexOf(w.last())).any { it.artist != "same" }
            if (othersLeft) assertThat(w.map { it.artist }.distinct().size).isGreaterThan(1)
        }
    }

    @Test
    fun `k-means separates two obvious groups`() {
        val vectors = (0 until 10).map { SmartVectors.normalized(floatArrayOf(1f, it * 0.01f)) } +
            (0 until 10).map { SmartVectors.normalized(floatArrayOf(it * 0.01f, 1f)) }
        val (assign, centroids) = SmartEngine.kMeans(vectors, 2)
        assertThat(centroids).hasSize(2)
        assertThat(assign.take(10).distinct()).hasSize(1)
        assertThat(assign.drop(10).distinct()).hasSize(1)
        assertThat(assign[0]).isNotEqualTo(assign[10])
    }

    @Test
    fun `mood breakdown splits plays across tags`() {
        val result = SmartEngine.moodBreakdown(
            mapOf("a" to listOf("Calm"), "b" to listOf("Calm", "Romantic")),
            mapOf("a" to 2, "b" to 2)
        )
        assertThat(result["Calm"]).isWithin(1e-4f).of(0.75f)
        assertThat(result["Romantic"]).isWithin(1e-4f).of(0.25f)
    }

    @Test
    fun `workout mix needs high energy and tempo`() {
        val fast = (0 until 25).map { vec("f$it", "a$it", 1f, it.toFloat(), energy = 0.8f, tempo = 130f) }
        val slow = (0 until 25).map { vec("s$it", "b$it", 0f, 1f, energy = 0.2f, tempo = 80f) }
        val mixes = SmartEngine.moodMixes(fast + slow, taste = null)
        val workout = mixes.first { it.id == "smart_workout" }
        assertThat(workout.songIds.all { it.startsWith("f") }).isTrue()
    }

    @Test
    fun `drift label reflects distance`() {
        assertThat(SmartEngine.driftLabel(null)).isEqualTo("Not enough listening yet")
        assertThat(SmartEngine.driftLabel(0.05f)).contains("steady")
        assertThat(SmartEngine.driftLabel(0.9f)).contains("a lot")
    }

    @Test
    fun `tempo estimate finds a clear 120 bpm click`() {
        val sr = SmartModel.SAMPLE_RATE
        val pcm = FloatArray(sr * 10)
        val period = sr / 2 // 120 BPM
        var i = 0
        while (i < pcm.size) {
            for (j in 0 until 400) if (i + j < pcm.size) pcm[i + j] = if (j % 2 == 0) 0.8f else -0.8f
            i += period
        }
        val bpm = AudioDsp.estimateTempo(pcm, sr)
        assertThat(bpm).isWithin(6f).of(120f)
    }
}
