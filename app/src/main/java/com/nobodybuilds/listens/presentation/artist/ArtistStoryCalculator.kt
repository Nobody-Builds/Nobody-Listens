package com.nobodybuilds.listens.presentation.artist

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One listening event, reduced to what the artist story needs. */
data class StoryEvent(
    val songId: String,
    val startMillis: Long,
    val durationMs: Long,
    val endMillis: Long = startMillis + durationMs.coerceAtLeast(0L),
)

@Immutable
data class StoryMonth(
    val month: YearMonth,
    val durationMs: Long,
    val playCount: Int,
)

@Immutable
data class ArtistStory(
    val firstListenMillis: Long,
    val firstSongId: String,
    val totalDurationMs: Long,
    val playCount: Int,
    val topSongId: String,
    val topSongPlays: Int,
    val topSongDurationMs: Long,
    val peakMonth: YearMonth,
    val peakMonthDurationMs: Long,
    /** Share of all listening time (0..1). */
    val shareOfAll: Double,
    val lastPlayedMillis: Long,
    /** Last 12 months (or since the first listen, if shorter), oldest first, current month last. */
    val months: List<StoryMonth>,
    val milestones: List<String>,
)

/**
 * Pure computation behind the "Your story with <artist>" section on the artist page.
 * No Android dependencies, so it can be unit tested on the JVM.
 */
object ArtistStoryCalculator {

    private const val HOUR_MS = 3_600_000L
    private const val MINUTE_MS = 60_000L

    private val HOUR_MILESTONES = listOf(1L, 5L, 10L, 24L, 50L, 100L, 250L, 500L, 1000L)
    private val PLAY_MILESTONES = listOf(10, 25, 50, 100, 250, 500, 1000, 2500, 5000)

