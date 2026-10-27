package com.nobodybuilds.listens.data.service.http

import android.content.ContentResolver
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import com.nobodybuilds.listens.data.model.Song
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes ALAC and AC3/EAC3 with the FFmpeg extension and re-encodes to ADTS AAC. */
internal class CastFfmpegTranscoder(
    private val contentResolver: ContentResolver
) {
    @OptIn(UnstableApi::class)
    fun transcodeToAacAdtsViaFfmpeg(
        codecInfo: AudioCodecInfo,
        song: Song,
        uri: Uri,
        outputStream: OutputStream
    ) {
        val extractor = MediaExtractor()
        var decoder: Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *>? = null
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
                error("FFmpeg transcode: failed to open source songId=${song.id}")
            }

            val inputFormat = extractor.getTrackFormat(codecInfo.trackIndex)
            inputFormat.setString(MediaFormat.KEY_MIME, codecInfo.codecMime)
            extractor.selectTrack(codecInfo.trackIndex)
            decoder = when (codecInfo.codecMime) {
                "audio/alac" -> createFfmpegAlacDecoder(inputFormat, codecInfo)
                "audio/ac3", "audio/eac3" -> createFfmpegAc3Decoder(inputFormat, codecInfo)
                else -> error("FFmpeg transcode: unsupported codec ${codecInfo.codecMime}")
            }

            val encChannels = minOf(codecInfo.channelCount, 2)
            val bitrate = when {
                encChannels >= 2 && codecInfo.sampleRate >= 44100 -> 256_000
                encChannels >= 2 -> 192_000
                else -> 128_000
            }
            val encFormat = MediaFormat.createAudioFormat(
                "audio/mp4a-latm",
                codecInfo.sampleRate,
                encChannels
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
            }
            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val encoderInfo = MediaCodec.BufferInfo()
            val timeoutUs = 20_000L
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val bytesPerSampleFrame = 2 * codecInfo.channelCount
            var srcDone = false
            var decDone = false
            var encDone = false
            var encoderEosQueued = false
            
            var pcmBuffer = ByteArray(32768)
            var downmixBuffer = ByteArray(32768)

            while (!encDone) {
                if (!srcDone) {
                    val inputBuffer = decoder.dequeueInputBuffer()
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val expectedSize = extractor.sampleSize
                            .takeIf { it in 1..Int.MAX_VALUE.toLong() }
                            ?.toInt()
                            ?: runCatching {
                                inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                            }.getOrDefault(16_384)

                        inputBuffer.ensureSpaceForWrite(expectedSize)
                        val sampleData = inputBuffer.data
                            ?: error("FFmpeg transcode: missing decoder input buffer")
                        sampleData.clear()

                        val bytesRead = extractor.readSampleData(sampleData, 0)
                        if (bytesRead < 0) {
                            inputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                            inputBuffer.timeUs = 0L
                            decoder.queueInputBuffer(inputBuffer)
                            srcDone = true
                        } else {
                            sampleData.position(bytesRead)
                            inputBuffer.timeUs = extractor.sampleTime
                            inputBuffer.flip()
                            decoder.queueInputBuffer(inputBuffer)
                            extractor.advance()
                        }
                    }
                }

                if (!decDone) {
                    var decoderOutput = decoder.dequeueOutputBuffer()
                    while (decoderOutput != null) {
                        val isEos = decoderOutput.isEndOfStream()
                        val pcm = decoderOutput.data?.duplicate()?.apply {
                            position(0)
                            limit(decoderOutput!!.data?.limit() ?: 0)
                        }

                        if (!decoderOutput.shouldBeSkipped && pcm != null && pcm.hasRemaining()) {
                            var remaining = pcm.remaining()
                            var pts = decoderOutput.timeUs
                            while (remaining > 0) {
                                val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                                if (encInIdx < 0) {
                                    if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                                        encDone = true
                                        break
                                    }
                                    continue
                                }
                                
                                val encBuf = encoder.getInputBuffer(encInIdx) ?: break
                                encBuf.clear()
                                val frameBytesInAlign = codecInfo.channelCount * 2
                                val frameBytesOut = encChannels * 2
                                val rawCapacityFrames = if (frameBytesOut > 0) encBuf.capacity() / frameBytesOut else 1024
                                val maxOutCapacityFrames = maxOf(rawCapacityFrames, 1)
                                val maxInputBytes = maxOutCapacityFrames * frameBytesInAlign
                                
                                val rawToWrite = minOf(remaining, maxInputBytes)
                                val toWrite = if (frameBytesInAlign > 0) (rawToWrite / frameBytesInAlign) * frameBytesInAlign else rawToWrite
                                var encoderBytesAssigned = toWrite
                                
                                if (pcmBuffer.size < toWrite) {
                                    pcmBuffer = ByteArray(toWrite)
                                }
                                pcm.get(pcmBuffer, 0, toWrite)
                                
                                if (codecInfo.channelCount > 2) {
                                    val inChannels = codecInfo.channelCount
                                    val outChannels = encChannels
                                    val frameBytesIn = inChannels * 2
                                    val frameBytesOut = outChannels * 2
                                    val numFrames = toWrite / frameBytesIn
                                    val outSize = numFrames * frameBytesOut
                                    
                                    if (downmixBuffer.size < outSize) {
                                        downmixBuffer = ByteArray(outSize)
                                    }
                                    
                                    val inBuf = ByteBuffer.wrap(pcmBuffer, 0, toWrite).order(ByteOrder.nativeOrder()).asShortBuffer()
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
                                val eos = isEos && remaining <= toWrite
                                encoder.queueInputBuffer(
                                    encInIdx,
                                    0,
                                    encoderBytesAssigned,
                                    pts,
                                    if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                )
                                
                                if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                                    encDone = true
                                    break
                                }
                                
                                if (eos) {
                                    encoderEosQueued = true
                                }
                                if (bytesPerSampleFrame > 0) {
                                    pts += toWrite.toLong() * 1_000_000L /
                                        (bytesPerSampleFrame * codecInfo.sampleRate)
                                }
                                remaining -= toWrite
                                if (drainEncoderToAdts(
                                        encoder,
                                        encoderInfo,
                                        codecInfo.sampleRate,
                                        encChannels,
                                        outputStream
                                    )
                                ) {
                                    encDone = true
                                }
                            }
                        } else if (isEos && !encoderEosQueued) {
                            queueEncoderEndOfStream(encoder, encoderInfo, decoderOutput.timeUs, outputStream, codecInfo)
                            encoderEosQueued = true
                        }

                        decoderOutput.release()
                        if (isEos) {
                            decDone = true
                        }
                        if (encDone) {
                            break
                        }
                        decoderOutput = decoder.dequeueOutputBuffer()
                    }
                }

                if (srcDone && decDone && !encoderEosQueued) {
                    queueEncoderEndOfStream(encoder, encoderInfo, 0L, outputStream, codecInfo)
                    encoderEosQueued = true
                }

                if (!encDone) {
                    if (drainEncoderToAdts(
                            encoder,
                            encoderInfo,
                            codecInfo.sampleRate,
                            codecInfo.channelCount,
                            outputStream
                        )
                    ) {
                        encDone = true
                    }
                }
            }
            outputStream.flush()
        } finally {
            runCatching { android.os.Process.setThreadPriority(originalPriority) }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    @OptIn(UnstableApi::class)
    private fun createFfmpegAlacDecoder(
        inputFormat: MediaFormat,
        codecInfo: AudioCodecInfo
    ): Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *> {
        val initData = extractCodecSpecificData(inputFormat)
        require(initData.isNotEmpty()) {
            "FFmpeg ALAC transcode requires codec initialization data"
        }

        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(codecInfo.channelCount)
            .setSampleRate(codecInfo.sampleRate)
            .setInitializationData(initData)
            .setMaxInputSize(
                runCatching { inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                    .getOrDefault(64 * 1024)
            )
            .build()

        val decoderClass = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder")
        val constructor = decoderClass.getDeclaredConstructor(
            Format::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(
            format,
            16,
            16,
            format.maxInputSize.coerceAtLeast(16_384),
            false
        ) as Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *>
    }

    /**
     * Creates an FFmpeg decoder for AC3/EAC3.
     * Unlike ALAC, AC3 is self-framing and requires no codec initialization data (no CSD-0).
     */
    @OptIn(UnstableApi::class)
    private fun createFfmpegAc3Decoder(
        inputFormat: MediaFormat,
        codecInfo: AudioCodecInfo
    ): Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *> {
        val format = Format.Builder()
            .setSampleMimeType(codecInfo.codecMime)
            .setChannelCount(codecInfo.channelCount)
            .setSampleRate(codecInfo.sampleRate)
            .setMaxInputSize(
                runCatching { inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                    .getOrDefault(64 * 1024)
            )
            .build()

        val decoderClass = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder")
        val constructor = decoderClass.getDeclaredConstructor(
            Format::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(
            format,
            16,
            16,
            format.maxInputSize.coerceAtLeast(16_384),
            false
        ) as Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *>
    }

    private fun extractCodecSpecificData(format: MediaFormat): List<ByteArray> {
        val initData = mutableListOf<ByteArray>()
        var index = 0
        while (true) {
            val csd = runCatching { format.getByteBuffer("csd-$index") }.getOrNull() ?: break
            val bytes = csd.toByteArray()
            if (bytes.isNotEmpty()) {
                initData += bytes
            }
            index += 1
        }
        return initData
    }

    private fun queueEncoderEndOfStream(
        encoder: MediaCodec,
        encoderInfo: MediaCodec.BufferInfo,
        ptsUs: Long,
        outputStream: OutputStream,
        codecInfo: AudioCodecInfo
    ) {
        repeat(4) {
            val encInIdx = encoder.dequeueInputBuffer(20_000L)
            if (encInIdx >= 0) {
                encoder.queueInputBuffer(
                    encInIdx,
                    0,
                    0,
                    ptsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                return
            }
            drainEncoderToAdts(
                encoder,
                encoderInfo,
                codecInfo.sampleRate,
                codecInfo.channelCount,
                outputStream
            )
        }
        error("Failed to queue AAC encoder EOS")
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val duplicate = duplicate()
        duplicate.position(0)
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }
}
