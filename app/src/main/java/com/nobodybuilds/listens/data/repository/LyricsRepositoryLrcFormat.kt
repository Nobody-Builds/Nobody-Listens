package com.nobodybuilds.listens.data.repository

import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.SyncedLine

/** LRC text helpers shared by the lyrics repository, its JSON cache and the AMLLDB converter. */
internal object LyricsRepositoryLrcFormat {

    fun convertAmlTtmlToLrc(ttml: String): String? {
        val lineRegex = Regex(
            "<p\\b[^>]*\\bbegin=\"([^\"]+)\"[^>]*>(.*?)</p>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val spanRegex = Regex(
            "<span\\b([^>]*)>(.*?)</span>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val beginAttrRegex = Regex("\\bbegin=\"([^\"]+)\"")
        val roleAttrRegex = Regex("\\bttm:role=\"([^\"]+)\"")

        val lrcLines = mutableListOf<String>()
        lineRegex.findAll(ttml).forEach { lineMatch ->
            val lineStartMs = parseTtmlTimeToMs(lineMatch.groupValues[1]) ?: return@forEach
            var inner = lineMatch.groupValues[2]
            val markerRegex = Regex("§§TS\\(([^)]+)\\)§§")

            inner = spanRegex.replace(inner) { spanMatch ->
                val attributes = spanMatch.groupValues[1]
                val role = roleAttrRegex.find(attributes)?.groupValues?.getOrNull(1)?.lowercase()
                if (role == "x-roman") {
                    return@replace ""
                }
                val wordStartMs = beginAttrRegex
                    .find(attributes)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parseTtmlTimeToMs)
                val text = decodeXmlEntities(spanMatch.groupValues[2])

                if (wordStartMs == null) {
                    // Keep visible text (e.g. translation) but do not inject word timing.
                    return@replace text
                }

                "§§TS(${formatTimestamp(wordStartMs.toInt())})§§$text"
            }

            val withoutXmlTags = decodeXmlEntities(inner.replace(Regex("<[^>]+>"), ""))
            val lrcInlineTagged = markerRegex.replace(withoutXmlTags, "<$1>")
            if (lrcInlineTagged.isBlank()) return@forEach

            lrcLines += "[${formatTimestamp(lineStartMs.toInt())}]$lrcInlineTagged"
        }

        return lrcLines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun parseTtmlTimeToMs(value: String): Long? {
        val raw = value.trim()
        if (raw.isEmpty()) return null

        if (raw.endsWith("s")) {
            val seconds = raw.removeSuffix("s").toDoubleOrNull() ?: return null
            return (seconds * 1000.0).toLong()
        }

        val parts = raw.split(":")
        val secondsPart = parts.lastOrNull()?.toDoubleOrNull() ?: return null
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                (hours * 3_600_000L) + (minutes * 60_000L) + (secondsPart * 1000.0).toLong()
            }
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                (minutes * 60_000L) + (secondsPart * 1000.0).toLong()
            }
            1 -> (secondsPart * 1000.0).toLong()
            else -> null
        }
    }

    private fun decodeXmlEntities(value: String): String =
        value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")

    fun formatTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    fun toWordByWordLrc(lines: List<SyncedLine>): String {
        return lines.joinToString("\n") { line ->
            val linePrefix = "[${formatTimestamp(line.time)}]"
            val words = line.words
            if (words.isNullOrEmpty()) {
                linePrefix + line.line
            } else {
                val wordsPart = words.mapIndexed { index, word ->
                    val separator = if (index > 0 && word.startsNewWord) " " else ""
                    "$separator<${formatTimestamp(word.time)}>${word.word}"
                }
                    .joinToString("")
                linePrefix + wordsPart
            }
        }
    }

    fun looksLikeFlattenedWordByWordCache(lyrics: Lyrics): Boolean {
        val synced = lyrics.synced ?: return false
        var suspiciousLines = 0

        for (line in synced) {
            val text = line.line
            if (text.isBlank() || text.any { it.isWhitespace() }) continue

            val hasLongLatinRun = Regex("[A-Za-z]{10,}").containsMatchIn(text)
            if (hasLongLatinRun) {
                suspiciousLines += 1
                if (suspiciousLines >= 2) return true
            }
        }

        return false
    }

    fun lyricsToRawContent(lyrics: Lyrics): String? {
        val syncedLyrics = lyrics.synced
        if (!syncedLyrics.isNullOrEmpty()) {
            val hasWordTimestamps = syncedLyrics.any { !it.words.isNullOrEmpty() }
            return if (hasWordTimestamps) {
                toWordByWordLrc(syncedLyrics)
            } else {
                syncedLyrics.joinToString("\n") { line ->
                    "[${formatTimestamp(line.time)}]${line.line}"
                }
            }
        }

        return lyrics.plain
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }
    }
}
