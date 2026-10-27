package com.nobodybuilds.listens.data.garden

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class GardenGrowthTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val min = 60_000L

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a day counts only with at least five minutes`() {
        val d1 = LocalDate.of(2026, 10, 1)
        val d2 = LocalDate.of(2026, 10, 2)
        val spans = listOf(
            ListenSpan(at(d1, 10), at(d1, 10) + 3 * min),
            ListenSpan(at(d1, 12), at(d1, 12) + 2 * min), // 5 min total on d1
            ListenSpan(at(d2, 9), at(d2, 9) + 4 * min),   // 4 min on d2
        )
        assertThat(GardenGrowth.listeningDays(spans, zone)).containsExactly(d1)
    }

    @Test
    fun `overlapping spans are not double counted`() {
        val d = LocalDate.of(2026, 10, 1)
        val spans = listOf(
            ListenSpan(at(d, 10), at(d, 10) + 3 * min),
            ListenSpan(at(d, 10), at(d, 10) + 3 * min),
        )
        assertThat(GardenGrowth.listeningDays(spans, zone)).isEmpty()
    }

    @Test
    fun `spans crossing midnight are split between days`() {
        val d = LocalDate.of(2026, 10, 1)
        val spans = listOf(ListenSpan(at(d, 23, 50), at(d, 23, 50) + 20 * min))
        val totals = GardenGrowth.dailyTotals(spans, zone)
        assertThat(totals[d]).isEqualTo(10 * min)
        assertThat(totals[d.plusDays(1)]).isEqualTo(10 * min)
    }

    @Test
    fun `points add focus bonus`() {
        assertThat(GardenGrowth.points(4, 3)).isWithin(1e-9).of(5.5)
    }

    @Test
    fun `stages follow thresholds`() {
        assertThat(GardenGrowth.stageFor(0.0)).isEqualTo(GardenStage.Seed)
        assertThat(GardenGrowth.stageFor(1.0)).isEqualTo(GardenStage.Sprout)
        assertThat(GardenGrowth.stageFor(2.5)).isEqualTo(GardenStage.Sprout)
        assertThat(GardenGrowth.stageFor(3.0)).isEqualTo(GardenStage.Seedling)
        assertThat(GardenGrowth.stageFor(15.0)).isEqualTo(GardenStage.FirstBud)
        assertThat(GardenGrowth.stageFor(69.5)).isEqualTo(GardenStage.FullBloom)
        assertThat(GardenGrowth.stageFor(500.0)).isEqualTo(GardenStage.LittleTree)
    }

    @Test
    fun `growth never decreases as days are added`() {
        var previous = -1
        for (days in 0..120) {
            val stage = GardenGrowth.growth(days, 0).stage.ordinal
            assertThat(stage).isAtLeast(previous)
            previous = stage
        }
    }

    @Test
    fun `next stage hint counts remaining days`() {
        val state = GardenGrowth.growth(13, 0)
        assertThat(state.daysToNextStage).isEqualTo(2)
        assertThat(GardenGrowth.nextStageHint(state)).isEqualTo("2 more listening days to the first bud")
        assertThat(GardenGrowth.nextStageHint(GardenGrowth.growth(0, 0))).isEqualTo("1 more listening day to a sprout")
    }

    @Test
    fun `half points round up to a whole day`() {
        val state = GardenGrowth.growth(13, 1) // 13.5 -> needs 1.5 -> 2 days
        assertThat(state.daysToNextStage).isEqualTo(2)
    }

    @Test
    fun `last stage has no next and tree matures towards 100`() {
        val state = GardenGrowth.growth(85, 0)
        assertThat(state.stage).isEqualTo(GardenStage.LittleTree)
        assertThat(state.daysToNextStage).isNull()
        assertThat(state.treeMaturity).isWithin(1e-4f).of(0.5f)
        assertThat(GardenGrowth.growth(200, 0).treeMaturity).isEqualTo(1f)
    }

    @Test
    fun `milestones and new milestones`() {
        val state = GardenGrowth.growth(31, 0)
        assertThat(state.milestonesReached).containsExactly(7, 30).inOrder()
        assertThat(GardenGrowth.newMilestones(state.milestonesReached, setOf(7))).containsExactly(30)
        assertThat(GardenGrowth.newMilestones(state.milestonesReached, setOf(7, 30))).isEmpty()
    }

    @Test
    fun `garden day starts at one`() {
        val first = LocalDate.of(2026, 10, 1)
        assertThat(GardenGrowth.gardenDay(null, first)).isEqualTo(0)
        assertThat(GardenGrowth.gardenDay(first, first)).isEqualTo(1)
        assertThat(GardenGrowth.gardenDay(first, LocalDate.of(2026, 10, 18))).isEqualTo(18)
    }

    @Test
    fun `seasons by month`() {
        assertThat(GardenGrowth.season(4)).isEqualTo(GardenSeason.Spring)
        assertThat(GardenGrowth.season(7)).isEqualTo(GardenSeason.Monsoon)
        assertThat(GardenGrowth.season(10)).isEqualTo(GardenSeason.Autumn)
        assertThat(GardenGrowth.season(1)).isEqualTo(GardenSeason.Winter)
        assertThat(GardenGrowth.season(12)).isEqualTo(GardenSeason.Winter)
    }

    @Test
    fun `calendar has eight weeks ending today without future days`() {
        val today = LocalDate.of(2026, 10, 18) // a Sunday
        val weeks = GardenGrowth.calendarWeeks(today)
        assertThat(weeks).hasSize(8)
        assertThat(weeks.last().last()).isEqualTo(today)
        val wed = LocalDate.of(2026, 10, 14)
        val midWeek = GardenGrowth.calendarWeeks(wed)
        assertThat(midWeek.last()[2]).isEqualTo(wed)
        assertThat(midWeek.last()[3]).isNull()
        assertThat(midWeek.first().first()!!.dayOfWeek).isEqualTo(java.time.DayOfWeek.MONDAY)
    }
}
