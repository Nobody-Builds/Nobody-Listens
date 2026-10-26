package com.nobodybuilds.listens.presentation.recap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.AlbumPlaybackSummary
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.ArtistPlaybackSummary
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.SongPlaybackSummary
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class RecapPeriod(val routeValue: String, val days: Long) {
    WEEK("week", 7),
    MONTH("month", 30);

    companion object {
        fun fromRoute(value: String?): RecapPeriod = entries.firstOrNull { it.routeValue == value } ?: WEEK
    }
}

/** Everything a recap shows. Built only when the user opens one. */
data class RecapData(
    val period: RecapPeriod,
    val startMillis: Long,
    val endMillis: Long,
    val totalDurationMs: Long,
    val previousDurationMs: Long,
    val plays: Int,
    val uniqueSongs: Int,
    val topSongs: List<SongPlaybackSummary>,
    val topArtists: List<ArtistPlaybackSummary>,
    /** Artwork to show for the top artist (from their most played song). */
    val topArtistArt: String?,
    val topAlbum: AlbumPlaybackSummary?,
    val busiestDay: LocalDate?,
    val busiestDayDurationMs: Long,
    /** Hour of day (0–23) with the most listening. */
    val peakHour: Int?,
    val activeDays: Int,
    val longestStreakDays: Int,
    val newSongCount: Int,
    val newArtistCount: Int,
    val skipCount: Int,
    val startedCount: Int,
    val skipRate: Float,
    // Filled from the on-device smart layer when it is on and has analysed the songs played.
    val moods: Map<String, Float>? = null,
    val genres: List<String>? = null,
) {
    /** Change in listening time vs the period before, in percent; null when there's nothing to compare. */
    val changePercent: Int?
        get() = if (previousDurationMs <= 0L) null
        else (((totalDurationMs - previousDurationMs).toDouble() / previousDurationMs) * 100).roundToInt()

    val hasListening: Boolean get() = totalDurationMs > 0L && plays > 0
}

sealed interface RecapUiState {
    data object Loading : RecapUiState
    data class Ready(val data: RecapData) : RecapUiState
    data object Failed : RecapUiState
}

@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository,
    private val smartRepository: com.nobodybuilds.listens.data.smart.SmartRepository,
) : ViewModel() {

    val period: RecapPeriod = RecapPeriod.fromRoute(savedStateHandle.get<String>("period"))

    private val _state = MutableStateFlow<RecapUiState>(RecapUiState.Loading)
    val state: StateFlow<RecapUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = RecapUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { withContext(Dispatchers.Default) { build() } }
                .onFailure { Timber.w(it, "Recap: failed to build") }
                .map { RecapUiState.Ready(it) as RecapUiState }
                .getOrDefault(RecapUiState.Failed)
        }
    }

    private suspend fun build(): RecapData {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        // Last 7 / 30 days, ending now (today included).
        val start = today.minusDays(period.days - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val previousStart = today.minusDays(period.days * 2 - 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val songs = withContext(Dispatchers.IO) { musicRepository.getAllSongsOnce() }
        val summary = playbackStatsRepository.loadSummary(
            range = StatsTimeRange.CUSTOM,
            songs = songs,
            nowMillis = now,
            customStartMillis = start,
            customEndMillis = now,
        )
        val previous = playbackStatsRepository.loadSummary(
            range = StatsTimeRange.CUSTOM,
            songs = songs,
            nowMillis = now,
            customStartMillis = previousStart,
            customEndMillis = start - 1,
        )

        val busiest = summary.dayListeningDistribution?.days?.maxByOrNull { it.totalDurationMs }?.takeIf { it.totalDurationMs > 0 }
        val peakHour = summary.dayListeningDistribution?.buckets
            ?.groupBy { it.startMinute / 60 }
            ?.mapValues { (_, buckets) -> buckets.sumOf { it.totalDurationMs } }
            ?.maxByOrNull { it.value }
            ?.key

        val smartOn = runCatching { smartRepository.isEnabled.first() }.getOrDefault(false)
        val moods = if (smartOn) runCatching {
            smartRepository.moodBreakdown(summary.songs.associate { it.songId to it.playCount })
        }.getOrNull()?.takeIf { it.isNotEmpty() } else null
        val genres = if (smartOn) runCatching {
            smartRepository.insights(StatsTimeRange.CUSTOM).topGenres.map { it.first }
        }.getOrNull()?.takeIf { it.isNotEmpty() } else null

        val topArtist = summary.topArtists.firstOrNull()
        val topArtistArt = topArtist?.let { artist ->
            summary.songs.firstOrNull { it.artist.contains(artist.artist, ignoreCase = true) && !it.albumArtUri.isNullOrBlank() }?.albumArtUri
        }

        return RecapData(
            period = period,
            startMillis = start,
            endMillis = now,
            totalDurationMs = summary.totalDurationMs,
            previousDurationMs = previous.totalDurationMs,
            plays = summary.totalPlayCount,
            uniqueSongs = summary.uniqueSongs,
            topSongs = summary.topSongs.take(5),
            topArtists = summary.topArtists.take(5),
            topArtistArt = topArtistArt,
            topAlbum = summary.topAlbums.firstOrNull(),
            busiestDay = busiest?.date,
            busiestDayDurationMs = busiest?.totalDurationMs ?: 0L,
            peakHour = peakHour,
            activeDays = summary.activeDays,
            longestStreakDays = summary.longestStreakDays,
            newSongCount = summary.newSongCount,
            newArtistCount = summary.newArtistCount,
            skipCount = summary.skipCount,
            startedCount = summary.startedCount,
            skipRate = summary.skipRate,
            moods = moods,
            genres = genres,
        )
    }
}
