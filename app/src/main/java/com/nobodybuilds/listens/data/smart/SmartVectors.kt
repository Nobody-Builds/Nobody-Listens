package com.nobodybuilds.listens.data.smart

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pure vector helpers: score quantisation, embeddings and cosine maths. */
object SmartVectors {

    /** 0..1 scores → one byte each, square-root curve keeps small scores precise. */
    fun quantize(scores: FloatArray): ByteArray = ByteArray(scores.size) { i ->
        val v = sqrt(scores[i].coerceIn(0f, 1f)) * 255f
        v.roundToInt().coerceIn(0, 255).toByte()
    }

    fun dequantize(bytes: ByteArray): FloatArray = FloatArray(bytes.size) { i ->
        val q = (bytes[i].toInt() and 0xFF) / 255f
        q * q
    }

    /**
     * Raw (uncentred) embedding: sqrt of the music-range class scores plus three scaled DSP
     * features. The engine centres these on the library mean before cosine similarity.
     */
    fun embedding(scores: FloatArray, rms: Float, bpm: Float, zcr: Float): FloatArray {
        val n = SmartModel.MUSIC_RANGE_END - SmartModel.MUSIC_RANGE_START + 1
        val out = FloatArray(n + 3)
        for (i in 0 until n) {
            out[i] = sqrt(scores.getOrElse(SmartModel.MUSIC_RANGE_START + i) { 0f }.coerceAtLeast(0f))
        }
        out[n] = SmartTagger.energyLevel(rms) * DSP_WEIGHT
        out[n + 1] = SmartTagger.tempoLevel(bpm) * DSP_WEIGHT
        out[n + 2] = SmartTagger.brightnessLevel(zcr) * DSP_WEIGHT
        return out
    }

    private const val DSP_WEIGHT = 0.6f

    fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) s += a[i] * b[i]
        return s
    }

    fun norm(a: FloatArray): Float = sqrt(dot(a, a))

    /** Returns a normalised copy (zero vector stays zero). */
    fun normalized(a: FloatArray): FloatArray {
        val n = norm(a)
        return if (n <= 1e-9f) a.copyOf() else FloatArray(a.size) { a[it] / n }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        val na = norm(a)
        val nb = norm(b)
        if (na <= 1e-9f || nb <= 1e-9f) return 0f
        return dot(a, b) / (na * nb)
    }

    fun mean(vectors: Collection<FloatArray>): FloatArray? {
        val first = vectors.firstOrNull() ?: return null
        val out = FloatArray(first.size)
        vectors.forEach { v -> for (i in out.indices) out[i] += v.getOrElse(i) { 0f } }
        for (i in out.indices) out[i] /= vectors.size
        return out
    }

    /** Weighted sum (weights may be negative). */
    fun weightedSum(pairs: List<Pair<FloatArray, Float>>): FloatArray? {
        val first = pairs.firstOrNull()?.first ?: return null
        val out = FloatArray(first.size)
        pairs.forEach { (v, w) -> for (i in out.indices) out[i] += v.getOrElse(i) { 0f } * w }
        return out
    }

    fun subtract(a: FloatArray, b: FloatArray): FloatArray = FloatArray(a.size) { a[it] - b.getOrElse(it) { 0f } }
}
