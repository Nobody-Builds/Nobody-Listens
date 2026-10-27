package com.nobodybuilds.listens.data.service.http

import android.content.ContentResolver
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.nobodybuilds.listens.data.model.Song
import timber.log.Timber
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class CastAacTranscoder(
    private val contentResolver: ContentResolver
) {
    private val ffmpegTranscoder = CastFfmpegTranscoder(contentResolver)

    /**
     * Transcodes audio (primarily ALAC/FLAC) to raw ADTS-framed AAC-LC using Android's MediaCodec.
     * The output stream receives a continuous sequence of 7-byte ADTS headers + AAC frames,
     * making it a valid audio/aac bitstream that the Cast Default Media Receiver can play.
     */
    fun transcodeToAacAdts(
        codecInfo: AudioCodecInfo,
        song: Song,
        uri: Uri,
        outputStream: OutputStream
    ) {
        val countingOutput = CountingOutputStream(outputStream)
        val shouldTryFfmpeg = when (codecInfo.codecMime) {
            "audio/alac" -> isFfmpegAlacTranscodeSupported()
            "audio/ac3", "audio/eac3" -> isFfmpegAc3TranscodeSupported(codecInfo.codecMime)
            else -> false
        }
        if (shouldTryFfmpeg) {
            val codecLabel = codecInfo.codecMime.substringAfter('/').uppercase()
            val ffmpegFailure = runCatching {
                Timber.tag(CAST_HTTP_LOG_TAG).i(
                    "transcode %s→AAC using FFmpeg decoder songId=%s sr=%d ch=%d",
                    codecLabel, song.id, codecInfo.sampleRate, codecInfo.channelCount
                )
                Timber.tag("PX_CAST_HTTP")
                    .i("transcode_ffmpeg codec=${codecInfo.codecMime} songId=${song.id} sr=${codecInfo.sampleRate} ch=${codecInfo.channelCount}")
                ffmpegTranscoder.transcodeToAacAdtsViaFfmpeg(codecInfo, song, uri, countingOutput)
            }.exceptionOrNull()

            if (ffmpegFailure == null) {
                return
            }

            if (!ffmpegFailure.isClientAbortDuringResponse()) {
                Timber.tag(CAST_HTTP_LOG_TAG).w(
                    ffmpegFailure,
                    "FFmpeg %s transcode failed, falling back to MediaCodec songId=%s",
                    codecLabel, song.id
                )
            }

            if (countingOutput.bytesWritten > 0L) {
                throw ffmpegFailure
            }
        }

        transcodeToAacAdtsViaMediaCodec(codecInfo, song, uri, countingOutput)
    }

    private fun transcodeToAacAdtsViaMediaCodec(
        codecInfo: AudioCodecInfo,
        song: Song,
        uri: Uri,
        outputStream: OutputStream
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        val tid = android.os.Process.myTid()
        val originalPriority = android.os.Process.getThreadPriority(tid)
        try {
            val opened = runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val len = afd.length.takeIf { it > 0 } ?: afd.declaredLength.takeIf { it > 0 }
                    if (len != null) extractor.setDataSource(afd.fileDescriptor, afd.startOffset, len)
                    else extractor.setDataSource(afd.fileDescriptor)
                } != null
            }.getOrElse { false } || runCatching {
                song.path.takeIf { it.isNotBlank() }?.let { extractor.setDataSource(it) }
                true
            }.getOrElse { false }
            if (!opened) {
                Timber.tag(CAST_HTTP_LOG_TAG).e("transcode: failed to open source songId=%s", song.id)
                return
            }

            val inputFormat = extractor.getTrackFormat(codecInfo.trackIndex)
            extractor.selectTrack(codecInfo.trackIndex)

            // Use the specific codec name returned by findDecoderForFormat; if that
            // specific decoder fails to configure (rare on some Qualcomm devices) or is null 
            // (common for high-sample-rate ALAC/FLAC formats due to strict OS config limits),
            // strictly fall back to createDecoderByType which lets the system pick any available decoder.
            val decoderName = runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(inputFormat)
            }.getOrNull()

            if (decoderName != null) {
                decoder = runCatching {
                    MediaCodec.createByCodecName(decoderName).also { dec ->
                        dec.configure(inputFormat, null, null, 0)
                        dec.start()
                    }
                }.getOrElse { namedDecoderError ->
                    Timber.tag(CAST_HTTP_LOG_TAG).w(
                        namedDecoderError,
                        "transcodeToAacAdts: named decoder '%s' failed, retrying with createDecoderByType for %s songId=%s",
                        decoderName, codecInfo.codecMime, song.id
                    )
                    null
                }
            }

            if (decoder == null) {
                // Important: We must patch the inputFormat's MIME type to match precisely the codec we
                // are asking for, as the format could have had "audio/mp4a-latm" (which we safely overrode)
                inputFormat.setString(MediaFormat.KEY_MIME, codecInfo.codecMime)
                decoder = runCatching {
                    MediaCodec.createDecoderByType(codecInfo.codecMime).also { dec ->
                        dec.configure(inputFormat, null, null, 0)
                        dec.start()
                    }
                }.getOrElse { fallbackError ->
                    Timber.tag(CAST_HTTP_LOG_TAG).e(
                        fallbackError,
                        "transcodeToAacAdts: fallback decoder failed for %s songId=%s",
                        codecInfo.codecMime, song.id
                    )
                    Timber.tag("PX_CAST_HTTP")
                        .e("transcode_no_decoder songId=${song.id} codec=${codecInfo.codecMime}")
                    return
                }
            }


            val encChannels = minOf(codecInfo.channelCount, 2)
            val bitrate = when {
                encChannels >= 2 && codecInfo.sampleRate >= 44100 -> 256_000
                encChannels >= 2 -> 192_000
                else -> 128_000
            }
            val encFormat = MediaFormat.createAudioFormat(
                "audio/mp4a-latm", codecInfo.sampleRate, encChannels
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
            }
            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            val TIMEOUT_US = 20_000L
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            var srcDone = false
            var decDone = false
            var encDone = false

            // Pre-allocate buffers to eliminate GC storms in real-time loop.
            var pcmBuffer = ByteArray(32768)
            var downmixBuffer = ByteArray(32768)
            var actualDecoderChannels = codecInfo.channelCount

            while (!encDone) {
                // Feed compressed packets from extractor into decoder
                if (!srcDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)
                        if (buf != null) {
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                srcDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Pull decoded PCM from decoder and push into encoder
                if (!decDone) {
                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        runCatching {
                            val newFormat = decoder.outputFormat
                            if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                actualDecoderChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                    } else if (outIdx >= 0) {
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val pcm = if (bufferInfo.size > 0) decoder.getOutputBuffer(outIdx) else null
                        
                        if (pcm != null) {
                            pcm.position(bufferInfo.offset)
                            pcm.limit(bufferInfo.offset + bufferInfo.size)
                        }
                        
                        var remaining = bufferInfo.size
                        var pts = bufferInfo.presentationTimeUs
                        val bytesPerSampleFrame = 2 * actualDecoderChannels
                        
                        val isEosEmpty = isEos && remaining == 0
                        while (remaining > 0 || isEosEmpty) {
                            val encInIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                            if (encInIdx < 0) {
                                if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                                    encDone = true
                                    break
                                }
                                continue
                            }
                            
                            val encBuf = encoder.getInputBuffer(encInIdx) ?: break
                            encBuf.clear()
                            
                            val frameBytesIn = bytesPerSampleFrame.takeIf { it > 0 } ?: (actualDecoderChannels * 2)
                            val frameBytesOut = encChannels * 2
                            val rawCapacityFrames = if (frameBytesOut > 0) encBuf.capacity() / frameBytesOut else 1024
                            val maxOutCapacityFrames = maxOf(rawCapacityFrames, 1)
                            val maxInputBytes = maxOutCapacityFrames * frameBytesIn

                            val rawToWrite = if (isEosEmpty) 0 else minOf(remaining, maxInputBytes)
                            val toWrite = if (frameBytesIn > 0) (rawToWrite / frameBytesIn) * frameBytesIn else rawToWrite
                            var encoderBytesAssigned = toWrite
                            if (toWrite > 0 && pcm != null) {
                                if (pcmBuffer.size < toWrite) {
                                    pcmBuffer = ByteArray(toWrite)
                                }
                                pcm.get(pcmBuffer, 0, toWrite)
                                
                                if (actualDecoderChannels > 2) {
                                    val inChannels = actualDecoderChannels
                                    val outChannels = encChannels
                                    val frameBytesIn = inChannels * 2
                                    val frameBytesOut = outChannels * 2
                                    val numFrames = toWrite / frameBytesIn
                                    val outSize = numFrames * frameBytesOut
                                    
                                    if (downmixBuffer.size < outSize) {
                                        downmixBuffer = ByteArray(outSize)
                                    }
                                    
                                    val inBuf = ByteBuffer.wrap(pcmBuffer, 0, toWrite).order(
                                        ByteOrder.nativeOrder()).asShortBuffer()
                                    val outBuf = ByteBuffer.wrap(downmixBuffer, 0, outSize).order(ByteOrder.nativeOrder()).asShortBuffer()
                                    
                                    for (i in 0 until numFrames) {
                                        if (inChannels == 6) {
                                            val c0 = inBuf.get()
                                            val c1 = inBuf.get()
                                            val c2 = inBuf.get()
                                            val lfe = inBuf.get()
                                            val c4 = inBuf.get()
                                            val c5 = inBuf.get()
                                            val left = (c0 * 0.5f + c2 * 0.35f + c4 * 0.35f).toInt()
                                            val right = (c1 * 0.5f + c2 * 0.35f + c5 * 0.35f).toInt()
                                            outBuf.put(left.coerceIn(-32768, 32767).toShort())
                                            outBuf.put(right.coerceIn(-32768, 32767).toShort())
                                        } else {
                                            outBuf.put(inBuf.get())
                                            outBuf.put(inBuf.get())
                                            for (c in 2 until inChannels) {
                                                inBuf.get()
                                            }
                                        }
                                    }
                                    encBuf.put(downmixBuffer, 0, outSize)
                                    encoderBytesAssigned = outSize
                                } else {
                                    encBuf.put(pcmBuffer, 0, toWrite)
                                    encoderBytesAssigned = toWrite
                                }
                            }
                            
                            val eos = isEos && remaining <= toWrite
                            encoder.queueInputBuffer(
                                encInIdx, 0, encoderBytesAssigned, pts,
                                if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            )
                            
                            if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                                encDone = true
                                break
                            }
                            
                            if (bytesPerSampleFrame > 0 && toWrite > 0) {
                                pts += (toWrite.toLong() * 1_000_000L / (bytesPerSampleFrame * codecInfo.sampleRate))
                            }
                            remaining -= toWrite
                            if (isEosEmpty) break
                        }
                        
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (isEos) decDone = true
                    }
                }

                // Drain encoder output
                if (!encDone) {
                    if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                        encDone = true
                    }
                }
            }
            outputStream.flush()
        } catch (e: Exception) {
            if (!e.isClientAbortDuringResponse()) {
                Timber.tag(CAST_HTTP_LOG_TAG).e(e, "transcode %s→AAC error songId=%s", codecInfo.codecMime, song.id)
                Timber.tag("PX_CAST_HTTP").e(e, "transcode_ffmpeg_error codec=${codecInfo.codecMime} songId=${song.id}")
            }
        } finally {
            // Always restore the thread priority so pooled IO threads aren't permanently
            // degraded — Ktor reuses threads and a background-priority thread causes
            // subsequent raw-file requests to time out with "Broken pipe".
            runCatching { android.os.Process.setThreadPriority(originalPriority) }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { decoder?.stop(); decoder?.release() }
            runCatching { extractor.release() }
        }
    }
}
