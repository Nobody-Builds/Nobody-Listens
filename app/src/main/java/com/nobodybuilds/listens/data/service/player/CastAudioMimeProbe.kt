package com.nobodybuilds.listens.data.service.player

import android.content.ContentResolver
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.nobodybuilds.listens.data.model.Song
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Works out a song's real audio format for [CastPlayer] (file signature, MediaExtractor,
 * MediaMetadataRetriever) and whether this device can transcode ALAC / FLAC for the receiver.
 */
internal class CastAudioMimeProbe(
    private val contentResolver: ContentResolver?
) {
    private val signatureMimeCache = mutableMapOf<String, String?>()

    fun detectAudioMimeTypeBySignature(song: Song): String? {
        signatureMimeCache[song.id]?.let { return it }
        val bytes = readAudioSignature(song) ?: run {
            signatureMimeCache[song.id] = null
            return null
        }

        val id3PayloadOffset = parseId3PayloadOffset(bytes)
        val detected = detectMimeAtOffset(bytes, id3PayloadOffset)
            ?: detectMimeAtOffset(bytes, 0)
            ?: detectFramedAudioMime(bytes, id3PayloadOffset)
            ?: detectFramedAudioMime(bytes, 0)
        signatureMimeCache[song.id] = detected
        return detected
    }

    private fun readAudioSignature(song: Song, maxBytes: Int = 16 * 1024): ByteArray? {
        val uriBytes = runCatching {
            val uri = song.contentUriString.toUri()
            contentResolver
                ?.openInputStream(uri)
                ?.use { input ->
                    val buffer = ByteArray(maxBytes)
                    val read = input.read(buffer)
                    if (read <= 0) null else buffer.copyOf(read)
                }
        }.getOrNull()
        if (uriBytes != null && uriBytes.isNotEmpty()) {
            return uriBytes
        }

        val file = song.path
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.canRead() }
            ?: return null

        return runCatching {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(maxBytes)
                val read = input.read(buffer)
                if (read <= 0) null else buffer.copyOf(read)
            }
        }.getOrNull()
    }

    private fun parseId3PayloadOffset(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return 0
        }
        val flags = bytes[5].toInt() and 0xFF
        val hasFooter = (flags and 0x10) != 0
        val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        val totalTagBytes = 10 + tagSize + if (hasFooter) 10 else 0
        return totalTagBytes.coerceIn(0, bytes.size)
    }

    private fun detectMimeAtOffset(bytes: ByteArray, offset: Int): String? {
        if (offset < 0 || offset >= bytes.size) return null
        val remaining = bytes.size - offset
        if (remaining >= 4 &&
            bytes[offset] == 'f'.code.toByte() &&
            bytes[offset + 1] == 'L'.code.toByte() &&
            bytes[offset + 2] == 'a'.code.toByte() &&
            bytes[offset + 3] == 'C'.code.toByte()
        ) {
            return "audio/flac"
        }
        if (remaining >= 4 &&
            bytes[offset] == 'O'.code.toByte() &&
            bytes[offset + 1] == 'g'.code.toByte() &&
            bytes[offset + 2] == 'g'.code.toByte() &&
            bytes[offset + 3] == 'S'.code.toByte()
        ) {
            return "audio/ogg"
        }
        if (remaining >= 12 &&
            bytes[offset] == 'R'.code.toByte() &&
            bytes[offset + 1] == 'I'.code.toByte() &&
            bytes[offset + 2] == 'F'.code.toByte() &&
            bytes[offset + 3] == 'F'.code.toByte() &&
            bytes[offset + 8] == 'W'.code.toByte() &&
            bytes[offset + 9] == 'A'.code.toByte() &&
            bytes[offset + 10] == 'V'.code.toByte() &&
            bytes[offset + 11] == 'E'.code.toByte()
        ) {
            return "audio/wav"
        }
        if (remaining >= 12 &&
            bytes[offset] == 'F'.code.toByte() &&
            bytes[offset + 1] == 'O'.code.toByte() &&
            bytes[offset + 2] == 'R'.code.toByte() &&
            bytes[offset + 3] == 'M'.code.toByte() &&
            bytes[offset + 8] == 'A'.code.toByte() &&
            bytes[offset + 9] == 'I'.code.toByte() &&
            bytes[offset + 10] == 'F'.code.toByte() &&
            bytes[offset + 11] == 'F'.code.toByte()
        ) {
            return "audio/aiff"
        }
        // ISO Base Media File Format (MP4/M4A/M4B): check for 'ftyp' box at bytes 4-7.
        // Requires at least offset+8 bytes to safely access offset+4..offset+7.
        if (remaining >= 12 && offset + 8 <= bytes.size &&
            bytes[offset + 4] == 'f'.code.toByte() &&
            bytes[offset + 5] == 't'.code.toByte() &&
            bytes[offset + 6] == 'y'.code.toByte() &&
            bytes[offset + 7] == 'p'.code.toByte()
        ) {
            return "audio/mp4"
        }
        if (remaining >= 4 &&
            bytes[offset] == 'A'.code.toByte() &&
            bytes[offset + 1] == 'D'.code.toByte() &&
            bytes[offset + 2] == 'I'.code.toByte() &&
            bytes[offset + 3] == 'F'.code.toByte()
        ) {
            return "audio/aac"
        }
        return null
    }

    private fun detectFramedAudioMime(bytes: ByteArray, startOffset: Int): String? {
        if (bytes.size < 2) return null
        val start = startOffset.coerceIn(0, bytes.lastIndex)
        for (index in start until bytes.size - 1) {
            val b0 = bytes[index].toInt() and 0xFF
            val b1 = bytes[index + 1].toInt() and 0xFF
            if (b0 != 0xFF || (b1 and 0xF0) != 0xF0) continue
            // MPEG audio headers encode layer != 00. AAC ADTS always uses layer == 00.
            val layerBits = (b1 ushr 1) and 0x03
            if (layerBits == 0) return "audio/aac"
            if (layerBits in 1..3) return "audio/mpeg"
        }
        return null
    }

    fun detectAudioMimeTypeViaExtractor(song: Song): String? {
        val resolver = contentResolver ?: return null
        val uri = song.contentUriString.toUri()
        return runCatching {
            val extractor = MediaExtractor()
            try {
                val sourceAttached = runCatching {
                    resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        val length = afd.length
                            .takeIf { it > 0L }
                            ?: afd.declaredLength.takeIf { it > 0L }
                        if (length != null) {
                            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, length)
                        } else {
                            extractor.setDataSource(afd.fileDescriptor)
                        }
                    } != null
                }.getOrElse { false } || runCatching {
                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                    } != null
                }.getOrElse { false }

                if (!sourceAttached) {
                    return@runCatching null
                }

                for (trackIndex in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(trackIndex)
                    var trackMime = trackFormat.getString(MediaFormat.KEY_MIME)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.lowercase(Locale.ROOT)
                    if (trackMime != null && trackMime.startsWith("audio/")) {
                        if (trackMime == "audio/mp4a-latm" || trackMime == "audio/eac3" || trackMime == "audio/ac3") {
                            val isM4a = song.path.endsWith(".m4a", true)
                            val isExplicitAlacMetadata = song.mimeType?.contains("alac", true) == true
                            val hasHighBitrate = (runCatching { trackFormat.getInteger(MediaFormat.KEY_BIT_RATE) }.getOrNull() ?: 0) > 600_000

                            val isImpossibleCodecInM4a = isM4a && (trackMime == "audio/eac3" || trackMime == "audio/ac3")

                            if (isExplicitAlacMetadata || isImpossibleCodecInM4a || (isM4a && hasHighBitrate)) {
                                trackMime = "audio/alac"
                            } else if (isM4a) {
                                val mmr = MediaMetadataRetriever()
                                runCatching {
                                    resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                        mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                    }
                                    val mmrMime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.lowercase(Locale.ROOT)
                                    if (mmrMime == "audio/alac") {
                                        trackMime = "audio/alac"
                                    }
                                }.also { runCatching { mmr.release() } }
                            }
                        }
                        // MediaExtractor sometimes exposes the decoded PCM track of a FLAC file
                        // as "audio/raw" instead of the container format "audio/flac".  Recover the
                        // true format from the file extension / mime metadata so the FLAC → AAC
                        // transcode path in the caller is correctly triggered.
                        if (trackMime == "audio/raw") {
                            val mimeL = song.mimeType?.lowercase(Locale.ROOT)
                            val isFlac = song.path.endsWith(".flac", true) ||
                                mimeL == "audio/flac" || mimeL == "audio/x-flac"
                            if (isFlac) {
                                trackMime = "audio/flac"
                            }
                        }
                        return@runCatching trackMime
                    }
                }
                null
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrNull()
    }

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
     * Returns true if an ALAC MediaCodec decoder that supports this song's exact format
     * (sample rate + channel count) is available on this device.
     *
     * NOTE: QTI/Qualcomm ALAC decoders are NOT excluded here. Those decoders are only
     * problematic for direct audio playback routing; the HTTP server uses them solely as
     * a decode source (decode → PCM → AAC), so they are safe for transcoding.  Excluding
     * them here would cause CastPlayer to announce audio/mp4 while the server transcodes
     * to audio/aac, producing a MIME mismatch that makes Cast reject the item.
     */
    /**
     * Checks if a working ALAC decoder is available.
     * At this point, CastPlayer has already determined the track is ALAC via probe (detectAudioMimeTypeViaExtractor),
     * so it just needs to confirm decoder existence.
     */
    fun isAlacTranscodeSupported(): Boolean {
        if (isFfmpegAlacTranscodeSupported()) return true
        return isMimeTypeDecoderSupported("audio/alac")
    }

    /**
     * Checks if a working FLAC decoder is available.
     */
    fun isFlacTranscodeSupported(): Boolean {
        return isMimeTypeDecoderSupported("audio/flac")
    }

    private fun isFfmpegAlacTranscodeSupported(): Boolean {
        return runCatching {
            FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_ALAC)
        }.getOrDefault(false)
    }

    fun detectAudioMimeTypeViaMetadataRetriever(song: Song): String? {
        val resolver = contentResolver ?: return null
        val uri = song.contentUriString.toUri()
        val retriever = MediaMetadataRetriever()
        return runCatching {
            val sourceConfigured = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val length = afd.length
                        .takeIf { it > 0L }
                        ?: afd.declaredLength.takeIf { it > 0L }
                    if (length != null) {
                        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, length)
                    } else {
                        retriever.setDataSource(afd.fileDescriptor)
                    }
                } != null
            }.getOrElse { false } || runCatching {
                retriever.setDataSource(song.path)
                true
            }.getOrElse { false }

            if (!sourceConfigured) {
                return@runCatching null
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.lowercase(Locale.ROOT)
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }
}

