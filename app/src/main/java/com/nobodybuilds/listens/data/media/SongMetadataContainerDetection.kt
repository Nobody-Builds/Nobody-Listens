package com.nobodybuilds.listens.data.media

import timber.log.Timber
import java.io.File
import java.util.Locale

private const val TAG = "SongMetadataEditor"

/**
 * FLAC files with high sample rates (>96kHz) or bit depths (>24bit) can cause issues with TagLib.
 * This function detects such files and logs warnings.
 */
internal fun isProblematicFlacFile(filePath: String): FlacAnalysisResult {
    val extension = filePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension != "flac") {
        return FlacAnalysisResult.NotFlac
    }
    
    // Try to read FLAC header to detect sample rate and bit depth
    return try {
        val file = File(filePath)
        file.inputStream().use { inputStream ->
            val header = ByteArray(42)
            val bytesRead = inputStream.read(header)
            
            if (bytesRead < 42) {
                return FlacAnalysisResult.NotFlac
            }
            
            // Check FLAC signature "fLaC"
            if (header[0].toInt().toChar() != 'f' ||
                header[1].toInt().toChar() != 'L' ||
                header[2].toInt().toChar() != 'a' ||
                header[3].toInt().toChar() != 'C'
            ) {
                return FlacAnalysisResult.NotFlac
            }
            
            // STREAMINFO starts at byte 8 (after 4 byte magic + 4 byte block header)
            // Sample rate is at bytes 18-20 (bits 0-19 of STREAMINFO)
            // Bit depth is in byte 20-21
            val sampleRate = ((header[18].toInt() and 0xFF) shl 12) or
                ((header[19].toInt() and 0xFF) shl 4) or
                ((header[20].toInt() and 0xF0) shr 4)
            
            val bitsPerSample = (((header[20].toInt() and 0x01) shl 4) or
                ((header[21].toInt() and 0xF0) shr 4)) + 1

            Timber.tag(TAG)
                .d("FLAC analysis: sampleRate=$sampleRate, bitsPerSample=$bitsPerSample")
            
            // Consider problematic if sample rate > 96kHz or bit depth > 24
            val isProblematic = sampleRate > 96000 || bitsPerSample > 24
            
            if (isProblematic) {
                Timber.tag(TAG)
                    .w("FLAC file may be problematic: $filePath (${sampleRate}Hz, ${bitsPerSample}bit)")
                FlacAnalysisResult.Problematic(sampleRate, bitsPerSample)
            } else {
                FlacAnalysisResult.Safe(sampleRate, bitsPerSample)
            }
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Could not analyze FLAC file: $filePath")
        // If we can't analyze, assume it might be problematic
        FlacAnalysisResult.Unknown
    }
}

internal sealed class FlacAnalysisResult {
    object NotFlac : FlacAnalysisResult()
    data class Safe(val sampleRate: Int, val bitsPerSample: Int) : FlacAnalysisResult()
    data class Problematic(val sampleRate: Int, val bitsPerSample: Int) : FlacAnalysisResult()
    object Unknown : FlacAnalysisResult()
}

internal enum class DetectedContainer(val canonicalExtension: String) {
    MP3("mp3"),
    MP4("m4a"),
    FLAC("flac"),
    OGG_OPUS("opus"),
    OGG_VORBIS("ogg"),
    OGG("ogg"),
    WAV("wav"),
    UNKNOWN("")
}

/**
 * Detects the actual audio container by reading the file's magic bytes.
 * Many files in the wild have wrong extensions (e.g. MP4/M4A served as .mp3 by YouTube rippers
 * or Telegram). Writing ID3v2 tags to an MP4 container corrupts it irreversibly, so the
 * tag-writing pipeline must route by real content, not by extension.
 */
internal fun detectContainerFormat(filePath: String): DetectedContainer {
    return try {
        File(filePath).inputStream().use { input ->
            val header = ByteArray(512)
            var bytesRead = 0
            while (bytesRead < header.size) {
                val read = input.read(header, bytesRead, header.size - bytesRead)
                if (read <= 0) break
                bytesRead += read
            }
            if (bytesRead < 4) return DetectedContainer.UNKNOWN
            when {
                // "ID3" marker → MP3 with ID3v2 tag
                header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte() -> DetectedContainer.MP3
                // MP3 frame sync (0xFFE... 11-bit sync word)
                header[0] == 0xFF.toByte() &&
                    (header[1].toInt() and 0xE0) == 0xE0.toInt() -> DetectedContainer.MP3
                // "ftyp" at offset 4 → ISO BMFF (MP4/M4A)
                bytesRead >= 8 &&
                    header[4] == 'f'.code.toByte() &&
                    header[5] == 't'.code.toByte() &&
                    header[6] == 'y'.code.toByte() &&
                    header[7] == 'p'.code.toByte() -> DetectedContainer.MP4
                // "fLaC"
                header[0] == 'f'.code.toByte() &&
                    header[1] == 'L'.code.toByte() &&
                    header[2] == 'a'.code.toByte() &&
                    header[3] == 'C'.code.toByte() -> DetectedContainer.FLAC
                // "OggS"
                header[0] == 'O'.code.toByte() &&
                    header[1] == 'g'.code.toByte() &&
                    header[2] == 'g'.code.toByte() &&
                    header[3] == 'S'.code.toByte() -> detectOggContainer(header, bytesRead)
                // "RIFF" + "WAVE"
                bytesRead >= 12 &&
                    header[0] == 'R'.code.toByte() &&
                    header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() &&
                    header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() &&
                    header[9] == 'A'.code.toByte() &&
                    header[10] == 'V'.code.toByte() &&
                    header[11] == 'E'.code.toByte() -> DetectedContainer.WAV
                else -> DetectedContainer.UNKNOWN
            }
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Container detection failed for $filePath")
        DetectedContainer.UNKNOWN
    }
}

private fun detectOggContainer(header: ByteArray, bytesRead: Int): DetectedContainer {
    if (bytesRead < 28) return DetectedContainer.OGG

    val segmentCount = header[26].toInt() and 0xFF
    val bodyOffset = 27 + segmentCount
    if (bodyOffset >= bytesRead) return DetectedContainer.OGG

    return when {
        header.matchesAscii(bodyOffset, "OpusHead", bytesRead) -> DetectedContainer.OGG_OPUS
        header[bodyOffset] == 0x01.toByte() &&
            header.matchesAscii(bodyOffset + 1, "vorbis", bytesRead) -> DetectedContainer.OGG_VORBIS
        else -> DetectedContainer.OGG
    }
}

private fun ByteArray.matchesAscii(offset: Int, value: String, bytesRead: Int): Boolean {
    if (offset < 0 || offset + value.length > bytesRead) return false
    for (index in value.indices) {
        if (this[offset + index] != value[index].code.toByte()) return false
    }
    return true
}
