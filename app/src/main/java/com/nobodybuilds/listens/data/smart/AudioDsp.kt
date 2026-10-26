package com.nobodybuilds.listens.data.smart

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Cheap pure-Kotlin DSP on 16 kHz mono PCM in [-1, 1]. */
object AudioDsp {

    data class DspFeatures(val rmsMean: Float, val rmsVar: Float, val tempoBpm: Float, val zcr: Float)

    /**
     * Box-filter + linear resampler from [srcRate] to [dstRate]. Good enough for YAMNet's
     * log-mel front-end (it only looks at < 8 kHz anyway).
     */
    fun resample(input: FloatArray, length: Int, srcRate: Int, dstRate: Int): FloatArray {
        if (length <= 0 || srcRate <= 0) return FloatArray(0)
        if (srcRate == dstRate) return input.copyOf(length)
        val ratio = srcRate.toDouble() / dstRate
        val outLen = (length / ratio).toInt()
        val out = FloatArray(outLen)
        if (ratio > 1.0) {
            // Downsampling: average the source samples that fall into each output sample.
            for (i in 0 until outLen) {
                val start = (i * ratio).toInt()
                val end = minOf(length, ((i + 1) * ratio).toInt().coerceAtLeast(start + 1))
                var sum = 0f
                for (j in start until end) sum += input[j]
                out[i] = sum / (end - start)
            }
        } else {
            for (i in 0 until outLen) {
                val pos = i * ratio
                val j = pos.toInt()
                val frac = (pos - j).toFloat()
                val a = input[j.coerceAtMost(length - 1)]
                val b = input[(j + 1).coerceAtMost(length - 1)]
                out[i] = a + (b - a) * frac
            }
        }
        return out
    }

    fun compute(pcm: List<FloatArray>, sampleRate: Int = SmartModel.SAMPLE_RATE): DspFeatures {
        val rmsValues = ArrayList<Float>()
        var zcSum = 0.0
        var zcCount = 0L
        val tempos = ArrayList<Float>()
        val frame = sampleRate / 20 // 50 ms
        for (window in pcm) {
            var i = 0
            while (i + frame <= window.size) {
                var s = 0.0
                for (j in i until i + frame) s += window[j] * window[j]
                rmsValues += sqrt(s / frame).toFloat()
                i += frame
            }
            for (j in 1 until window.size) {
                if ((window[j] >= 0f) != (window[j - 1] >= 0f)) zcSum += 1.0
            }
            zcCount += (window.size - 1).coerceAtLeast(0)
            val bpm = estimateTempo(window, sampleRate)
            if (bpm > 0f) tempos += bpm
        }
        // Ignore near-silent frames (intros, gaps) for the energy statistics.
        val voiced = rmsValues.filter { it > 0.005f }.ifEmpty { rmsValues }
        val mean = if (voiced.isEmpty()) 0f else voiced.average().toFloat()
        val variance = if (voiced.isEmpty()) 0f else voiced.map { (it - mean) * (it - mean) }.average().toFloat()
        val tempo = if (tempos.isEmpty()) 0f else tempos.sorted()[tempos.size / 2]
        val zcr = if (zcCount == 0L) 0f else (zcSum / zcCount).toFloat()
        return DspFeatures(mean, variance, tempo, zcr)
    }

    /**
     * Onset-strength autocorrelation tempo estimate in 60–200 BPM, with a log-normal prior
     * around 120 BPM to reduce octave errors. Returns 0 when there is no clear pulse.
     */
    fun estimateTempo(pcm: FloatArray, sampleRate: Int = SmartModel.SAMPLE_RATE): Float {
        val hop = 256
        val win = 512
        if (pcm.size < win * 8) return 0f
        val frames = (pcm.size - win) / hop
        val logEnergy = FloatArray(frames)
        for (f in 0 until frames) {
            val start = f * hop
            var prev = if (start > 0) pcm[start - 1] else 0f
            var s = 0.0
            // First-difference high-pass emphasises percussive onsets.
            for (j in start until start + win) {
                val x = pcm[j]
                val d = x - prev
                prev = x
                s += d * d
            }
            logEnergy[f] = ln(1e-6 + s / win).toFloat()
        }
        val onset = FloatArray(frames)
        for (f in 1 until frames) onset[f] = (logEnergy[f] - logEnergy[f - 1]).coerceAtLeast(0f)
        val mean = onset.average().toFloat()
        for (f in onset.indices) onset[f] -= mean

        val fps = sampleRate.toFloat() / hop
        val minLag = (fps * 60f / 200f).toInt().coerceAtLeast(1)
        val maxLag = (fps * 60f / 60f).toInt()
        if (maxLag >= frames / 2) return 0f
        var bestLag = -1
        var bestScore = 0.0
        var zeroLag = 0.0
        for (f in 0 until frames) zeroLag += onset[f] * onset[f]
        if (zeroLag <= 1e-9) return 0f
        for (lag in minLag..maxLag) {
            var acc = 0.0
            for (f in 0 until frames - lag) acc += onset[f] * onset[f + lag]
            acc /= zeroLag
            val bpm = 60.0 * fps / lag
            val prior = exp(-0.5 * (ln(bpm / 120.0) / 0.5).let { it * it })
            val score = acc * prior
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestScore < 0.02) return 0f
        return (60f * fps / bestLag)
    }

    /** Average absolute amplitude, used to skip silent windows. */
    fun meanAbs(pcm: FloatArray): Float {
        if (pcm.isEmpty()) return 0f
        var s = 0.0
        for (x in pcm) s += abs(x)
        return (s / pcm.size).toFloat()
    }
}