/** Maps a MIME type to the form the Cast receiver accepts, or null when Cast cannot play it. */
internal fun String.toCastSupportedMimeTypeOrNull(): String? {
    val normalized = this.trim().substringBefore(';').lowercase(Locale.ROOT)
    return when (normalized) {
        "audio/mpeg",
        "audio/mp3",
        "audio/mpeg3",
        "audio/x-mpeg" -> "audio/mpeg"

        "audio/flac",
        "audio/x-flac" -> "audio/flac"

        "audio/aac",
        "audio/aacp",
        "audio/adts",
        "audio/vnd.dlna.adts",
        "audio/mp4a-latm",
        "audio/aac-latm",
        "audio/x-aac",
        "audio/x-hx-aac-adts",
        // ALAC codec in M4A container: server transcodes to AAC ADTS, so announce as audio/aac
        "audio/alac" -> "audio/aac"

        "audio/mp4",
        "audio/x-m4a",
        "audio/m4a",
        "audio/3gpp",
        "audio/3gp" -> "audio/mp4"

        "audio/wav",
        "audio/x-wav",
        "audio/wave" -> "audio/wav"

        "audio/ogg",
        "audio/oga",
        "audio/opus",
        "application/ogg" -> "audio/ogg"

        "audio/webm" -> "audio/webm"

        "audio/amr",
        "audio/amr-wb",
        "audio/l16",
        "audio/l24" -> normalized

        // AIFF is not natively supported by Cast. Map to audio/mpeg as best-effort fallback.
        "audio/aiff",
        "audio/x-aiff",
        "audio/aif" -> "audio/mpeg"

        else -> null
    }
}
