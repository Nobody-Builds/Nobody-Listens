package com.nobodybuilds.listens.presentation.artist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class ArtistStoryCalculatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val min = 60_000L
    private val hour = 60 * min

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun ev(song: String, start: Long, dur: Long) = StoryEvent(song, start, dur)

    private val now = at(2026, 10, 18)

    @Test
    fun `returns null when artist was never played`() {
        val events = listOf(ev("x", at(2026, 1, 1), 3 * min))
        assertNull(ArtistStoryCalculator.compute(events, setOf("a1"), now, zone))
        assertNull(ArtistStoryCalculator.compute(events, emptySet(), now, zone))
    }

    @Test
    fun `computes totals first listen top song peak and share`() {
        val events = listOf(
            ev("a1", at(2025, 3, 4), 4 * min),       // first listen
            ev("other", at(2025, 3, 5), 10 * min),
            ev("a2", at(2026, 3, 1), 30 * min),
            ev("a2", at(2026, 3, 2), 30 * min),
            ev("a1", at(2026, 10, 16), 6 * min),     // last played
        )
        val story = ArtistStoryCalculator.compute(events, setOf("a1", "a2"), now, zone)!!

        assertEquals(at(2025, 3, 4), story.firstListenMillis)
        assertEquals("a1", story.firstSongId)
        assertEquals(70 * min, story.totalDurationMs)
        assertEquals(4, story.playCount)
        // a1 and a2 both have 2 plays; a2 has more time.
        assertEquals("a2", story.topSongId)
        assertEquals(2, story.topSongPlays)
        assertEquals(YearMonth.of(2026, 3), story.peakMonth)
        assertEquals(60 * min, story.peakMonthDurationMs)
        assertEquals(70.0 / 80.0, story.shareOfAll, 1e-9)
        assertEquals(at(2026, 10, 16) + 6 * min, story.lastPlayedMillis)
    }

    @Test
    fun `timeline covers last 12 months ending with current month`() {
        val events = listOf(ev("a", at(2024, 1, 1), hour), ev("a", at(2026, 9, 10), 20 * min))
        val story = ArtistStoryCalculator.compute(events, setOf("a"), now, zone)!!
        assertEquals(12, story.months.size)
        assertEquals(YearMonth.of(2025, 11), story.months.first().month)
        assertEquals(YearMonth.of(2026, 10), story.months.last().month)
        assertEquals(20 * min, story.months[10].durationMs)
        assertEquals(1, story.months[10].playCount)
        assertEquals(0L, story.months.last().durationMs)
    }

    @Test
    fun `timeline is shorter when first listen is recent`() {
        val events = listOf(ev("a", at(2026, 8, 20), 5 * min))
        val story = ArtistStoryCalculator.compute(events, setOf("a"), now, zone)!!
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), story.months.map { it.month })
    }

    @Test
    fun `milestones pick the highest reached per kind`() {
        val m = ArtistStoryCalculator.milestones(
            totalMs = 12 * hour, plays = 130,
            firstListenMillis = at(2025, 6, 1), nowMillis = now, zoneId = zone
        )
        assertEquals(listOf("10 hours", "100 plays", "1 year together"), m)

        val few = ArtistStoryCalculator.milestones(20 * min, 3, at(2026, 10, 10), now, zone)
        assertTrue(few.isEmpty())
    }

    @Test
    fun `matches artist by id then by whole-word name`() {
        assertTrue(ArtistStoryCalculator.songMatchesArtist(7, emptyList(), emptyList(), "Someone", 7, "Ado"))
        assertTrue(ArtistStoryCalculator.songMatchesArtist(1, listOf(2L, 7L), emptyList(), "", 7, "Ado"))
        assertTrue(ArtistStoryCalculator.songMatchesArtist(1, emptyList(), emptyList(), "Eve feat. Ado", 7, "ado"))
        assertTrue(ArtistStoryCalculator.songMatchesArtist(1, emptyList(), emptyList(), "  The   Beatles ", 7, "the beatles"))
        assertFalse(ArtistStoryCalculator.songMatchesArtist(1, emptyList(), emptyList(), "Adomas", 7, "Ado"))
        assertFalse(ArtistStoryCalculator.songMatchesArtist(1, emptyList(), emptyList(), "Unknown Artist", 7, "Unknown Artist"))
    }

    @Test
    fun `formats durations shares and relative dates`() {
        assertEquals("3 h 20 min", ArtistStoryCalculator.formatDuration(3 * hour + 20 * min))
        assertEquals("2 h", ArtistStoryCalculator.formatDuration(2 * hour))
        assertEquals("45 min", ArtistStoryCalculator.formatDuration(45 * min))
        assertEquals("under a minute", ArtistStoryCalculator.formatDuration(30_000))

        assertEquals("3.2%", ArtistStoryCalculator.formatShare(0.032))
        assertEquals("15%", ArtistStoryCalculator.formatShare(0.151))
        assertEquals("<0.1%", ArtistStoryCalculator.formatShare(0.0004))

        assertEquals("today", ArtistStoryCalculator.formatRelative(now - hour, now, zone))
        assertEquals("yesterday", ArtistStoryCalculator.formatRelative(at(2026, 10, 17), now, zone))
        assertEquals("2 days ago", ArtistStoryCalculator.formatRelative(at(2026, 10, 16), now, zone))
        assertEquals("3 weeks ago", ArtistStoryCalculator.formatRelative(at(2026, 9, 27), now, zone))
        assertEquals("1 year ago", ArtistStoryCalculator.formatRelative(at(2025, 9, 1), now, zone))

        assertEquals("March 2026", ArtistStoryCalculator.formatMonth(YearMonth.of(2026, 3)))
        assertEquals("M", ArtistStoryCalculator.monthInitial(YearMonth.of(2026, 3)))
        assertEquals("Mar 4, 2025", ArtistStoryCalculator.formatDate(at(2025, 3, 4), zone))
    }
}
