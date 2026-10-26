package com.nobodybuilds.listens.utils

import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.SyncedLine
import com.nobodybuilds.listens.data.model.SyncedWord
import java.lang.Character
import java.util.Locale
import java.util.regex.Pattern

private fun String.capitalizeFirstLetter(): String {
    if (this.isEmpty()) return this
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

object LyricsUtils {

    private val LRC_LINE_REGEX = Pattern.compile("^\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})](.*)$")
    private val LRC_WORD_REGEX = Pattern.compile("<(\\d{2}):(\\d{2})[.:](\\d{2,3})>([^<]*)")
    private val LRC_WORD_TAG_REGEX = Regex("<\\d{2}:\\d{2}[.:]\\d{2,3}>")
    private val LRC_WORD_SPLIT_REGEX = Regex("(?=<\\d{2}:\\d{2}[.:]\\d{2,3}>)")
    private val LRC_TIMESTAMP_TAG_REGEX = Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]")
    private val TRANSLATION_CREDIT_REGEX = Regex("^\\s*by\\s*[:：].+", RegexOption.IGNORE_CASE)
    private val LRC_METADATA_PATTERN = Pattern.compile("^\\[[a-zA-Z]+:.*]$")

    /**
     * Parsea un String que contiene una letra en formato LRC o texto plano.
     * @param lyricsText El texto de la letra a procesar.
     * @return Un objeto Lyrics con las listas 'plain' o 'synced' pobladas.
     */
    fun parseLyrics(lyricsText: String?): Lyrics {
        if (lyricsText.isNullOrEmpty()) {
            return Lyrics(plain = emptyList(), synced = emptyList())
        }

        val normalizedInput = stripLeadingLyricsDocumentNoise(lyricsText)
        if (looksLikeTtmlDocument(normalizedInput)) {
            val converted = TtmlLyricsParser.parseToEnhancedLrc(normalizedInput)
                ?: return Lyrics(plain = emptyList(), synced = emptyList())
            return parseLyrics(converted)
        }

        val syncedLines = mutableListOf<SyncedLine>()
        val plainLines = mutableListOf<String>()
        var isSynced = false

        lyricsText.lines().forEach { rawLine ->
            val line = sanitizeLrcLine(rawLine)
            if (line.isEmpty() || LRC_METADATA_PATTERN.matcher(line).matches()) return@forEach

            val lineMatcher = LRC_LINE_REGEX.matcher(line)
            if (lineMatcher.matches()) {
                isSynced = true
                val minutes = lineMatcher.group(1)?.toLong() ?: 0
                val seconds = lineMatcher.group(2)?.toLong() ?: 0
                val fraction = lineMatcher.group(3)?.toLong() ?: 0
                val textWithTags = stripFormatCharacters(lineMatcher.group(4)?.trim() ?: "")
                val text = stripLrcTimestamps(textWithTags)

                val millis = if (lineMatcher.group(3)?.length == 2) fraction * 10 else fraction
                val lineTimestamp = minutes * 60 * 1000 + seconds * 1000 + millis

                // Enhanced word-by-word parsing
                if (text.contains(LRC_WORD_TAG_REGEX)) {
                    val words = mutableListOf<SyncedWord>()
                    val parts = text.split(LRC_WORD_SPLIT_REGEX)
                    val displayText = LRC_WORD_TAG_REGEX.replace(text, "")
                    var pendingWordBoundary = false

                    for (part in parts) {
                        if (part.isEmpty()) continue
                        val wordMatcher = LRC_WORD_REGEX.matcher(part)
                        if (wordMatcher.find()) {
                            val wordMinutes = wordMatcher.group(1)?.toLong() ?: 0
                            val wordSeconds = wordMatcher.group(2)?.toLong() ?: 0
                            val wordFraction = wordMatcher.group(3)?.toLong() ?: 0
                            val wordText = stripFormatCharacters(wordMatcher.group(4) ?: "")
                            val timedWordTextRaw = wordText
                                .substringBefore('\n')
                                .substringBefore('\r')
                                .substringBefore("\\n")
                                .substringBefore("\\r")
                            val startsNewWord = words.isEmpty() ||
                                pendingWordBoundary ||
                                timedWordTextRaw.firstOrNull()?.isWhitespace() == true
                            val timedWordText = timedWordTextRaw.trim()
                            pendingWordBoundary = timedWordTextRaw.lastOrNull()?.isWhitespace() == true
                            val wordMillis = if (wordMatcher.group(3)?.length == 2) wordFraction * 10 else wordFraction
                            val wordTimestamp = wordMinutes * 60 * 1000 + wordSeconds * 1000 + wordMillis
                            if (timedWordText.isNotEmpty()) {
                                words.add(
                                    SyncedWord(
                                        time = wordTimestamp.toInt(),
                                        word = timedWordText,
                                        startsNewWord = startsNewWord
                                    )
                                )
                            }
                        } else {
                            // Preserve only leading untagged text as a timed word.
                            // Trailing untagged chunks (e.g. inline translations) should remain visible in line text
                            // but must not steal word highlight timing.
                            if (words.isEmpty()) {
                                val leading = stripFormatCharacters(part)
                                val startsNewWord = pendingWordBoundary || leading.firstOrNull()?.isWhitespace() == true
                                val visibleLeading = leading.trim()
                                pendingWordBoundary = leading.lastOrNull()?.isWhitespace() == true
                                if (visibleLeading.isNotEmpty()) {
                                    words.add(
                                        SyncedWord(
                                            time = lineTimestamp.toInt(),
                                            word = visibleLeading,
                                            startsNewWord = words.isEmpty() || startsNewWord
                                        )
                                    )
                                }
                            } else if (part.any { it.isWhitespace() }) {
                                pendingWordBoundary = true
                            }
                        }
                    }

                    if (words.isNotEmpty()) {
                        syncedLines.add(SyncedLine(lineTimestamp.toInt(), displayText, words))
                    } else {
                        syncedLines.add(SyncedLine(lineTimestamp.toInt(), displayText))
                    }
                } else {
                    syncedLines.add(SyncedLine(lineTimestamp.toInt(), text))
                }
            } else {
                // línea SIN timestamp
                val stripped = stripLrcTimestamps(stripFormatCharacters(line))
                // Si ya detectamos que el archivo tiene sincronización y ya existe
                // al menos una SyncedLine, tratamos esta línea como continuación
                // de la anterior
                if (isSynced && syncedLines.isNotEmpty()) {
                    val last = syncedLines.removeAt(syncedLines.lastIndex)
                    // Mantenemos el texto previo y añadimos la nueva línea con un salto de línea.
                    val mergedLineText = if (last.line.isEmpty()) {
                        stripped
                    } else {
                        last.line + "\n" + stripped
                    }
                    // Conservamos la lista de palabras sincronizadas si existía.
                    val merged = if (last.words?.isNotEmpty() == true) {
                        SyncedLine(last.time, mergedLineText, last.words)
                    } else {
                        SyncedLine(last.time, mergedLineText)
                    }

                    syncedLines.add(merged)
                } else {
                    // Si no hay sincronización en el archivo, es texto plano
                    plainLines.add(stripped)
                }
            }
        }

        return if (isSynced && syncedLines.isNotEmpty()) {
            val sortedSyncedLines = syncedLines.sortedBy { it.time }
            val pairedLines = pairTranslationLines(sortedSyncedLines).map { line ->

                val romanized = when {
                    MultiLangRomanizer.isJapanese(line.line) -> MultiLangRomanizer.romanizeJapanese(line.line)
                    MultiLangRomanizer.isChinese(line.line) -> MultiLangRomanizer.romanizeChinese(line.line)
                    MultiLangRomanizer.isKorean(line.line) -> MultiLangRomanizer.romanizeKorean(line.line)
                    MultiLangRomanizer.isHindi(line.line) -> MultiLangRomanizer.romanizeHindi(line.line)
                    MultiLangRomanizer.isPunjabi(line.line) -> MultiLangRomanizer.romanizePunjabi(line.line)
                    MultiLangRomanizer.isCyrillic(line.line) -> MultiLangRomanizer.romanizeCyrillic(line.line)
                    else -> null
                }?.capitalizeFirstLetter()?.trim()

                val origTrans = line.translation?.trim()

                line.copy(romanization = romanized)
            }
            val plainVersion = pairedLines.map { line ->
                buildString {
                    append(line.line)
                    if (!line.romanization.isNullOrEmpty()) append("\n").append(line.romanization)
                    if (!line.translation.isNullOrEmpty()) append("\n").append(line.translation)
                }
            }
            Lyrics(synced = pairedLines, plain = plainVersion)
        } else {
            val processedPlain = plainLines.map { line ->
                val romanized = when {
                    MultiLangRomanizer.isJapanese(line) -> MultiLangRomanizer.romanizeJapanese(line)
                    MultiLangRomanizer.isChinese(line) -> MultiLangRomanizer.romanizeChinese(line)
                    MultiLangRomanizer.isKorean(line) -> MultiLangRomanizer.romanizeKorean(line)
                    MultiLangRomanizer.isHindi(line) -> MultiLangRomanizer.romanizeHindi(line)
                    MultiLangRomanizer.isPunjabi(line) -> MultiLangRomanizer.romanizePunjabi(line)
                    MultiLangRomanizer.isCyrillic(line) -> MultiLangRomanizer.romanizeCyrillic(line)
                    else -> null
                }?.capitalizeFirstLetter()?.trim()

                if (!romanized.isNullOrEmpty()) "$line\n$romanized" else line
            }
            Lyrics(plain = processedPlain)
        }
    }

    /**
     * Pairs consecutive synced lines that share the same timestamp.
     * The second line is treated as a translation of the first.
     * Only pairs one translation per original — a third line at the same timestamp stays separate.
     */
    internal fun pairTranslationLines(lines: List<SyncedLine>): List<SyncedLine> {
        if (lines.size < 2) return lines
        val result = mutableListOf<SyncedLine>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val next = lines.getOrNull(i + 1)
            if (next != null && next.time == current.time && current.translation == null && current.line.isNotBlank() && next.line.isNotBlank()) {
                val translationParts = mutableListOf(next.line)
                var consumed = 2
                while (true) {
                    val trailing = lines.getOrNull(i + consumed) ?: break
                    if (trailing.time != current.time || !isTranslationCreditLine(trailing.line)) break
                    translationParts.add(trailing.line)
                    consumed++
                }
                result.add(current.copy(translation = translationParts.joinToString("\n")))
                i += consumed
            } else {
                result.add(current)
                i++
            }
        }
        return result
    }

    internal fun stripLrcTimestamps(value: String): String {
        if (value.isEmpty()) return value
        val withoutTags = LRC_TIMESTAMP_TAG_REGEX.replace(value, "")
        return withoutTags.trimStart()
    }

    internal fun isTranslationCreditLine(line: String): Boolean {
        val normalized = stripLrcTimestamps(line).trim()
        return normalized.isNotEmpty() && TRANSLATION_CREDIT_REGEX.matches(normalized)
    }

    /**
     * Converts synced lyrics to LRC format string.
     * Each line is formatted as [mm:ss.xx]text
     * @param syncedLines The list of synced lines to convert.
     * @return A string in LRC format.
     */
    fun syncedToLrcString(syncedLines: List<SyncedLine>): String {
        return syncedLines.sortedBy { it.time }.flatMap { line ->
            val totalMs = line.time
            val minutes = totalMs / 60000
            val seconds = (totalMs % 60000) / 1000
            val hundredths = (totalMs % 1000) / 10
            val timestamp = "[%02d:%02d.%02d]".format(minutes, seconds, hundredths)
            buildList {
                add("$timestamp${line.line}")
                if (!line.translation.isNullOrBlank()) {
                    line.translation
                        .lines()
                        .filter { it.isNotBlank() }
                        .forEach { translationLine ->
                            add("$timestamp$translationLine")
                        }
                }
            }
        }.joinToString("\n")
    }

    /**
     * Converts plain lyrics (list of lines) to a plain text string.
     * @param plainLines The list of plain text lines.
     * @return A string with each line separated by newline.
     */
    fun plainToString(plainLines: List<String>): String {
        // Strip auto-generated romanization (if present after \n) when converting back to string for storage.
        return plainLines.joinToString("\n") { it.substringBefore('\n') }
    }

    /**
     * Converts Lyrics object to LRC or plain text format based on available data.
     * Prefers synced lyrics if available.
     * @param lyrics The Lyrics object to convert.
     * @param preferSynced Whether to prefer synced lyrics over plain. Default true.
     * @return A string representation of the lyrics.
     */
    fun toLrcString(lyrics: Lyrics, preferSynced: Boolean = true): String {
        return if (preferSynced && !lyrics.synced.isNullOrEmpty()) {
            syncedToLrcString(lyrics.synced)
        } else if (!lyrics.plain.isNullOrEmpty()) {
            plainToString(lyrics.plain)
        } else if (!lyrics.synced.isNullOrEmpty()) {
            syncedToLrcString(lyrics.synced)
        } else {
            ""
        }
    }
}

