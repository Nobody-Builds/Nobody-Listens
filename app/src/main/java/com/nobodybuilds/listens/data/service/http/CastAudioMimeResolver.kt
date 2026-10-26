package com.nobodybuilds.listens.data.service.http

import android.content.ContentResolver
import android.net.Uri
import com.nobodybuilds.listens.data.model.Song
import io.ktor.http.ContentType
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/** Works out which audio MIME type to send Cast for a song. */
internal class CastAudioMimeResolver(
    private val contentResolver: ContentResolver
) {
    private val signatureMimeCache = mutableMapOf<String, String?>()

    fun resolvePreferredAudioMimeType(song: Song, uri: Uri): String? {
        val providerMimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
        val normalizedFallback = listOfNotNull(song.mimeType, providerMimeType, resolveAudioMimeTypeFromPath(song.path))
            .firstNotNullOfOrNull { normalizeCastAudioMimeType(it) }

        // Container formats identified by metadata/extension are reliable — do not override them
        // with signature detection, which can produce false positives (e.g. 0xFF sync words inside
        // an MP4 moov atom are misread as AAC/MPEG framing). Only use signature to upgrade truly
        // ambiguous metadata types (audio/mpeg, audio/aac) or when metadata is absent.
        val isContainerFormat = normalizedFallback != null &&
            normalizedFallback != "audio/mpeg" &&
            normalizedFallback != "audio/aac"

        if (isContainerFormat) {
            // Metadata says it's a container (mp4, flac, wav, ogg, webm…). Trust it.
            // Only override if signature found a *different* container magic at file level.
            val signatureMimeType = detectAudioMimeTypeBySignature(song, uri)
            val signatureIsContainer = signatureMimeType != null &&
                signatureMimeType != "audio/mpeg" &&
                signatureMimeType != "audio/aac"
            if (signatureIsContainer && signatureMimeType != normalizedFallback) {
                Timber.tag("PX_CAST_HTTP")
                    .w("MIME container-mismatch songId=${song.id} meta=$normalizedFallback signature=$signatureMimeType — trusting meta")
            }
            // Always prefer the container type from metadata over signature for container formats.
            return normalizedFallback
        }

        // Metadata is ambiguous (audio/mpeg or audio/aac) or absent — use signature to resolve.
        val signatureMimeType = detectAudioMimeTypeBySignature(song, uri)
        if (signatureMimeType != null && signatureMimeType != normalizedFallback) {
            Timber.tag("PX_CAST_HTTP")
                .w("MIME mismatch songId=${song.id} fallback=$normalizedFallback signature=$signatureMimeType — using signature")
        }
        val fallbackMimeType = song.mimeType ?: providerMimeType ?: resolveAudioMimeTypeFromPath(song.path)
        return signatureMimeType ?: normalizedFallback ?: fallbackMimeType
    }

    private fun normalizeCastAudioMimeType(rawMimeType: String): String? {
        val normalized = rawMimeType
            .trim()
            .substringBefore(';')
            .lowercase(Locale.ROOT)
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
            // ALAC codec in M4A: server transcodes to AAC ADTS, normalize as audio/aac
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

    private fun detectAudioMimeTypeBySignature(song: Song, uri: Uri): String? {
        signatureMimeCache[song.id]?.let { return it }
        val bytes = readAudioSignature(song = song, uri = uri) ?: run {
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

    private fun readAudioSignature(song: Song, uri: Uri, maxBytes: Int = 16 * 1024): ByteArray? {
        val uriBytes = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
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
            ?.takeIf { it.exists() && it.isFile && it.canRead() }
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
            val layerBits = (b1 ushr 1) and 0x03
            if (layerBits == 0) return "audio/aac"
            if (layerBits in 1..3) return "audio/mpeg"
        }
        return null
    }

    fun resolveAudioContentType(mimeType: String?): ContentType {
        val normalized = mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)
            ?: return ContentType.Audio.MPEG

        return runCatching { ContentType.parse(normalized) }
            .getOrElse { ContentType.Audio.MPEG }
    }

    private fun resolveAudioMimeTypeFromPath(path: String?): String? {
        val extension = path
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return when (extension) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a", "m4b", "m4p", "mp4", "3gp", "3gpp", "3ga" -> "audio/mp4"
            "wav" -> "audio/wav"
            "aif", "aiff", "aifc" -> "audio/aiff"
            "ogg", "oga", "opus" -> "audio/ogg"
            "weba" -> "audio/webm"
            "wma" -> "audio/x-ms-wma"
            else -> null
        }
    }
}
