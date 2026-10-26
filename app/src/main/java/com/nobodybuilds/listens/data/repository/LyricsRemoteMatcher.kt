package com.nobodybuilds.listens.data.repository

import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.network.lyrics.LrcLibResponse
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal enum class RemoteLyricsMatchMode {
    AUTOMATIC,
    CANDIDATE
}

internal data class RemoteLyricsMatch(
    val response: LrcLibResponse,
    val score: Int
)

/** Scores LRCLIB results against a song: title, artist, duration and remix/live variants. */
internal object LyricsRemoteMatcher {

    private val BRACKETED_QUALIFIER_REGEX = Regex("""[\(\[\{]([^)\]\}]*)[\)\]\}]""")
    private val FEATURE_QUALIFIER_REGEX = Regex("""\b(feat(?:uring)?|ft)\.?\b""", RegexOption.IGNORE_CASE)
    private val TITLE_SEPARATOR_REGEX = Regex("""\s+[-\u2013\u2014:]\s+""")
    private val TIMING_VARIANT_KEYWORDS = setOf(
        "remix",
        "mix",
        "mashup",
        "bootleg",
        "edit",
        "extended",
        "radio",
        "club",
        "vip",
        "dub",
        "live",
        "acoustic",
        "unplugged",
        "sped",
        "slowed",
        "nightcore",
        "instrumental",
        "karaoke",
        "cover",
        "demo",
        "version",
        "rework",
        "flip",
        "refix"
    )
    private val TITLE_DROP_QUALIFIERS = setOf(
        "explicit",
        "clean",
        "mono",
        "stereo",
        "official audio",
        "official video"
    )
    private val UNKNOWN_ARTISTS = setOf(
        "",
        "<unknown>",
        "unknown",
        "unknown artist",
        "various artists",
        "various"
    )
    private val ARTIST_CONNECTOR_TOKENS = setOf(
        "feat",
        "featuring",
        "ft",
        "and",
        "with",
        "x",
        "vs",
        "the"
    )

    private fun hasLyrics(response: LrcLibResponse): Boolean =
        !response.plainLyrics.isNullOrBlank() || !response.syncedLyrics.isNullOrBlank()

    private fun hasSyncedLyrics(response: LrcLibResponse): Boolean =
        !response.syncedLyrics.isNullOrBlank()

    fun rankRemoteLyricsMatches(
        song: Song,
        responses: List<LrcLibResponse>,
        mode: RemoteLyricsMatchMode
    ): List<RemoteLyricsMatch> {
        val songDurationSeconds = song.duration / 1000.0
        if (songDurationSeconds <= 0.0) return emptyList()

        return responses
            .mapNotNull { response ->
                val score = remoteLyricsMatchScore(
                    song = song,
                    response = response,
                    mode = mode,
                    songDurationSeconds = songDurationSeconds
                ) ?: return@mapNotNull null
                RemoteLyricsMatch(response, score)
            }
            .sortedWith(
                compareByDescending<RemoteLyricsMatch> { it.score }
                    .thenByDescending { hasSyncedLyrics(it.response) }
                    .thenBy { abs(it.response.duration - songDurationSeconds) }
            )
    }

    private fun remoteLyricsMatchScore(
        song: Song,
        response: LrcLibResponse,
        mode: RemoteLyricsMatchMode,
        songDurationSeconds: Double
    ): Int? {
        if (!hasLyrics(response) || response.duration <= 0.0) return null
        if (!variantDescriptorsCompatible(song, response)) return null

        val hasSynced = hasSyncedLyrics(response)
        val durationTolerance = remoteDurationToleranceSeconds(songDurationSeconds, hasSynced, mode)
        val durationDiff = abs(response.duration - songDurationSeconds)
        if (durationDiff > durationTolerance) return null

        val titleScore = titleMatchScore(song.title, response.name, mode) ?: return null
        val artistScore = artistMatchScore(song.displayArtist, response.artistName)
        if (!isUnknownArtist(song.displayArtist) && artistScore == null) return null

        val durationScore = (durationTolerance - durationDiff).coerceAtLeast(0.0).toInt()
        val syncedScore = if (hasSynced) 10 else 0
        return titleScore + (artistScore ?: 0) + durationScore + syncedScore
    }

    private fun remoteDurationToleranceSeconds(
        songDurationSeconds: Double,
        hasSyncedLyrics: Boolean,
        mode: RemoteLyricsMatchMode
    ): Double {
        return when (mode) {
            RemoteLyricsMatchMode.AUTOMATIC -> {
                if (hasSyncedLyrics) {
                    (songDurationSeconds * 0.02).coerceIn(5.0, 8.0)
                } else {
                    (songDurationSeconds * 0.04).coerceIn(8.0, 15.0)
                }
            }
            RemoteLyricsMatchMode.CANDIDATE -> 15.0
        }
    }

    private fun titleMatchScore(songTitle: String, responseTitle: String, mode: RemoteLyricsMatchMode): Int? {
        val songBase = baseTitleForMatching(songTitle)
        val responseBase = baseTitleForMatching(responseTitle)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 70

        val songTokens = matchTokens(songBase)
        val responseTokens = matchTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        if (songTokens.size == 1 || responseTokens.size == 1) {
            return if (songTokens == responseTokens) 60 else null
        }

        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return if (mode == RemoteLyricsMatchMode.AUTOMATIC) 58 else 54
        }