    /** Normalises an artist name for loose matching ("The  Beatles " -> "the beatles"). */
    fun normalizeName(name: String): String =
        name.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    /**
     * True when a song belongs to the artist: by id first (primary id or any artist ref),
     * otherwise by the normalised artist name appearing in the song's artist string.
     */
    fun songMatchesArtist(
        songArtistId: Long,
        songArtistRefIds: Collection<Long>,
        songArtistNames: Collection<String>,
        songArtistString: String,
        artistId: Long,
        artistName: String,
    ): Boolean {
        if (artistId > 0 && (songArtistId == artistId || artistId in songArtistRefIds)) return true
        val target = normalizeName(artistName)
        if (target.isEmpty() || target == "unknown artist" || target == "<unknown>") return false
        if (songArtistNames.any { normalizeName(it) == target }) return true
        val haystack = normalizeName(songArtistString)
        if (haystack.isEmpty()) return false
        if (haystack == target) return true
        // Whole-word containment so "Ado" doesn't match "Adomas".
        val pattern = Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(target) + "($|[^\\p{L}\\p{N}])")
        return pattern.containsMatchIn(haystack)
    }

    /**
     * Builds the story. Returns null when none of [events] belong to [artistSongIds].
     * [allEvents] is every listening event (used for the share of total listening).
     */
    fun compute(
        allEvents: List<StoryEvent>,
        artistSongIds: Set<String>,
        nowMillis: Long,
        zoneId: ZoneId,
        maxMonths: Int = 12,
    ): ArtistStory? {
        if (artistSongIds.isEmpty()) return null
        val artistEvents = allEvents.filter { it.songId in artistSongIds }
        if (artistEvents.isEmpty()) return null

        val first = artistEvents.minBy { it.startMillis }
        val last = artistEvents.maxBy { it.endMillis }
        val total = artistEvents.sumOf { it.durationMs.coerceAtLeast(0L) }
        val allTotal = allEvents.sumOf { it.durationMs.coerceAtLeast(0L) }

        val bySong = artistEvents.groupBy { it.songId }
        val top = bySong.entries.maxWith(
            compareBy<Map.Entry<String, List<StoryEvent>>> { it.value.size }
                .thenBy { e -> e.value.sumOf { it.durationMs.coerceAtLeast(0L) } }
                .thenByDescending { e -> e.value.minOf { it.startMillis } } // earlier discovered wins ties
        )

        val byMonth = artistEvents.groupBy { monthOf(it.startMillis, zoneId) }
        val monthTotals = byMonth.mapValues { (_, list) -> list.sumOf { it.durationMs.coerceAtLeast(0L) } }
        val peak = monthTotals.entries.maxWith(
            compareBy<Map.Entry<YearMonth, Long>> { it.value }.thenBy { it.key }
        )

        val currentMonth = monthOf(nowMillis, zoneId)
        val firstMonth = monthOf(first.startMillis, zoneId)
        val span = (ChronoUnit.MONTHS.between(firstMonth, currentMonth) + 1).coerceIn(1L, maxMonths.toLong()).toInt()
        val months = (span - 1 downTo 0).map { back ->
            val m = currentMonth.minusMonths(back.toLong())
            StoryMonth(m, monthTotals[m] ?: 0L, byMonth[m]?.size ?: 0)
        }

        val share = if (allTotal > 0) total.toDouble() / allTotal else 0.0

        return ArtistStory(
            firstListenMillis = first.startMillis,
            firstSongId = first.songId,
            totalDurationMs = total,
            playCount = artistEvents.size,
            topSongId = top.key,
            topSongPlays = top.value.size,
            topSongDurationMs = top.value.sumOf { it.durationMs.coerceAtLeast(0L) },
            peakMonth = peak.key,
            peakMonthDurationMs = peak.value,
            shareOfAll = share.coerceIn(0.0, 1.0),
            lastPlayedMillis = last.endMillis,
            months = months,
            milestones = milestones(total, artistEvents.size, first.startMillis, nowMillis, zoneId),
        )
    }

    /** Highest reached milestone per kind: hours, plays, time together. */
    fun milestones(totalMs: Long, plays: Int, firstListenMillis: Long, nowMillis: Long, zoneId: ZoneId): List<String> {
        val out = mutableListOf<String>()
        val hours = totalMs / HOUR_MS
        HOUR_MILESTONES.lastOrNull { hours >= it }?.let { out += if (it == 1L) "1 hour" else "$it hours" }
        PLAY_MILESTONES.lastOrNull { plays >= it }?.let { out += "$it plays" }
        val firstDate = Instant.ofEpochMilli(firstListenMillis).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val years = ChronoUnit.YEARS.between(firstDate, today)
        if (years >= 1) {
            out += if (years == 1L) "1 year together" else "$years years together"
        } else {
            val monthsTogether = ChronoUnit.MONTHS.between(firstDate, today)
            if (monthsTogether >= 6) out += "6 months together"
            else if (monthsTogether >= 1) out += "1 month together"
        }
        return out
    }

    fun monthOf(millis: Long, zoneId: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zoneId))

    /** "3 h 20 min", "45 min", "under a minute". */
    fun formatDuration(ms: Long): String {
        val totalMinutes = ms.coerceAtLeast(0L) / MINUTE_MS
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            m > 0 -> "$m min"
            else -> "under a minute"
        }
    }

    /** "today", "yesterday", "5 days ago", "3 weeks ago", "2 months ago", "1 year ago". */
    fun formatRelative(thenMillis: Long, nowMillis: Long, zoneId: ZoneId): String {
        val then = Instant.ofEpochMilli(thenMillis).atZone(zoneId).toLocalDate()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val days = ChronoUnit.DAYS.between(then, now).coerceAtLeast(0L)
        return when {
            days == 0L -> "today"
            days == 1L -> "yesterday"
            days < 14 -> "$days days ago"
            days < 60 -> "${days / 7} weeks ago"
            days < 365 -> {
                val months = ChronoUnit.MONTHS.between(then, now).coerceAtLeast(2L)
                "$months months ago"
            }
            else -> {
                val years = ChronoUnit.YEARS.between(then, now).coerceAtLeast(1L)
                if (years == 1L) "1 year ago" else "$years years ago"
            }
        }
    }

    /** "3.2%" (one decimal under 10%, whole numbers above, "<0.1%" for tiny shares). */
    fun formatShare(share: Double): String {
        val pct = share * 100.0
        return when {
            pct <= 0.0 -> "0%"
            pct < 0.1 -> "<0.1%"
            pct < 10.0 -> String.format(Locale.US, "%.1f%%", pct)
            else -> String.format(Locale.US, "%.0f%%", pct)
        }
    }

    fun formatMonth(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year

    fun monthInitial(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)

    fun formatDate(millis: Long, zoneId: ZoneId): String {
        val d: LocalDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
        return "${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth}, ${d.year}"
    }
}
