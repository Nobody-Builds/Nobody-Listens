package com.nobodybuilds.listens.data.service.http

import android.content.ContentResolver
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.nobodybuilds.listens.data.model.Song
import java.util.Locale

internal data class AudioCodecInfo(
    val codecMime: String,
    val sampleRate: Int,
    val channelCount: Int,
    val trackIndex: Int
)

/** Detects a song's real audio codec and whether this device can transcode it for Cast. */
internal class CastCodecSupport(
    private val contentResolver: ContentResolver
) {
    // Cache for the actual codec info (codec MIME, sample rate, channels) to avoid re-probing.
    private val codecInfoCache = mutableMapOf<String, AudioCodecInfo?>()

    private fun isMimeTypeDecoderSupported(mimeType: String): Boolean {
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder) continue
                val types = runCatching { info.supportedTypes }.getOrNull() ?: continue
                if (types.any { it.equals(mimeType, ignoreCase = true) }) return true
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Returns true if a working MediaCodec decoder for the given ALAC [codecInfo] is available
     * on this device.
     *
     * NOTE: We do NOT filter out QTI/Qualcomm decoders here.  Those decoders have reported
     * instability when used for *device audio playback*, but in our pipeline we only use the
     * decoder as a source (decode → PCM → AAC encode) and never route audio to the output
     * stack.
     */
    fun isAlacTranscodeSupported(codecInfo: AudioCodecInfo): Boolean {
        if (codecInfo.codecMime != "audio/alac") return false
        if (isFfmpegAlacTranscodeSupported()) return true
        return isMimeTypeDecoderSupported("audio/alac")
    }

    /**
     * Returns true if a working MediaCodec decoder for FLAC is available on this device.
     */
    fun isFlacTranscodeSupported(codecInfo: AudioCodecInfo): Boolean {
        if (codecInfo.codecMime != "audio/flac") return false
        return isMimeTypeDecoderSupported("audio/flac")
    }

    /**
     * Returns true if AC3/EAC3 can be transcoded to AAC on this device.
     * Tries FFmpeg first (compiled-in, reliable), then falls back to checking
     * for a MediaCodec AC3 decoder (Qualcomm/Dolby on some Snapdragon devices).
     */
    fun isAc3TranscodeSupported(codecInfo: AudioCodecInfo): Boolean {
        if (codecInfo.codecMime != "audio/ac3" && codecInfo.codecMime != "audio/eac3") return false
        if (isFfmpegAc3TranscodeSupported(codecInfo.codecMime)) return true
        return isMimeTypeDecoderSupported(codecInfo.codecMime)
    }

    /**
     * Detects the actual audio codec inside a container (e.g. audio/alac vs audio/mp4 for ALAC-in-M4A).
     * Results are cached to avoid repeated MediaExtractor operations per song.
     */
    fun detectAudioCodecViaExtractor(song: Song, uri: Uri): AudioCodecInfo? {
        if (codecInfoCache.contains(song.id)) return codecInfoCache[song.id]
        val extractor = MediaExtractor()
        val result = runCatching {
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
            if (!opened) return@runCatching null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                var mime = fmt.getString(MediaFormat.KEY_MIME)?.trim()?.lowercase(Locale.ROOT) ?: continue
                if (!mime.startsWith("audio/")) continue

                // Fix ALAC mislabeled by missing OEM box definitions.
                // EAC3 (audio/eac3) and AC3 (audio/ac3) are common misidentifications for ALAC boxes on some Samsung devices,
                // alongside audio/mp4a-latm.
                if (mime == "audio/mp4a-latm" || mime == "audio/eac3" || mime == "audio/ac3") {
                    val isM4a = song.path.endsWith(".m4a", true)
                    val isExplicitAlacMetadata = song.mimeType?.contains("alac", true) == true
                    // High-bitrate heuristic only applies to audio/mp4a-latm misidentifications.
                    // AC3/EAC3 files can legitimately have high bitrates, so excluding them prevents
                    // genuine Dolby files from being mis-reclassified as ALAC.
                    val hasHighBitrate = mime == "audio/mp4a-latm" &&
                        (runCatching { fmt.getInteger(MediaFormat.KEY_BIT_RATE) }.getOrNull() ?: 0) > 600_000

                    // EAC3/AC3 inside an .m4a is mislabeled ALAC ONLY when CSD-0 is present.
                    // The Samsung OEM bug shows audio/ac3 but the MediaFormat still carries the
                    // ALACSpecificConfig as csd-0. Genuine AC3/EAC3 content has no csd-0 at all.
                    val hasCsd0 = (mime == "audio/eac3" || mime == "audio/ac3") &&
                        runCatching { (fmt.getByteBuffer("csd-0")?.remaining() ?: 0) > 0 }.getOrDefault(false)
                    val isImpossibleCodecInM4a = isM4a && (mime == "audio/eac3" || mime == "audio/ac3") && hasCsd0

                    if (isExplicitAlacMetadata || isImpossibleCodecInM4a || (isM4a && hasHighBitrate)) {
                        mime = "audio/alac"
                    } else if (isM4a) {
                        val mmr = android.media.MediaMetadataRetriever()
                        runCatching {
                            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            }
                            val mmrMime = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.lowercase(Locale.ROOT)
                            if (mmrMime == "audio/alac") {
                                mime = "audio/alac"
                            }
                        }.also { runCatching { mmr.release() } }
                    }
                }

                // MediaExtractor sometimes exposes the decoded PCM track of a FLAC file
                // as "audio/raw" instead of the container format "audio/flac".  Recover
                // the true format from the file extension / mime metadata so the FLAC → AAC
                // transcode path is correctly triggered.
                if (mime == "audio/raw") {
                    val mimeL = song.mimeType?.lowercase(Locale.ROOT)
                    val isFlac = song.path.endsWith(".flac", true) ||
                        mimeL == "audio/flac" || mimeL == "audio/x-flac"
                    if (isFlac) {
                        mime = "audio/flac"
                    }
                }

                val sr = runCatching { fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull() ?: continue
                val ch = runCatching { fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrNull() ?: continue
                return@runCatching AudioCodecInfo(mime, sr, ch, i)
            }
            null
        }.getOrNull().also { runCatching { extractor.release() } }
        codecInfoCache[song.id] = result
        return result
    }
}

@OptIn(UnstableApi::class)
internal fun isFfmpegAlacTranscodeSupported(): Boolean {
    return runCatching {
        FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_ALAC)
    }.getOrDefault(false)
}

@OptIn(UnstableApi::class)
internal fun isFfmpegAc3TranscodeSupported(mimeType: String): Boolean {
    return runCatching {
        FfmpegLibrary.supportsFormat(mimeType)
    }.getOrDefault(false)
}

// isKnownUnstableAlacDecoder is intentionally NOT used in the transcode path.
// It only applies when routing ALAC audio directly to device output (playback),
// which we never do here — we decode to PCM and re-encode to AAC.
