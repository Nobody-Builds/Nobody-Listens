package com.nobodybuilds.listens.data.garden

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.max

/**
 * Listening garden — a small plant that grows on the days you listen. It only ever grows:
 * missed days cost nothing, and nothing here models streaks, decay or loss.
 */
enum class GardenStage(val title: String, val threshold: Double, val hintName: String) {
    Seed("Seed", 0.0, "a seed"),
    Sprout("Sprout", 1.0, "a sprout"),
    Seedling("Seedling", 3.0, "a seedling"),
    YoungPlant("Young plant", 6.0, "a young plant"),
    LeafyPlant("Leafy plant", 10.0, "a leafy plant"),
    FirstBud("First bud", 15.0, "the first bud"),
    Bloom("Bloom", 22.0, "the first bloom"),
    Flowering("Flowering", 30.0, "flowering"),
    FullBloom("Full bloom", 45.0, "full bloom"),
    LittleTree("Little tree", 70.0, "a little tree");

    val next: GardenStage? get() = entries.getOrNull(ordinal + 1)
}

enum class GardenSeason { Spring, Monsoon, Autumn, Winter }

/** A listening span in epoch millis. */
data class ListenSpan(val startMillis: Long, val endMillis: Long)

data class GardenGrowthState(
    val listeningDays: Int,
    val focusRounds: Int,
    val points: Double,
    val stage: GardenStage,
    /** 0..1 progress from the current stage threshold to the next one (1 for the last stage). */
    val stageProgress: Float,
    /** Listening days still needed to reach [GardenStage.next]; null at the last stage. */
    val daysToNextStage: Int?,
    /** 0..1 how grown the little tree is (keeps filling out up to [GardenGrowth.TREE_FULL_POINTS]). */
    val treeMaturity: Float,
    val milestonesReached: List<Int>,
)

object GardenGrowth {
    const val MIN_LISTENING_MS: Long = 5 * 60 * 1000L
    const val POINTS_PER_DAY = 1.0
    const val POINTS_PER_FOCUS_ROUND = 0.5
    const val TREE_FULL_POINTS = 100.0
    val MILESTONES = listOf(7, 30, 100)

    /**
     * Days on which at least [minMs] were listened. Overlapping spans are merged first so a
     * double-recorded play is not counted twice, and spans crossing midnight are split.
     */
    fun listeningDays(spans: List<ListenSpan>, zone: ZoneId, minMs: Long = MIN_LISTENING_MS): Set<LocalDate> {
        val totals = dailyTotals(spans, zone)
        return totals.filterValues { it >= minMs }.keys
    }

    fun dailyTotals(spans: List<ListenSpan>, zone: ZoneId): Map<LocalDate, Long> {
        val merged = mergeSpans(spans)
        val totals = HashMap<LocalDate, Long>()
        for (span in merged) {
            var start = span.startMillis
            val end = span.endMillis
            while (start < end) {
                val date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val sliceEnd = minOf(end, nextMidnight)
                totals[date] = (totals[date] ?: 0L) + (sliceEnd - start)
                start = sliceEnd
            }
        }
        return totals
    }

    internal fun mergeSpans(spans: List<ListenSpan>): List<ListenSpan> {
        val valid = spans.filter { it.endMillis > it.startMillis && it.startMillis >= 0 }.sortedBy { it.startMillis }
        if (valid.isEmpty()) return emptyList()
        val out = ArrayList<ListenSpan>(valid.size)
        var cur = valid.first()
        for (i in 1 until valid.size) {
            val s = valid[i]
            cur = if (s.startMillis <= cur.endMillis) {
                ListenSpan(cur.startMillis, max(cur.endMillis, s.endMillis))
            } else {
                out += cur
                s
            }
        }
        out += cur
        return out
    }

    fun points(listeningDays: Int, focusRounds: Int): Double =
        listeningDays.coerceAtLeast(0) * POINTS_PER_DAY + focusRounds.coerceAtLeast(0) * POINTS_PER_FOCUS_ROUND

    fun stageFor(points: Double): GardenStage =
        GardenStage.entries.last { points >= it.threshold }

    fun growth(listeningDays: Int, focusRounds: Int): GardenGrowthState {
        val p = points(listeningDays, focusRounds)
        val stage = stageFor(p)
        val next = stage.next
        val progress = if (next == null) 1f
        else ((p - stage.threshold) / (next.threshold - stage.threshold)).toFloat().coerceIn(0f, 1f)
        val daysToNext = next?.let { ceil((it.threshold - p) / POINTS_PER_DAY - 1e-9).toInt().coerceAtLeast(1) }
        val maturity = if (stage == GardenStage.LittleTree) {
            ((p - stage.threshold) / (TREE_FULL_POINTS - stage.threshold)).toFloat().coerceIn(0f, 1f)
        } else 0f
        return GardenGrowthState(
            listeningDays = listeningDays,
            focusRounds = focusRounds,
            points = p,
            stage = stage,
            stageProgress = progress,
            daysToNextStage = daysToNext,
            treeMaturity = maturity,
            milestonesReached = MILESTONES.filter { listeningDays >= it },
        )
    }

    /** "2 more listening days to the first bud", or a gentle line at the last stage. */
    fun nextStageHint(state: GardenGrowthState): String {
        val next = state.stage.next ?: return "Your little tree keeps filling out as you listen"
        val days = state.daysToNextStage ?: 1
        val unit = if (days == 1) "listening day" else "listening days"
        return "$days more $unit to ${next.hintName}"
    }

    /** Day N of the garden: the first listening day is day 1. 0 before the first listening day. */
    fun gardenDay(firstDay: LocalDate?, today: LocalDate): Int {
        if (firstDay == null) return 0
        return (ChronoUnit.DAYS.between(firstDay, today) + 1).toInt().coerceAtLeast(1)
    }

    fun season(month: Int): GardenSeason = when (month) {
        3, 4, 5 -> GardenSeason.Spring
        6, 7, 8, 9 -> GardenSeason.Monsoon
        10, 11 -> GardenSeason.Autumn
        else -> GardenSeason.Winter
    }

    /**
     * The last [weeks] weeks as columns (Monday first). Each column has 7 cells; days after
     * [today] are null so the grid never shows the future.
     */
    fun calendarWeeks(today: LocalDate, weeks: Int = 8): List<List<LocalDate?>> {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = thisMonday.minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w ->
            val monday = firstMonday.plusWeeks(w.toLong())
            (0 until 7).map { d -> monday.plusDays(d.toLong()).takeIf { !it.isAfter(today) } }
        }
    }

    /** Milestones reached but not yet celebrated. */
    fun newMilestones(reached: List<Int>, shown: Set<Int>): List<Int> = reached.filter { it !in shown }

    /** A stable seed for the procedural plant: the first listening day, so the plant never changes shape. */
    fun plantSeed(firstDay: LocalDate?): Long = firstDay?.toEpochDay()?.times(2_654_435_761L) ?: 7_919L
}