private fun stripLeadingLyricsDocumentNoise(value: String): String {
    return value.trimStart { char ->
        char.isWhitespace() ||
            char == '\uFEFF' ||
            Character.getType(char).toByte() == Character.FORMAT
    }
}

private fun looksLikeTtmlDocument(value: String): Boolean {
    if (value.startsWith("<tt", ignoreCase = true)) {
        return true
    }
    if (!value.startsWith("<?xml", ignoreCase = true)) {
        return false
    }

    val afterDeclaration = value.substringAfter("?>", missingDelimiterValue = "")
    return afterDeclaration
        .trimStart()
        .startsWith("<tt", ignoreCase = true)
}

private fun sanitizeLrcLine(rawLine: String): String {
    if (rawLine.isEmpty()) return rawLine

    val withoutTerminators = rawLine
        .trimEnd('\r', '\n')
        .filterNot { char ->
            Character.getType(char).toByte() == Character.FORMAT ||
                (Character.isISOControl(char) && char != '\t')
        }
        .trimEnd('\uFEFF')

    val trimmedPrefix = withoutTerminators.trimStart { it.isWhitespace() }
    val firstBracket = trimmedPrefix.indexOf('[')
    return if (firstBracket > 0) {
        trimmedPrefix.substring(firstBracket)
    } else {
        trimmedPrefix
    }
}

private fun stripFormatCharacters(value: String): String {
    val cleaned = value.filterNot { char ->
        Character.getType(char).toByte() == Character.FORMAT ||
            (Character.isISOControl(char) && char != '\t')
    }

    return when (cleaned) {
        "\"", "'" -> ""
        else -> cleaned
    }
}
