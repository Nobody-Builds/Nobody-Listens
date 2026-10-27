package com.nobodybuilds.listens.data.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.DailyListeningBucket
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.DailyListeningDay
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.DayListeningDistribution
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.PlaybackEvent
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.PlaybackSegment
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.PlaybackSpan

private const val SEGMENT_JOIN_TOLERANCE_MS = 0L

internal fun sanitizeEvent(event: PlaybackEvent): PlaybackEvent {
    val safeDuration = event.durationMs.coerceAtLeast(0L)
    val safeEnd = (event.endTimestamp ?: event.timestamp).coerceAtLeast(0L)
    val safeStart = when {
        event.startTimestamp != null -> event.startTimestamp.coerceIn(0L, safeEnd)
        safeDuration > 0L -> (safeEnd - safeDuration).coerceAtLeast(0L)
        else -> safeEnd
    }
    val normalizedDuration = (safeEnd - safeStart).coerceAtLeast(0L)
    val finalDuration = when {
        event.startTimestamp == null && safeDuration in 1 until normalizedDuration -> safeDuration
        else -> normalizedDuration
    }
    val finalStart = (safeEnd - finalDuration).coerceAtLeast(0L)
    return event.copy(
        songId = event.songId,
        timestamp = safeEnd,
        durationMs = finalDuration,
        startTimestamp = finalStart,
        endTimestamp = safeEnd
    )
}

internal fun mergeSongEvents(events: List<PlaybackEvent>): List<PlaybackSegment> {
    if (events.isEmpty()) return emptyList()
    val sorted = events.sortedBy { it.startMillis() }
    val songId = sorted.first().songId
    val segments = mutableListOf<PlaybackSegment>()
    var currentStart = sorted.first().startMillis()
    var currentEnd = sorted.first().endMillis()
    for (index in 1 until sorted.size) {
        val event = sorted[index]
        val start = event.startMillis()
        val end = event.endMillis()
        if (start <= currentEnd + SEGMENT_JOIN_TOLERANCE_MS) {
            currentEnd = max(currentEnd, end)
        } else {
            segments += PlaybackSegment(songId, currentStart, currentEnd)
            currentStart = start
            currentEnd = end
        }
    }
    segments += PlaybackSegment(songId, currentStart, currentEnd)
    return segments
}

internal fun mergeSpans(spans: List<PlaybackSpan>): List<PlaybackSpan> {
    if (spans.isEmpty()) return emptyList()
    val sorted = spans.sortedBy { it.startMillis }
    val merged = mutableListOf<PlaybackSpan>()
    var currentStart = sorted.first().startMillis
    var currentEnd = sorted.first().endMillis
    for (index in 1 until sorted.size) {
        val span = sorted[index]
        val start = span.startMillis
        val end = span.endMillis
        if (start <= currentEnd + SEGMENT_JOIN_TOLERANCE_MS) {
            currentEnd = max(currentEnd, end)
        } else {
            merged += PlaybackSpan(currentStart, currentEnd)
            currentStart = start
            currentEnd = end
        }
    }
    merged += PlaybackSpan(currentStart, currentEnd)
    return merged
}

internal data class DaySlice(
    val date: LocalDate,
    val durationMs: Long
)

internal fun sliceSpanByDay(span: PlaybackSpan, zoneId: ZoneId): List<DaySlice> {
    if (span.durationMs <= 0L) return emptyList()
    val slices = mutableListOf<DaySlice>()
    var cursor = span.startMillis
    val end = span.endMillis
    while (cursor < end) {
        val zoned = Instant.ofEpochMilli(cursor).atZone(zoneId)
        val dayStart = zoned.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
        val nextDayStart = zoned.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val sliceEnd = min(end, nextDayStart)
        val sliceDuration = (sliceEnd - cursor).coerceAtLeast(0L)
        if (sliceDuration > 0L) {
            slices += DaySlice(zoned.toLocalDate(), sliceDuration)
        }
        cursor = sliceEnd
    }
    return slices
}

