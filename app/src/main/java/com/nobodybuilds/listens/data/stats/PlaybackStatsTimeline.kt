package com.nobodybuilds.listens.data.stats

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.PlaybackEvent
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.PlaybackSpan
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.TimelineEntry

internal data class TimelineBucket(
    val label: String,
    val startMillis: Long,
    val endMillis: Long,
    val inclusiveEnd: Boolean
)

internal fun accumulateTimelineEntries(
    buckets: List<TimelineBucket>,
    spans: List<PlaybackSpan>
): List<TimelineEntry> {
    if (buckets.isEmpty()) return emptyList()
    val durationByBucket = LongArray(buckets.size)
    val playCountByBucket = DoubleArray(buckets.size)
    spans.forEach { span ->
        val spanStart = span.startMillis
        val spanEnd = span.endMillis
        val spanDuration = span.durationMs
        if (spanDuration <= 0L) return@forEach
        buckets.forEachIndexed { index, bucket ->
            val bucketEndExclusive = if (bucket.inclusiveEnd) bucket.endMillis + 1 else bucket.endMillis
            val overlapStart = max(spanStart, bucket.startMillis)
            val overlapEnd = min(spanEnd, bucketEndExclusive)
            val overlap = (overlapEnd - overlapStart).coerceAtLeast(0L)
            if (overlap > 0) {
                durationByBucket[index] += overlap
                playCountByBucket[index] += overlap.toDouble() / spanDuration.toDouble()
            }
        }
    }
    return buckets.mapIndexed { index, bucket ->
        TimelineEntry(
            label = bucket.label,
            totalDurationMs = durationByBucket[index],
            playCount = playCountByBucket[index].roundToInt()
        )
    }
}

internal fun createTimelineBuckets(
    range: StatsTimeRange,
    zoneId: ZoneId,
    now: Instant,
    spans: List<PlaybackSpan>,
    fallbackStart: Long,
    customStart: Long? = null
): List<TimelineBucket> {
    return when (range) {
        StatsTimeRange.CUSTOM -> createCustomBuckets(zoneId, customStart ?: fallbackStart, now)
        StatsTimeRange.DAY -> createDayBuckets(zoneId, now)
        StatsTimeRange.WEEK -> createWeekBuckets(zoneId, now)
        StatsTimeRange.MONTH -> createMonthBuckets(zoneId, now)
        StatsTimeRange.YEAR -> createYearBuckets(zoneId, now)
        StatsTimeRange.ALL -> createAllTimeBuckets(zoneId, spans, fallbackStart, now)
    }
}

/** Days for ranges up to ~a month, months up to ~a year and a half, years beyond that. */
private fun createCustomBuckets(zoneId: ZoneId, startMillis: Long, now: Instant): List<TimelineBucket> {
    val startDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate()
    val endDate = now.atZone(zoneId).toLocalDate()
    if (endDate.isBefore(startDate)) return emptyList()
    val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1
    return when {
        days <= 35 -> {
            val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
            (0 until days).map { offset ->
                val day = startDate.plusDays(offset)
                TimelineBucket(
                    label = formatter.format(day),
                    startMillis = day.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    endMillis = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    inclusiveEnd = false
                )
            }
        }
        days <= 550 -> {
            val formatter = DateTimeFormatter.ofPattern("MMM yy", Locale.US)
            var month = YearMonth.from(startDate)
            val lastMonth = YearMonth.from(endDate)
            buildList {
                while (!month.isAfter(lastMonth)) {
                    val bucketStart = maxOf(month.atDay(1), startDate)
                    add(
                        TimelineBucket(
                            label = formatter.format(month),
                            startMillis = bucketStart.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                            endMillis = month.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                            inclusiveEnd = false
                        )
                    )
                    month = month.plusMonths(1)
                }
            }
        }
        else -> (startDate.year..endDate.year).map { yearValue ->
            val year = Year.of(yearValue)
            TimelineBucket(
                label = yearValue.toString(),
                startMillis = maxOf(year.atDay(1), startDate).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                endMillis = year.plusYears(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }
}

private fun createDayBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
    val dayStart = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
    val formatter = DateTimeFormatter.ofPattern("ha", Locale.US)
    return (0 until 6).map { index ->
        val bucketStart = dayStart.plus(Duration.ofHours((index * 4).toLong()))
        val bucketEnd = bucketStart.plus(Duration.ofHours(4))
        val label = formatter.format(bucketStart.atZone(zoneId)).lowercase(Locale.US)
        TimelineBucket(
            label = label,
            startMillis = bucketStart.toEpochMilli(),
            endMillis = bucketEnd.toEpochMilli(),
            inclusiveEnd = false
        )
    }
}

private fun createWeekBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
    val startOfWeek = now.atZone(zoneId)
        .toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0 until 7).map { index ->
        val day = startOfWeek.plusDays(index.toLong())
        val start = day.atStartOfDay(zoneId).toInstant()
        val end = day.plusDays(1).atStartOfDay(zoneId).toInstant()
        TimelineBucket(
            label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US),
            startMillis = start.toEpochMilli(),
            endMillis = end.toEpochMilli(),
            inclusiveEnd = false
        )
    }
}

