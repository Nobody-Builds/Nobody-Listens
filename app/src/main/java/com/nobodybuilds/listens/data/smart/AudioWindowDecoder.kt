package com.nobodybuilds.listens.data.smart

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import timber.log.Timber

/**
 * Decodes a few short windows of a local audio file to 16 kHz mono float PCM using
 * MediaExtractor + MediaCodec (MP3, FLAC, AAC/M4A, OGG/Opus…). Blocking — call off the main thread.
 */
class AudioWindowDecoder(private val context: Context) {

    class DecodeException(message: String) : Exception(message)

    /**
     * @param windowMs length of each window
     * @param positions relative positions (0..1) of the window starts
     */
    fun decodeWindows(
        source: String,
        durationMsHint: Long,
        windowMs: Long = 10_000L,
        positions: List<Float> = listOf(0.2f, 0.5f, 0.75f),
        isCancelled: () -> Boolean = { false }
    ): List<FloatArray> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            setDataSource(extractor, source)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw DecodeException("No audio track")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw DecodeException("No mime")
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                durationMsHint * 1000L
            }
            if (durationUs <= 0L) throw DecodeException("Unknown duration")

            val windowUs = windowMs * 1000L
            val starts: List<Long> = when {
                durationUs <= windowUs + 2_000_000L -> listOf(0L) // short track: one window from the start
                durationUs < windowUs * 4 -> listOf(((durationUs - windowUs) / 2).coerceAtLeast(0L))
                else -> positions.map { (durationUs * it).toLong().coerceAtMost(durationUs - windowUs) }
            }

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val windows = ArrayList<FloatArray>(starts.size)
            for ((index, startUs) in starts.withIndex()) {
                if (isCancelled()) break
                if (index > 0) decoder.flush()
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val pcm = decodeOne(extractor, decoder, format, startUs, windowUs, isCancelled)
                if (pcm.isNotEmpty()) windows += pcm
            }
            return windows
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun setDataSource(extractor: MediaExtractor, source: String) {
        when {
            source.startsWith("content://") -> extractor.setDataSource(context, Uri.parse(source), null)
            source.startsWith("file://") -> extractor.setDataSource(Uri.parse(source).path ?: throw DecodeException("Bad path"))
            source.startsWith("/") && File(source).canRead() -> extractor.setDataSource(source)
            else -> throw DecodeException("Not a local file")
        }
    }

    private fun decodeOne(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        inputFormat: MediaFormat,
        startUs: Long,
        windowUs: Long,
        isCancelled: () -> Boolean
    ): FloatArray {
        val info = MediaCodec.BufferInfo()
        var sampleRate = inputFormat.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
        var channels = inputFormat.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
        var isFloat = false
        var outputFormatKnown = false

        // Mono samples at the source rate; resampled at the end.
        var mono = FloatArray(0)
        var monoLen = 0
        var targetMonoLen = 0

        var inputDone = false
        val endUs = startUs + windowUs
        val deadline = System.nanoTime() + MAX_WINDOW_DECODE_NANOS

        while (true) {
            if (isCancelled()) break
            if (System.nanoTime() > deadline) {
                Timber.w("Smart: decode window timed out")
                break
            }
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                    val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                    val time = extractor.sampleTime
                    if (size < 0 || time > endUs) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, time, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = decoder.outputFormat
                    sampleRate = f.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    channels = f.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                    isFloat = f.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
                        AudioFormat.ENCODING_PCM_FLOAT
                    outputFormatKnown = true
                }
                outIndex >= 0 -> {
                    if (!outputFormatKnown) {
                        val f = decoder.getOutputFormat(outIndex)
                        sampleRate = f.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = f.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        isFloat = f.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
                            AudioFormat.ENCODING_PCM_FLOAT
                        outputFormatKnown = true
                    }
                    if (targetMonoLen == 0) {
                        targetMonoLen = (sampleRate.toLong() * windowUs / 1_000_000L).toInt()
                        mono = FloatArray(targetMonoLen)
                    }
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (info.size > 0 && info.presentationTimeUs + 50_000L >= startUs && monoLen < targetMonoLen) {
                        val out = decoder.getOutputBuffer(outIndex)
                        if (out != null) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            out.order(ByteOrder.nativeOrder())
                            val ch = channels.coerceAtLeast(1)
                            if (isFloat) {
                                val fb = out.asFloatBuffer()
                                val frames = fb.remaining() / ch
                                for (frame in 0 until frames) {
                                    if (monoLen >= targetMonoLen) break
                                    var s = 0f
                                    for (c in 0 until ch) s += fb.get()
                                    mono[monoLen++] = s / ch
                                }
                            } else {
                                val sb = out.asShortBuffer()
                                val frames = sb.remaining() / ch
                                for (frame in 0 until frames) {
                                    if (monoLen >= targetMonoLen) break
                                    var s = 0f
                                    for (c in 0 until ch) s += sb.get() / 32768f
                                    mono[monoLen++] = s / ch
                                }
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (eos || (targetMonoLen > 0 && monoLen >= targetMonoLen)) break
                }
            }
        }
        if (monoLen == 0) return FloatArray(0)
        return AudioDsp.resample(mono, monoLen, sampleRate, SmartModel.SAMPLE_RATE)
    }

    private fun MediaFormat.getIntegerOr(key: String, default: Int): Int =
        if (containsKey(key)) try { getInteger(key) } catch (_: Exception) { default } else default

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val MAX_WINDOW_DECODE_NANOS = 20_000_000_000L // 20 s per window
    }
}
