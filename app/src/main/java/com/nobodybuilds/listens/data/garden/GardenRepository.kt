package com.nobodybuilds.listens.data.garden

import com.nobodybuilds.listens.data.focus.FocusRepository
import com.nobodybuilds.listens.data.smart.SmartRepository
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import timber.log.Timber

data class GardenSnapshot(
    val today: LocalDate,
    val listeningDays: Set<LocalDate>,
    val firstDay: LocalDate?,
    val gardenDay: Int,
    val growth: GardenGrowthState,
    val seed: Long,
    val season: GardenSeason,
    val todayCounted: Boolean,
)

/**
 * Reads listening days from the play history and focus rounds from [FocusRepository], and turns
 * them into a [GardenSnapshot]. No work in the constructor; everything is computed on collection.
 */
@Singleton
class GardenRepository @Inject constructor(
    private val statsRepository: PlaybackStatsRepository,
    private val focusRepository: FocusRepository,
    private val smartRepository: SmartRepository,
    val preferences: GardenPreferences,
) {

    private fun focusRounds(): Flow<Int> = focusRepository.completedRounds
        .catch { Timber.w(it, "Garden: focus rounds unavailable"); emit(0) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshots: Flow<GardenSnapshot>
        get() = combine(statsRepository.refreshFlow, focusRounds()) { _, rounds -> rounds }
            .mapLatest { rounds -> compute(rounds) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** One-shot read for the widget and workers. */
    suspend fun snapshot(): GardenSnapshot = withContext(Dispatchers.IO) {
        val rounds = runCatching { focusRounds().first() }.getOrDefault(0)
        compute(rounds)
    }

    private suspend fun compute(focusRounds: Int): GardenSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val spans = statsRepository.exportEventsForBackup().map { e ->
            val end = e.endTimestamp ?: e.timestamp
            val start = e.startTimestamp ?: (end - e.durationMs)
            ListenSpan(start, end)
        }
        val days = GardenGrowth.listeningDays(spans, zone)
        val first = days.minOrNull()
        return GardenSnapshot(
            today = today,
            listeningDays = days,
            firstDay = first,
            gardenDay = GardenGrowth.gardenDay(first, today),
            growth = GardenGrowth.growth(days.size, focusRounds),
            seed = GardenGrowth.plantSeed(first),
            season = GardenGrowth.season(today.monthValue),
            todayCounted = today in days,
        )
    }

    /**
     * Mood mix of recent listening (last 60 days) when Smart features are on; empty otherwise.
     * Keys are SmartModel mood names ("Calm", "Upbeat", ...).
     */
    suspend fun recentMoodMix(): Map<String, Float> = withContext(Dispatchers.IO) {
        runCatching {
            if (!smartRepository.isEnabled.first()) return@runCatching emptyMap()
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60)
            val counts = statsRepository.exportEventsForBackup()
                .filter { it.timestamp >= cutoff }
                .groupingBy { it.songId }
                .eachCount()
            if (counts.isEmpty()) emptyMap() else smartRepository.moodBreakdown(counts)
        }.onFailure { Timber.w(it, "Garden: mood mix failed") }.getOrDefault(emptyMap())
    }
}