        val overlap = songTokens.intersect(responseTokens).size
        val songCoverage = overlap.toDouble() / songTokens.size
        val responseCoverage = overlap.toDouble() / responseTokens.size
        val requiredSongCoverage = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 0.85 else 0.75
        val requiredResponseCoverage = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 0.70 else 0.55

        return if (songCoverage >= requiredSongCoverage && responseCoverage >= requiredResponseCoverage) {
            45
        } else {
            null
        }
    }

    private fun artistMatchScore(songArtist: String, responseArtist: String): Int? {
        if (isUnknownArtist(songArtist)) return 0

        val songBase = normalizeForMatch(songArtist)
        val responseBase = normalizeForMatch(responseArtist)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 30
        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return 22
        }

        val songTokens = artistTokens(songBase)
        val responseTokens = artistTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        val overlap = songTokens.intersect(responseTokens).size
        val smallerArtistCoverage = overlap.toDouble() / minOf(songTokens.size, responseTokens.size)
        return if (smallerArtistCoverage >= 0.5) 12 else null
    }

    private fun variantDescriptorsCompatible(song: Song, response: LrcLibResponse): Boolean {
        val songVariants = timingVariantTokens(song.title) + timingVariantTokensFromFileName(song)
        val responseVariants = timingVariantTokens(response.name)

        if (songVariants.isEmpty()) {
            return responseVariants.isEmpty()
        }

        return responseVariants == songVariants
    }

    private fun baseTitleForMatching(title: String): String {
        var base = title.replace(Regex("""^\s*\d{1,3}\s*[\._-]\s+"""), "")

        base = BRACKETED_QUALIFIER_REGEX.replace(base) { match ->
            val qualifier = match.groupValues.getOrNull(1).orEmpty()
            if (shouldDropTitleQualifier(qualifier)) " " else " $qualifier "
        }

        var parts = TITLE_SEPARATOR_REGEX.split(base)
        while (parts.size > 1 && shouldDropTitleQualifier(parts.last())) {
            parts = parts.dropLast(1)
        }

        return normalizeForMatch(parts.joinToString(" "))
    }

    private fun shouldDropTitleQualifier(value: String): Boolean {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return true
        return FEATURE_QUALIFIER_REGEX.containsMatchIn(value) ||
            timingVariantTokens(value).isNotEmpty() ||
            normalized in TITLE_DROP_QUALIFIERS
    }

    private fun timingVariantTokens(value: String): Set<String> {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return emptySet()

        val tokens = matchTokens(normalized)
        val variants = tokens
            .filter { it in TIMING_VARIANT_KEYWORDS }
            .toMutableSet()

        if (Regex("""\bmash\s+up\b""").containsMatchIn(normalized)) {
            variants += "mashup"
        }
        if ("versus" in tokens || "vs" in tokens) {
            variants += "mashup"
        }

        return variants
    }

    private fun timingVariantTokensFromFileName(song: Song): Set<String> {
        val fileName = songFileName(song)
        if (fileName.isBlank()) return emptySet()

        val variants = BRACKETED_QUALIFIER_REGEX
            .findAll(fileName)
            .flatMap { match -> timingVariantTokens(match.groupValues.getOrNull(1).orEmpty()) }
            .toMutableSet()

        val titleBase = baseTitleForMatching(song.title)
        if (titleBase.isBlank()) return variants

        TITLE_SEPARATOR_REGEX.split(fileName).forEach { part ->
            val normalizedPart = normalizeForMatch(part)
            if (normalizedPart.startsWith("$titleBase ")) {
                variants += timingVariantTokens(normalizedPart.removePrefix(titleBase).trim())
            }
        }

        return variants
    }

    private fun songFileName(song: Song): String {
        if (song.path.isBlank()) return ""
        return runCatching { File(song.path).nameWithoutExtension }.getOrDefault("")
    }

    private fun artistTokens(normalizedArtist: String): Set<String> =
        matchTokens(normalizedArtist)
            .filterNot { it in ARTIST_CONNECTOR_TOKENS }
            .toSet()

    private fun matchTokens(normalizedValue: String): Set<String> =
        normalizedValue
            .split(' ')
            .filter { it.isNotBlank() }
            .toSet()

    private fun containsWholePhrase(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        return Regex("""(?:^|\s)${Regex.escape(needle)}(?:\s|$)""").containsMatchIn(haystack)
    }

    private fun normalizeForMatch(value: String): String {
        val withoutDiacritics = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

        return withoutDiacritics
            .replace("&", " and ")
            .replace(Regex("""[\u2019'`]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun isUnknownArtist(value: String): Boolean =
        normalizeForMatch(value) in UNKNOWN_ARTISTS

    fun cleanTitleSmart(title: String): String {
        // 1. Remove leading digits/spaces/dots/hyphens (e.g., "01 ", "01. ", "01 - ")
        var cleaned = title.replace(Regex("^[\\d\\s.\\-]+"), "")
        
        // 2. Truncate at first special char (-, (, ))
        // "Taare Ginn - Envy" -> "Taare Ginn "
        // "Song (Feat. X)" -> "Song "
        val splitRegex = Regex("[-()]")
        cleaned = cleaned.split(splitRegex).firstOrNull() ?: cleaned
        
        // 3. Trim whitespace
        return cleaned.trim()
    }
}
