package com.nobodybuilds.listens.data.ytmusic


object YouTubeMusicFormats {
    const val HIGHEST_TARGET_BITRATE = 320_000
    const val OGG_OPUS_MIME = "audio/ogg; codecs=\"opus\""

    /**
     * Cover-sized art: square album art at 800px. Video thumbnails stay as given; the
     * HD one is found when the song is saved (see [YouTubeMusicThumbnails]).
     */
    fun largeThumbnail(url: String?): String? = YouTubeMusicThumbnails.albumArt(url, COVER_SIZE)

    private const val COVER_SIZE = 800

    fun selectHighest(
        formats: List<YouTubeMusicAudioFormat>,
        expectedDurationMs: Long? = null,
    ): YouTubeMusicAudioFormat? = ranked(formats, expectedDurationMs).firstOrNull()

    /**
     * Best formats first, up to [maxBitrate]. [opusOnly] keeps downloads in Opus,
     * which is remuxed into a tagged .opus file.
     */
    fun ranked(
        formats: List<YouTubeMusicAudioFormat>,
        expectedDurationMs: Long? = null,
        maxBitrate: Int = HIGHEST_TARGET_BITRATE,
        opusOnly: Boolean = false,
    ): List<YouTubeMusicAudioFormat> {
        val audio = formats.filter { format ->
            format.bitrate > 0 &&
                format.mimeType.startsWith("audio/") &&
                (format.url != null || format.signatureCipher != null) &&
                !isPreview(format, expectedDurationMs) &&
                (!opusOnly || codecName(format.mimeType).contains("opus"))
        }
        if (audio.isEmpty()) return emptyList()

        val atOrUnder = audio.filter { it.bitrate <= maxBitrate }
        val pool = atOrUnder.ifEmpty { audio }
        val comparator = if (atOrUnder.isNotEmpty()) {
            compareByDescending<YouTubeMusicAudioFormat> { resolveUrl(it) != null }
                .thenByDescending { it.bitrate }
                .thenByDescending { codecRank(it.mimeType) }
                .thenByDescending { it.sampleRate ?: 0 }
        } else {
            compareByDescending<YouTubeMusicAudioFormat> { resolveUrl(it) != null }
                .thenBy { it.bitrate }
                .thenByDescending { codecRank(it.mimeType) }
                .thenByDescending { it.sampleRate ?: 0 }
        }
        return pool.sortedWith(comparator)
    }

    /**
     * Uses a direct URL. Ciphered URLs need a signature solver, so they are skipped
     * and the next client is tried.
     */
    fun resolveUrl(format: YouTubeMusicAudioFormat): String? {
        val direct = format.url?.takeIf { it.startsWith("https://") }
        if (direct != null) return direct
        val cipher = format.signatureCipher ?: return null
        if (queryParam(cipher, "s") != null) return null
        return queryParam(cipher, "url")?.takeIf { it.startsWith("https://") }
    }

    fun fileExtension(mimeType: String): String {
        val codec = codecName(mimeType)
        val base = mimeType.substringBefore(";").substringAfter("/").lowercase()
        return when {
            base == "ogg" -> "opus"
            codec.contains("opus") || base == "webm" -> "webm"
            codec.contains("mp4a") || base == "mp4" || base == "m4a" -> "m4a"
            base.isNotBlank() -> base
            else -> "audio"
        }
    }

    fun fileMimeType(mimeType: String): String {
        val extension = fileExtension(mimeType)
        return when (extension) {
            "opus" -> "audio/ogg"
            "webm" -> "audio/webm"
            "m4a" -> "audio/mp4"
            else -> mimeType.substringBefore(";").ifBlank { "application/octet-stream" }
        }
    }

    fun downloadSpec(title: String, artist: String, mimeType: String): YouTubeMusicDownloadSpec {
        val extension = fileExtension(mimeType)
        val safeTitle = sanitize(title).ifBlank { "YouTube Music" }
        val safeArtist = sanitize(artist)
        val stem = if (safeArtist.isBlank()) safeTitle else "$safeArtist - $safeTitle"
        return YouTubeMusicDownloadSpec(
            fileName = "$stem.$extension",
            mimeType = fileMimeType(mimeType),
            extension = extension,
        )
    }

    fun isValidVideoId(videoId: String): Boolean = VIDEO_ID.matches(videoId)

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    /** Byte offset that shows whether a URL works past YouTube's unauthenticated first megabyte. */
    fun probeOffset(contentLength: Long?): Long {
        val length = contentLength ?: return 0L
        return if (length > PROBE_OFFSET + 2) PROBE_OFFSET else 0L
    }

    private const val PROBE_OFFSET = 1_572_864L

    private fun isPreview(format: YouTubeMusicAudioFormat, expectedDurationMs: Long?): Boolean {
        val expected = expectedDurationMs ?: return false
        val approx = format.approxDurationMs ?: return false
        if (expected < 90_000L) return false
        return approx in 1L..(minOf(90_000L, (expected * 9L) / 10L))
    }

    private fun codecRank(mimeType: String): Int {
        val codec = codecName(mimeType)
        return when {
            codec.contains("opus") -> 3
            codec.contains("mp4a") -> 2
            else -> 1
        }
    }

    private fun codecName(mimeType: String): String {
        val match = Regex("""codecs="([^"]+)"""").find(mimeType) ?: return ""
        return match.groupValues.getOrNull(1)?.substringBefore(",")?.trim()?.lowercase().orEmpty()
    }

    private fun sanitize(value: String): String =
        value.replace('"', '\'')
            .replace(Regex("""[\\/:*?<>|]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)

    private fun queryParam(query: String, name: String): String? {
        return query.split("&").firstNotNullOfOrNull { part ->
            val split = part.indexOf('=')
            if (split <= 0) return@firstNotNullOfOrNull null
            val key = part.substring(0, split)
            if (key != name) return@firstNotNullOfOrNull null
            java.net.URLDecoder.decode(part.substring(split + 1), Charsets.UTF_8.name())
        }
    }
}
