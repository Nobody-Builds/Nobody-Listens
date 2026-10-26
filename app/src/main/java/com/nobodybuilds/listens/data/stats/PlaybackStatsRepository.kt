package com.nobodybuilds.listens.data.stats

import android.content.Context
import com.nobodybuilds.listens.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@Singleton
class PlaybackStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val store = PlaybackHistoryStore(context)
    private val _refreshVersion = MutableStateFlow(0L)
    val refreshFlow: StateFlow<Long> = _refreshVersion.asStateFlow()

    private val sessionGapThresholdMs = TimeUnit.MINUTES.toMillis(30)

    data class PlaybackEvent(
        val songId: String,
        val timestamp: Long,
        val durationMs: Long,
        val startTimestamp: Long? = null,
        val endTimestamp: Long? = null
    )

    data class SkipEvent(
        val songId: String,
        val timestamp: Long
    )

    data class PlaybackHistoryEntry(
        val songId: String,
        val timestamp: Long
    )

    data class SongPlaybackSummary(
        val songId: String,
        val title: String,
        val artist: String,
        val albumArtUri: String?,
        val totalDurationMs: Long,
        val playCount: Int
    )

    data class ArtistPlaybackSummary(
        val artist: String,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueSongs: Int
    )

    data class GenrePlaybackSummary(
        val genre: String,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueArtists: Int
    )

    data class AlbumPlaybackSummary(
        val album: String,
        val albumArtUri: String?,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueSongs: Int
    )

    data class TimelineEntry(
        val label: String,
        val totalDurationMs: Long,
        val playCount: Int
    )

    data class PlaybackSegment(
        val songId: String,
        val startMillis: Long,
        val endMillis: Long
    ) {
        val durationMs: Long
            get() = (endMillis - startMillis).coerceAtLeast(0L)
    }

    data class PlaybackSpan(
        val startMillis: Long,
        val endMillis: Long
    ) {
        val durationMs: Long
            get() = (endMillis - startMillis).coerceAtLeast(0L)
    }

    data class DayListeningDistribution(
        val bucketSizeMinutes: Int,
        val buckets: List<DailyListeningBucket>,
        val maxBucketDurationMs: Long,
        val days: List<DailyListeningDay>
    )

    data class DailyListeningBucket(
        val startMinute: Int,
        val endMinuteExclusive: Int,
        val totalDurationMs: Long
    )

    data class DailyListeningDay(
        val date: LocalDate,
        val buckets: List<DailyListeningBucket>,
        val totalDurationMs: Long
    )

    data class PlaybackStatsSummary(
        val range: StatsTimeRange,
        val startTimestamp: Long?,
        val endTimestamp: Long,
        val totalDurationMs: Long,
        val totalPlayCount: Int,
        val uniqueSongs: Int,
        val averageDailyDurationMs: Long,
        val songs: List<SongPlaybackSummary> = emptyList(),
        val topSongs: List<SongPlaybackSummary> = emptyList(),
        val topGenres: List<GenrePlaybackSummary> = emptyList(),
        val timeline: List<TimelineEntry>,
        val topArtists: List<ArtistPlaybackSummary>,
        val topAlbums: List<AlbumPlaybackSummary>,
        val activeDays: Int,
        val longestStreakDays: Int,
        val totalSessions: Int,
        val averageSessionDurationMs: Long,
        val longestSessionDurationMs: Long,
        val averageSessionsPerDay: Double,
        val dayListeningDistribution: DayListeningDistribution?,
        val peakTimeline: TimelineEntry?,
        val peakDayLabel: String?,
        val peakDayDurationMs: Long,
        // Nobody Listens insights
        val skipCount: Int = 0,
        val newSongs: List<SongPlaybackSummary> = emptyList(),
        val newSongCount: Int = 0,
        val newArtistCount: Int = 0,
        val customStartMillis: Long? = null,
        val customEndMillis: Long? = null
    ) {
        /** Plays that were started: counted plays plus skips. */
        val startedCount: Int get() = totalPlayCount + skipCount
        val skipRate: Float get() = if (startedCount > 0) skipCount.toFloat() / startedCount else 0f
    }

    suspend fun recordPlayback(
        songId: String,
        durationMs: Long,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext
        val coercedTimestamp = timestamp.coerceAtLeast(0L)
        val coercedDuration = durationMs.coerceAtLeast(0L)
        val start = (coercedTimestamp - coercedDuration).coerceAtLeast(0L)
        val sanitizedEvent = PlaybackEvent(
            songId = songId,
            timestamp = coercedTimestamp,
            durationMs = coercedDuration,
            startTimestamp = start,
            endTimestamp = coercedTimestamp
        )
        val writeSucceeded = store.updateEventsAtomically { events ->
            val cutoff = sanitizedEvent.endMillis() - MAX_HISTORY_AGE_MS
            if (cutoff > 0) {
                events.removeAll { it.endMillis() < cutoff }
            }
            events += sanitizedEvent
            events
        }
        if (writeSucceeded) {
            notifyStatsChanged()
        }
    }

    suspend fun recordSkip(
        songId: String,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext
        val written = synchronized(store.skipLock) {
            val skips = store.readSkipsLocked()
            skips += SkipEvent(songId, timestamp.coerceAtLeast(0L))
            // Keep the file small: the most recent skips are what the insight needs.
            val trimmed = if (skips.size > MAX_SKIP_EVENTS) skips.takeLast(MAX_SKIP_EVENTS) else skips
            store.writeSkipsLocked(trimmed)
        }
        if (written) notifyStatsChanged()
    }

    suspend fun loadSummary(
        range: StatsTimeRange,
        songs: List<Song>,
        nowMillis: Long = System.currentTimeMillis(),
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): PlaybackStatsSummary = withContext(Dispatchers.IO) {
        val zoneId = ZoneId.systemDefault()
        val allEvents = store.readEvents()
        val skips = synchronized(store.skipLock) { store.readSkipsLocked() }
        buildSummaryFromEvents(
            range = range,
            songs = songs,
            nowMillis = nowMillis,
            allEvents = allEvents,
            zoneId = zoneId,
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis,
            skipEvents = skips
        )
    }

    internal fun buildSummaryFromEvents(
        range: StatsTimeRange,
        songs: List<Song>,
        nowMillis: Long,
        allEvents: List<PlaybackEvent>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        customStartMillis: Long? = null,
        customEndMillis: Long? = null,
        skipEvents: List<SkipEvent> = emptyList()
    ): PlaybackStatsSummary {
        val (startBound, endBound) = if (range == StatsTimeRange.CUSTOM) {
            val end = (customEndMillis ?: nowMillis).coerceAtMost(nowMillis)
            val start = (customStartMillis ?: allEvents.minOfOrNull { it.startMillis() } ?: end).coerceAtMost(end)
            start to end
        } else {
            range.resolveBounds(allEvents, nowMillis, zoneId)
        }
        val filteredEvents = allEvents.mapNotNull { event ->
            val start = event.startMillis()
            val end = event.endMillis()
            val lowerBound = startBound ?: Long.MIN_VALUE
            if (end < lowerBound || start > endBound) {
                return@mapNotNull null
            }

            val clippedStart = max(start, lowerBound)
            val clippedEnd = min(end, endBound)
            val clippedDuration = (clippedEnd - clippedStart).coerceAtLeast(0L)
            if (clippedDuration <= 0L) {
                return@mapNotNull null
            }

            event.copy(
                timestamp = clippedEnd,
                durationMs = clippedDuration,
                startTimestamp = clippedStart,
                endTimestamp = clippedEnd
            )
        }

        val songMap = songs.associateBy { it.id }
        val normalizedEvents = filteredEvents

        val segmentsBySong = normalizedEvents
            .groupBy { it.songId }
            .mapValues { (_, eventsForSong) -> mergeSongEvents(eventsForSong) }

        val overallSpans = mergeSpans(segmentsBySong.values.flatten().map { PlaybackSpan(it.startMillis, it.endMillis) })

        val effectiveStart = startBound
            ?: overallSpans.minOfOrNull { it.startMillis }
            ?: normalizedEvents.minOfOrNull { it.startMillis() }
            ?: allEvents.minOfOrNull { it.startMillis() }
        val effectiveEnd = overallSpans.maxOfOrNull { it.endMillis } ?: endBound

        val totalDuration = overallSpans.sumOf { it.durationMs }
        val totalPlays = segmentsBySong.values.sumOf { it.size }
        val uniqueSongs = segmentsBySong.keys.size

        val allSongs = segmentsBySong
            .mapNotNull { (songId, segmentsForSong) ->
                val song = songMap[songId] ?: return@mapNotNull null
                val title = song.title.takeIf { it.isNotBlank() }
                    ?: song.path.substringAfterLast('/').ifBlank { return@mapNotNull null }
                val artist = song.displayArtist.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                SongPlaybackSummary(
                    songId = songId,
                    title = title,
                    artist = artist,
                    albumArtUri = song.albumArtUriString,
                    totalDurationMs = segmentsForSong.sumOf { it.durationMs },
                    playCount = segmentsForSong.size
                )
            }
            .sortedWith(
                compareByDescending<SongPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
        val topSongs = allSongs.take(5)

        val topGenres = segmentsBySong.entries
            .groupBy { (songId, _) ->
                val genre = songMap[songId]?.genre
                if (genre.isNullOrBlank()) "Unknown Genre" else genre
            }
            .map { (genre, groupedSongs) ->
                val flattened = groupedSongs.flatMap { it.value }
                val uniqueArtists = groupedSongs
                    .mapNotNull { (songId, _) ->
                        songMap[songId]?.artist?.takeIf { it.isNotBlank() }
                    }
                    .toSet()
                    .size
                GenrePlaybackSummary(
                    genre = genre,
                    totalDurationMs = flattened.sumOf { it.durationMs },
                    playCount = flattened.size,
                    uniqueArtists = uniqueArtists
                )
            }
            .sortedWith(
                compareByDescending<GenrePlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(5)

        var daySpan = 1L
        val averageDailyDuration = if (effectiveStart != null) {
            val startInstant = Instant.ofEpochMilli(effectiveStart)
            val endInstant = Instant.ofEpochMilli(effectiveEnd)
            val startDate = startInstant.atZone(zoneId).toLocalDate()
            val endDate = endInstant.atZone(zoneId).toLocalDate()
            daySpan = max(1L, java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1)
            if (daySpan > 0) totalDuration / daySpan else totalDuration
        } else {
            totalDuration
        }

        val daySlices = overallSpans.flatMap { sliceSpanByDay(it, zoneId) }
        val eventsByDay = daySlices.groupBy { it.date }
        val activeDays = eventsByDay.size
        val sortedDays = eventsByDay.keys.sorted()
        var longestStreak = 0
        var currentStreak = 0
        var lastDay: java.time.LocalDate? = null
        sortedDays.forEach { day ->
            if (lastDay == null || day == lastDay?.plusDays(1)) {
                currentStreak += 1
            } else {
                currentStreak = 1
            }
            if (currentStreak > longestStreak) {
                longestStreak = currentStreak
            }
            lastDay = day
        }

        val sessions = computeListeningSessions(overallSpans, sessionGapThresholdMs)
        val totalSessions = sessions.size
        val totalSessionDuration = sessions.sumOf { it.totalDuration }
        val averageSessionDuration = if (totalSessions > 0) totalSessionDuration / totalSessions else 0L
        val longestSessionDuration = sessions.maxOfOrNull { it.totalDuration } ?: 0L
        val averageSessionsPerDay = if (daySpan > 0) totalSessions.toDouble() / daySpan else 0.0

        val timelineBuckets = createTimelineBuckets(
            range = range,
            zoneId = zoneId,
            now = Instant.ofEpochMilli(endBound),
            spans = overallSpans,
            fallbackStart = effectiveStart ?: endBound,
            customStart = startBound
        )
        val timelineEntries = accumulateTimelineEntries(timelineBuckets, overallSpans)

        val topArtists = segmentsBySong.entries
            .groupBy { (songId, _) ->
                songMap[songId]?.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            }
            .map { (artist, groupedSongs) ->
                val flattened = groupedSongs.flatMap { it.value }
                val uniqueSongCount = groupedSongs.size
                ArtistPlaybackSummary(
                    artist = artist,
                    totalDurationMs = flattened.sumOf { it.durationMs },
                    playCount = flattened.size,
                    uniqueSongs = uniqueSongCount
                )
            }
            .sortedWith(
                compareByDescending<ArtistPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(5)

        val topAlbums = segmentsBySong.entries
            .groupBy { (songId, _) ->
                val song = songMap[songId]
                song?.album?.takeIf { it.isNotBlank() } ?: "Unknown Album"
            }
            .map { (album, groupedSongs) ->
                val flattened = groupedSongs.flatMap { it.value }
                val uniqueSongCount = groupedSongs.size
                val firstSong = groupedSongs
                    .asSequence()
                    .mapNotNull { songMap[it.key] }
                    .firstOrNull()
                AlbumPlaybackSummary(
                    album = album,
                    albumArtUri = firstSong?.albumArtUriString,
                    totalDurationMs = flattened.sumOf { it.durationMs },
                    playCount = flattened.size,
                    uniqueSongs = uniqueSongCount
                )
            }
            .sortedWith(
                compareByDescending<AlbumPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(5)

        val peakTimeline = timelineEntries
            .filter { it.totalDurationMs > 0L }
            .maxByOrNull { it.totalDurationMs }

        val durationsByDayOfWeek = daySlices.groupBy { slice ->
            slice.date.dayOfWeek
        }
        val peakDay = durationsByDayOfWeek.maxByOrNull { entry ->
            entry.value.sumOf { it.durationMs }
        }
        val peakDayLabel = peakDay?.key?.getDisplayName(TextStyle.FULL, Locale.US)
        val peakDayDuration = peakDay?.value?.sumOf { it.durationMs } ?: 0L
        val dayListeningDistribution = computeDayListeningDistribution(
            spans = overallSpans,
            zoneId = zoneId,
            range = range,
            startBound = startBound,
            endBound = endBound
        )

        // ---- Nobody Listens insights: skips and first-time listens in this period ----
        val lowerBound = if (range == StatsTimeRange.ALL) Long.MIN_VALUE else (startBound ?: Long.MIN_VALUE)
        val skipCount = skipEvents.count { it.timestamp in lowerBound..endBound }
        val firstPlayBySong = HashMap<String, Long>()
        allEvents.forEach { event ->
            val start = event.startMillis()
            val previous = firstPlayBySong[event.songId]
            if (previous == null || start < previous) firstPlayBySong[event.songId] = start
        }
        val newSongIds = firstPlayBySong.filterValues { it in lowerBound..endBound }.keys
        val newSongs = allSongs.filter { it.songId in newSongIds }
        val firstPlayByArtist = HashMap<String, Long>()
        firstPlayBySong.forEach { (songId, firstStart) ->
            val artist = songMap[songId]?.artist?.takeIf { it.isNotBlank() } ?: return@forEach
            val previous = firstPlayByArtist[artist]
            if (previous == null || firstStart < previous) firstPlayByArtist[artist] = firstStart
        }
        val newArtistCount = firstPlayByArtist.values.count { it in lowerBound..endBound }

        return PlaybackStatsSummary(
            skipCount = skipCount,
            newSongs = newSongs,
            newSongCount = newSongIds.size,
            newArtistCount = newArtistCount,
            customStartMillis = if (range == StatsTimeRange.CUSTOM) startBound else null,
            customEndMillis = if (range == StatsTimeRange.CUSTOM) endBound else null,
            range = range,
            startTimestamp = startBound,
            endTimestamp = endBound,
            totalDurationMs = totalDuration,
            totalPlayCount = totalPlays,
            uniqueSongs = uniqueSongs,
            averageDailyDurationMs = averageDailyDuration,
            songs = allSongs,
            topSongs = topSongs,
            timeline = timelineEntries,
            topGenres = topGenres,
            topArtists = topArtists,
            topAlbums = topAlbums,
            activeDays = activeDays,
            longestStreakDays = longestStreak,
            totalSessions = totalSessions,
            averageSessionDurationMs = averageSessionDuration,
            longestSessionDurationMs = longestSessionDuration,
            averageSessionsPerDay = averageSessionsPerDay,
            dayListeningDistribution = dayListeningDistribution,
            peakTimeline = peakTimeline,
            peakDayLabel = peakDayLabel,
            peakDayDurationMs = peakDayDuration
        )
    }

    suspend fun exportEventsForBackup(): List<PlaybackEvent> = withContext(Dispatchers.IO) {
        store.readEvents().map { event -> sanitizeEvent(event) }
    }

    /** Nobody Listens smart features: read-only access to recorded skips (taste profile). */
    suspend fun loadSkipEvents(): List<SkipEvent> = withContext(Dispatchers.IO) {
        synchronized(store.skipLock) { store.readSkipsLocked() }.toList()
    }

    suspend fun loadPlaybackHistory(limit: Int = DEFAULT_PLAYBACK_HISTORY_LIMIT): List<PlaybackHistoryEntry> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        val safeLimit = limit.coerceAtMost(MAX_PLAYBACK_HISTORY_LIMIT)
        store.readEvents()
            .asSequence()
            .sortedByDescending { event -> event.timestamp }
            .take(safeLimit)
            .map { event ->
                PlaybackHistoryEntry(
                    songId = event.songId,
                    timestamp = event.timestamp.coerceAtLeast(0L)
                )
            }
            .toList()
    }

    suspend fun importEventsFromBackup(
        events: List<PlaybackEvent>,
        clearExisting: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val writeSucceeded = store.updateEventsAtomically { existingEvents ->
            val base = if (clearExisting) {
                emptyList()
            } else {
                existingEvents
            }
            val merged = (base + events)
                .map { event -> sanitizeEvent(event) }
                .distinctBy { event ->
                    "${event.songId}:${event.startMillis()}:${event.endMillis()}:${event.durationMs}"
                }
                .sortedBy { event -> event.timestamp }
                .toMutableList()
            merged
        }
        if (writeSucceeded) {
            notifyStatsChanged()
        }
    }

    fun requestRefresh() {
        notifyStatsChanged()
    }

    private fun notifyStatsChanged() {
        _refreshVersion.update { current ->
            if (current == Long.MAX_VALUE) {
                1L
            } else {
                current + 1L
            }
        }
    }

    companion object {
        private const val DEFAULT_PLAYBACK_HISTORY_LIMIT = 500
        private const val MAX_PLAYBACK_HISTORY_LIMIT = 5_000
        // Nobody Listens keeps listening history for life (lifetime stats); this is only a safety cap.
        private val MAX_HISTORY_AGE_MS = TimeUnit.DAYS.toMillis(365L * 100)
        private const val MAX_SKIP_EVENTS = 50_000
    }
}

enum class StatsTimeRange(val displayName: String) {
    DAY("Today"),
    WEEK("Week to Date"),
    MONTH("Month to Date"),
    YEAR("Year to Date"),
    ALL("All Time"),
    CUSTOM("Custom")
}