private fun createMonthBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
    val yearMonth = YearMonth.from(now.atZone(zoneId))
    val daysInMonth = yearMonth.lengthOfMonth()
    val bucketCount = 4
    return buildList {
        repeat(bucketCount) { index ->
            val startDay = index * 7 + 1
            if (startDay > daysInMonth) {
                return@repeat
            }
            val endDay = if (index == bucketCount - 1) {
                daysInMonth
            } else {
                minOf(startDay + 6, daysInMonth)
            }
            val start = yearMonth.atDay(startDay).atStartOfDay(zoneId).toInstant()
            val end = yearMonth.atDay(endDay).plusDays(1).atStartOfDay(zoneId).toInstant()
            add(
                TimelineBucket(
                    label = "Week ${index + 1}",
                    startMillis = start.toEpochMilli(),
                    endMillis = end.toEpochMilli(),
                    inclusiveEnd = false
                )
            )
        }
    }
}

private fun createYearBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
    val year = Year.from(now.atZone(zoneId))
    return (1..12).map { monthIndex ->
        val start = year.atMonth(monthIndex).atDay(1).atStartOfDay(zoneId).toInstant()
        val end = year.atMonth(monthIndex).atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant()
        TimelineBucket(
            label = year.atMonth(monthIndex).month.getDisplayName(TextStyle.SHORT, Locale.US),
            startMillis = start.toEpochMilli(),
            endMillis = end.toEpochMilli(),
            inclusiveEnd = false
        )
    }
}

private fun createAllTimeBuckets(
    zoneId: ZoneId,
    spans: List<PlaybackSpan>,
    fallbackStart: Long,
    now: Instant
): List<TimelineBucket> {
    val allSpans = if (spans.isEmpty()) listOf(PlaybackSpan(fallbackStart, fallbackStart)) else spans
    val minTimestamp = allSpans.minOfOrNull { it.startMillis } ?: fallbackStart
    val maxTimestamp = allSpans.maxOfOrNull { it.endMillis } ?: now.toEpochMilli()
    val startYear = Instant.ofEpochMilli(minTimestamp).atZone(zoneId).year
    val endYear = Instant.ofEpochMilli(maxTimestamp).atZone(zoneId).year
    if (startYear > endYear) return emptyList()
    return (startYear..endYear).map { yearValue ->
        val year = Year.of(yearValue)
        val start = year.atDay(1).atStartOfDay(zoneId).toInstant()
        val end = year.plusYears(1).atDay(1).atStartOfDay(zoneId).toInstant()
        TimelineBucket(
            label = yearValue.toString(),
            startMillis = start.toEpochMilli(),
            endMillis = end.toEpochMilli(),
            inclusiveEnd = false
        )
    }
}

internal fun StatsTimeRange.resolveBounds(
    events: List<PlaybackEvent>,
    nowMillis: Long,
    zoneId: ZoneId
): Pair<Long?, Long> {
    val nowInstant = Instant.ofEpochMilli(nowMillis)
    return when (this) {
        StatsTimeRange.DAY -> {
            val start = nowInstant.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to nowMillis
        }
        StatsTimeRange.WEEK -> {
            val start = nowInstant.atZone(zoneId)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            start to nowMillis
        }
        StatsTimeRange.MONTH -> {
            val start = YearMonth.from(nowInstant.atZone(zoneId))
                .atDay(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            start to nowMillis
        }
        StatsTimeRange.YEAR -> {
            val start = nowInstant.atZone(zoneId)
                .toLocalDate()
                .withDayOfYear(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            start to nowMillis
        }
        StatsTimeRange.ALL, StatsTimeRange.CUSTOM -> {
            val start = events.minOfOrNull { it.startMillis() }
            start to nowMillis
        }
    }
}
