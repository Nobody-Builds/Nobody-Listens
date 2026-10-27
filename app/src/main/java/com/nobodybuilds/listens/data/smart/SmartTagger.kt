package com.nobodybuilds.listens.data.smart

import com.nobodybuilds.listens.data.smart.SmartModel.ANGRY
import com.nobodybuilds.listens.data.smart.SmartModel.EXCITING
import com.nobodybuilds.listens.data.smart.SmartModel.HAPPY
import com.nobodybuilds.listens.data.smart.SmartModel.SAD
import com.nobodybuilds.listens.data.smart.SmartModel.SCARY
import com.nobodybuilds.listens.data.smart.SmartModel.TENDER
import kotlin.math.abs

/**
 * Pure mapping from YAMNet scores + cheap DSP features to mood and genre tags.
 * No Android dependencies so it can be unit tested.
 */
object SmartTagger {

    /** Minimum (averaged) genre score for a genre tag. YAMNet genre scores are small. */
    const val GENRE_THRESHOLD = 0.04f
    private const val MOOD_SECOND_RATIO = 0.85f
    private const val MOOD_MIN_SCORE = 0.2f

    // Genres that are too generic to show as a tag.
    private val HIDDEN_GENRES = setOf(249, 260, 259)

    /** RMS energy (mean of 50 ms frames, mono, [-1,1]) → 0..1. */
    fun energyLevel(rms: Float): Float = ((rms - 0.05f) / 0.25f).coerceIn(0f, 1f)

    /** BPM → 0..1 over 60–180. */
    fun tempoLevel(bpm: Float): Float = if (bpm <= 0f) 0.5f else ((bpm - 60f) / 120f).coerceIn(0f, 1f)

    /** Mean zero-crossing rate (0..0.5) → 0..1. */
    fun brightnessLevel(zcr: Float): Float = ((zcr - 0.02f) / 0.13f).coerceIn(0f, 1f)

    fun vocalness(scores: FloatArray): Float =
        SmartModel.VOCAL_CLASSES.maxOf { scores.getOrElse(it) { 0f } }

    fun genreTags(scores: FloatArray, max: Int = 2): List<String> {
        val ranked = SmartModel.GENRES.entries
            .filter { it.key !in HIDDEN_GENRES }
            .map { it.value to scores.getOrElse(it.key) { 0f } }
            .filter { it.second >= GENRE_THRESHOLD }
            .sortedByDescending { it.second }
        if (ranked.isEmpty()) return emptyList()
        val top = ranked.first().second
        return ranked.take(max).filterIndexed { i, (_, s) -> i == 0 || s >= top * 0.4f }.map { it.first }
    }

    /** Scores for each mood (0..~1.5). Exposed for tests and the insights screen. */
    fun moodScores(scores: FloatArray, rms: Float, bpm: Float, zcr: Float): Map<String, Float> {
        fun m(i: Int) = (scores.getOrElse(i) { 0f } * 4f).coerceIn(0f, 1f)
        fun g(vararg idx: Int) = idx.maxOf { (scores.getOrElse(it) { 0f } * 3f).coerceIn(0f, 1f) }
        val e = energyLevel(rms)
        val t = tempoLevel(bpm)
        val b = brightnessLevel(zcr)
        val danceTempo = if (bpm in 112f..135f) 1f else (1f - abs(bpm - 124f) / 40f).coerceIn(0f, 1f)
        val midTempo = (1f - abs(bpm - 115f) / 50f).coerceIn(0f, 1f)

        return linkedMapOf(
            SmartModel.MOOD_CALM to (0.5f * m(TENDER) + 0.55f * (1f - e) + 0.2f * (1f - t) +
                0.35f * g(241, 248, 232, 266) - 0.3f * m(ANGRY)),
            SmartModel.MOOD_UPBEAT to (0.6f * m(HAPPY) + 0.3f * e + 0.25f * midTempo +
                0.3f * g(211, 231, 227, 223, 243) - 0.3f * m(SAD)),
            SmartModel.MOOD_MELANCHOLY to (0.7f * m(SAD) + 0.3f * (1f - e) + 0.25f * g(246, 222) +
                0.1f * (1f - b) - 0.3f * m(HAPPY)),
            SmartModel.MOOD_ENERGETIC to (0.5f * m(EXCITING) + 0.55f * e + 0.25f * t +
                0.35f * g(214, 215, 216, 238, 236)),
            SmartModel.MOOD_ROMANTIC to (0.6f * m(TENDER) + 0.35f * g(221, 222, 257, 230) +
                0.2f * (1f - abs(e - 0.4f) * 2f).coerceIn(0f, 1f) - 0.3f * m(ANGRY)),
            SmartModel.MOOD_DARK to (0.6f * m(SCARY) + 0.5f * m(ANGRY) + 0.3f * g(215, 237, 236) +
                0.2f * (1f - b) - 0.3f * m(HAPPY)),
            SmartModel.MOOD_PARTY to (0.45f * g(269, 235, 240, 231, 212, 234) + 0.35f * e +
                0.35f * danceTempo + 0.15f * m(HAPPY))
        )
    }

    /** 1–2 mood tags, strongest first. */
    fun moodTags(scores: FloatArray, rms: Float, bpm: Float, zcr: Float): List<String> {
        val ranked = moodScores(scores, rms, bpm, zcr).entries.sortedByDescending { it.value }
        val first = ranked.first()
        val result = mutableListOf(first.key)
        val second = ranked.getOrNull(1)
        if (second != null && second.value >= MOOD_MIN_SCORE && second.value >= first.value * MOOD_SECOND_RATIO) {
            result += second.key
        }
        return result
    }

    fun joinTags(tags: List<String>): String = tags.joinToString(",")

    fun splitTags(raw: String?): List<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
