package com.nobodybuilds.listens.data.smart

import android.content.Context
import com.nobodybuilds.listens.data.database.SmartSongRow
import com.nobodybuilds.listens.data.database.SongFeaturesEntity
import java.io.Closeable
import java.io.IOException
import timber.log.Timber

/**
 * Analyses one song at a time: decode 3 short windows → YAMNet scores + DSP → tags.
 * Blocking; run on a background dispatcher. Real decode failures become STATUS_FAILED rows;
 * transient errors (I/O, permissions, codec state, low memory) return null so the song is tried
 * again later. Throws [SmartAnalysisUnavailableException] when the ML runtime itself fails, so
 * the caller can stop the whole batch without marking songs failed.
 */
class SongAnalyzer(context: Context) : Closeable {

    private val decoder = AudioWindowDecoder(context)
    private val classifier = YamnetClassifier(context)

    fun analyze(song: SmartSongRow, isCancelled: () -> Boolean): SongFeaturesEntity? {
        val songId = song.id.toString()
        return try {
            val source = song.contentUriString.ifBlank { song.filePath }
            val windows = decoder.decodeWindows(source, song.duration, isCancelled = isCancelled)
                .filter { AudioDsp.meanAbs(it) > 0.0005f }
            if (isCancelled()) return null
            if (windows.isEmpty()) return failed(songId)
            val scores = try {
                classifier.classifyWindows(windows, isCancelled)
            } catch (t: Throwable) {
                // Native load errors (UnsatisfiedLinkError etc.) or interpreter failures aren't about this song.
                throw SmartAnalysisUnavailableException(t)
            } ?: return if (isCancelled()) null else failed(songId)
            val dsp = AudioDsp.compute(windows)
            val moods = SmartTagger.moodTags(scores, dsp.rmsMean, dsp.tempoBpm, dsp.zcr)
            val genres = SmartTagger.genreTags(scores)
            SongFeaturesEntity(
                songId = songId,
                modelVersion = SmartModel.MODEL_VERSION,
                analyzedAt = System.currentTimeMillis(),
                scores = SmartVectors.quantize(scores),
                energy = dsp.rmsMean,
                energyVar = dsp.rmsVar,
                tempo = dsp.tempoBpm,
                brightness = dsp.zcr,
                moodTags = SmartTagger.joinTags(moods),
                genreTags = SmartTagger.joinTags(genres),
                status = SongFeaturesEntity.STATUS_OK
            )
        } catch (e: SmartAnalysisUnavailableException) {
            throw e
        } catch (e: IOException) {
            transient(songId, e)
        } catch (e: SecurityException) {
            transient(songId, e)
        } catch (e: IllegalStateException) {
            // MediaExtractor.setDataSource / MediaCodec state errors (codec busy, reclaimed, …).
            transient(songId, e)
        } catch (e: OutOfMemoryError) {
            System.gc()
            transient(songId, e)
        } catch (e: Error) {
            throw SmartAnalysisUnavailableException(e)
        } catch (t: Throwable) {
            Timber.w(t, "Smart: analysis failed for song %s", songId)
            failed(songId)
        }
    }

    private fun transient(songId: String, t: Throwable): SongFeaturesEntity? {
        Timber.w(t, "Smart: transient error for song %s, will retry", songId)
        return null
    }

    private fun failed(songId: String) = SongFeaturesEntity(
        songId = songId,
        modelVersion = SmartModel.MODEL_VERSION,
        analyzedAt = System.currentTimeMillis(),
        scores = null,
        energy = 0f,
        energyVar = 0f,
        tempo = 0f,
        brightness = 0f,
        moodTags = "",
        genreTags = "",
        status = SongFeaturesEntity.STATUS_FAILED
    )

    override fun close() {
        classifier.close()
    }
}

/** The on-device model couldn't be loaded or run; abort the batch and retry later. */
class SmartAnalysisUnavailableException(cause: Throwable) : Exception("Smart analysis unavailable", cause)