internal fun computeDayListeningDistribution(
    spans: List<PlaybackSpan>,
    zoneId: ZoneId,
    range: StatsTimeRange,
    startBound: Long?,
    endBound: Long,
    bucketSizeMinutes: Int = 5
): DayListeningDistribution? {
    if (spans.isEmpty()) return null
    val bucketDurationMs = TimeUnit.MINUTES.toMillis(bucketSizeMinutes.toLong())
    val minutesPerDay = TimeUnit.DAYS.toMinutes(1)
    val bucketCount = (minutesPerDay / bucketSizeMinutes).toInt().coerceAtLeast(1)
    val totals = LongArray(bucketCount)
    val totalsByDay = mutableMapOf<LocalDate, LongArray>()
    spans.forEach { span ->
        var cursor = span.startMillis
        val end = span.endMillis
        while (cursor < end) {
            val zoned = Instant.ofEpochMilli(cursor).atZone(zoneId)
            val day = zoned.toLocalDate()
            val dayStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
            var bucketIndex = ((cursor - dayStart) / bucketDurationMs).toInt()
            if (bucketIndex >= bucketCount) {
                bucketIndex = bucketCount - 1
            } else if (bucketIndex < 0) {
                bucketIndex = 0
            }
            val bucketStart = dayStart + bucketIndex * bucketDurationMs
            val bucketEnd = min(end, bucketStart + bucketDurationMs)
            val contribution = (bucketEnd - cursor).coerceAtLeast(0L)
            if (contribution > 0L) {
                totals[bucketIndex] += contribution
                val dayTotals = totalsByDay.getOrPut(day) { LongArray(bucketCount) }
                dayTotals[bucketIndex] += contribution
            }
            cursor = if (bucketEnd > cursor) bucketEnd else end
        }
    }
    val buckets = buildList {
        for (index in 0 until bucketCount) {
            val durationMs = totals[index]
            if (durationMs > 0L) {
                add(
                    DailyListeningBucket(
                        startMinute = index * bucketSizeMinutes,
                        endMinuteExclusive = (index + 1) * bucketSizeMinutes,
                        totalDurationMs = durationMs
                    )
                )
            }
        }
    }
    if (buckets.isEmpty()) return null
    val maxBucketDuration = buckets.maxOf { it.totalDurationMs }.coerceAtLeast(0L)

    val daySequence: List<LocalDate> = when (range) {
        StatsTimeRange.DAY -> {
            val anchor = startBound?.let { Instant.ofEpochMilli(it) }
                ?: spans.minOfOrNull { Instant.ofEpochMilli(it.startMillis) }
                ?: Instant.ofEpochMilli(endBound)
            listOf(anchor.atZone(zoneId).toLocalDate())
        }
        StatsTimeRange.WEEK -> {
            val anchor = startBound?.let { Instant.ofEpochMilli(it) }
                ?: spans.minOfOrNull { Instant.ofEpochMilli(it.startMillis) }
                ?: Instant.ofEpochMilli(endBound)
            val startDate = anchor.atZone(zoneId).toLocalDate()
            (0 until 7).map { offset -> startDate.plusDays(offset.toLong()) }
        }
        else -> totalsByDay.keys.sorted()
    }

    val days = daySequence.map { date ->
        val bucketTotals = totalsByDay[date]
        val dayBuckets = buildList {
            if (bucketTotals != null) {
                for (index in 0 until bucketCount) {
                    val duration = bucketTotals[index]
                    if (duration > 0L) {
                        add(
                            DailyListeningBucket(
                                startMinute = index * bucketSizeMinutes,
                                endMinuteExclusive = (index + 1) * bucketSizeMinutes,
                                totalDurationMs = duration
                            )
                        )
                    }
                }
            }
        }
        val totalDuration = bucketTotals?.sumOf { it } ?: 0L
        DailyListeningDay(
            date = date,
            buckets = dayBuckets,
            totalDurationMs = totalDuration
        )
    }

    return DayListeningDistribution(
        bucketSizeMinutes = bucketSizeMinutes,
        buckets = buckets,
        maxBucketDurationMs = maxBucketDuration,
        days = days
    )
}

internal data class ListeningSessionAggregate(
    var start: Long,
    var end: Long,
    var totalDuration: Long,
    var playCount: Int
)

internal fun computeListeningSessions(
    spans: List<PlaybackSpan>,
    sessionGapThresholdMs: Long
): List<ListeningSessionAggregate> {
    if (spans.isEmpty()) return emptyList()
    val sorted = spans.sortedBy { it.startMillis }
    val sessions = mutableListOf<ListeningSessionAggregate>()

    var current = ListeningSessionAggregate(
        start = sorted.first().startMillis,
        end = sorted.first().endMillis,
        totalDuration = sorted.first().durationMs,
        playCount = 1
    )

    for (index in 1 until sorted.size) {
        val span = sorted[index]
        val spanStart = span.startMillis
        val spanEnd = span.endMillis
        val gap = spanStart - current.end
        if (gap <= sessionGapThresholdMs) {
            current.end = max(current.end, spanEnd)
            current.totalDuration += span.durationMs
            current.playCount += 1
        } else {
            sessions += current
            current = ListeningSessionAggregate(
                start = spanStart,
                end = spanEnd,
                totalDuration = span.durationMs,
                playCount = 1
            )
        }
    }

    sessions += current
    return sessions
}

internal fun PlaybackEvent.startMillis(): Long {
    val end = (endTimestamp ?: timestamp).coerceAtLeast(0L)
    val inferredStart = (startTimestamp ?: (end - durationMs)).coerceAtLeast(0L)
    return min(inferredStart, end)
}

internal fun PlaybackEvent.endMillis(): Long {
    val end = (endTimestamp ?: timestamp).coerceAtLeast(0L)
    val start = startMillis()
    return max(end, start)
}
