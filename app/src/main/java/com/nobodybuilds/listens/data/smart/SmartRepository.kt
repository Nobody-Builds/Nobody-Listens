package com.nobodybuilds.listens.data.smart

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.SongFeaturesDao
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

data class IndexProgress(
    val analyzed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val running: Boolean = false,
    val lastIndexedAt: Long = 0L,
) {
    val fraction: Float get() = if (total <= 0) 0f else (analyzed + failed).toFloat().div(total).coerceIn(0f, 1f)
}

data class SongSmartTags(val moods: List<String>, val genres: List<String>)

data class SmartInsights(
    /** Share of listening per mood, strongest first. */
    val moodMix: Map<String, Float>,
    /** Average 0..1 energy of what you played, per hour of day (null = no plays that hour). */
    val energyByHour: List<Float?>,
    /** Share of plays per genre tag, top 5. */
    val topGenres: List<Pair<String, Float>>,
    /** Share of plays of songs first played in this period. */
    val discoveryRate: Float,
    /** Cosine distance between this period's and the previous period's taste centroid. */
    val tasteDrift: Float?,
    val tasteDriftLabel: String,
    /** Share of the period's plays that have smart data. */
    val coverage: Float,
    val playCount: Int,
)

/**
 * Entry point for the experimental on-device smart layer. Everything is computed locally from
 * the `song_features` table and the play history; nothing leaves the device.
 */
@Singleton
class SmartRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SongFeaturesDao,
    private val preferences: SmartPreferences,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicDao: MusicDao,
) {
    private val gson = Gson()
    private val mutex = Mutex()

    private var cachedVectors: List<SongVector>? = null
    /** (analysed song count, newest analyzedAt): changes when songs are added, removed or re-analysed. */
    private var cachedVectorsKey: Pair<Int, Long>? = null
    private var memoryMixes: Pair<Long, List<SmartMix>>? = null

    val isEnabled: Flow<Boolean> = preferences.enabled

    val indexProgress: Flow<IndexProgress> = combine(
        dao.observeAnalyzedCount(SmartModel.MODEL_VERSION),
        dao.observeFailedCount(SmartModel.MODEL_VERSION),
        dao.observeLocalSongCount(),
        WorkManager.getInstance(context).getWorkInfosByTagFlow(SmartIndexScheduler.UNIQUE_PERIODIC),
        preferences.lastIndexedAt,
    ) { analyzed, failed, total, infos, last ->
        IndexProgress(
            analyzed = analyzed,
            total = total,
            failed = failed,
            running = infos.any { it.state == WorkInfo.State.RUNNING },
            lastIndexedAt = last,
        )
    }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
        if (enabled) SmartIndexScheduler.schedule(context) else SmartIndexScheduler.cancel(context)
    }

    /** Re-registers the periodic work if the feature is on (e.g. after an app update). */
    suspend fun ensureScheduled() {
        if (preferences.isEnabled()) SmartIndexScheduler.schedule(context)
    }

    suspend fun deleteSmartData() = withContext(Dispatchers.IO) {
        SmartIndexScheduler.cancel(context)
        dao.clearAll()
        preferences.clearCaches()
        mutex.withLock {
            cachedVectors = null
            cachedVectorsKey = null
            memoryMixes = null
        }
        // Keep indexing if the feature is still on.
        if (preferences.isEnabled()) SmartIndexScheduler.schedule(context)
    }

    // ------------------------------------------------------------------ data loading

    private suspend fun vectors(): List<SongVector> = mutex.withLock {
        val rows = dao.getAnalyzedSongRows(SmartModel.MODEL_VERSION)
        val key = rows.size to (dao.getMaxAnalyzedAt(SmartModel.MODEL_VERSION) ?: 0L)
        cachedVectors?.let { if (key == cachedVectorsKey) return@withLock it }
        val artistById = rows.associate { it.id.toString() to it.artistName }
        val features = dao.getAllAnalyzed(SmartModel.MODEL_VERSION)
        val inputs = features.mapNotNull { f ->
            val artist = artistById[f.songId] ?: return@mapNotNull null
            val bytes = f.scores ?: return@mapNotNull null
            FeatureInput(
                songId = f.songId,
                artist = artist,
                scores = SmartVectors.dequantize(bytes),
                rms = f.energy,
                bpm = f.tempo,
                zcr = f.brightness,
                moods = SmartTagger.splitTags(f.moodTags),
                genres = SmartTagger.splitTags(f.genreTags),
            )
        }
        val built = withContext(Dispatchers.Default) { SmartEngine.buildVectors(inputs) }
        cachedVectors = built
        cachedVectorsKey = key
        built
    }

    private class History(
        val events: List<PlaybackStatsRepository.PlaybackEvent>,
        val signals: List<PlaySignal>,
        val playCounts: Map<String, Int>,
        val favorites: Set<String>,
    )

    private suspend fun history(): History {
        val events = playbackStatsRepository.exportEventsForBackup()
        val skips = playbackStatsRepository.loadSkipEvents()
        val rows = dao.getAnalyzedSongRows(SmartModel.MODEL_VERSION)
        val durationById = rows.associate { it.id.toString() to it.duration }
        val favorites = rows.filter { it.isFavorite }.map { it.id.toString() }.toSet()
        val now = System.currentTimeMillis()
        val signals = ArrayList<PlaySignal>(events.size + skips.size + favorites.size)
        events.forEach { e ->
            val duration = durationById[e.songId] ?: 0L
            val finished = duration <= 0L || e.durationMs >= duration * 0.6
            signals += PlaySignal(e.songId, e.timestamp, if (finished) 1f else 0.4f)
        }
        skips.forEach { s -> signals += PlaySignal(s.songId, s.timestamp, -0.7f) }
        favorites.forEach { id -> signals += PlaySignal(id, now, 2f) }
        val counts = events.groupingBy { it.songId }.eachCount()
        return History(events, signals, counts, favorites)
    }

    // ------------------------------------------------------------------ public API

    suspend fun tagsFor(songId: String): SongSmartTags? = withContext(Dispatchers.IO) {
        val f = dao.get(songId) ?: return@withContext null
        if (f.status != 0 || f.modelVersion != SmartModel.MODEL_VERSION) return@withContext null
        SongSmartTags(SmartTagger.splitTags(f.moodTags), SmartTagger.splitTags(f.genreTags))
    }

    suspend fun isAnalyzed(songId: String): Boolean = tagsFor(songId) != null

    /** Song radio: ids of songs similar to [seedSongId] (seed excluded), diversity-ordered. */
    suspend fun similarSongs(seedSongId: String, limit: Int = 50): List<String> = withContext(Dispatchers.IO) {
        if (!preferences.isEnabled()) return@withContext emptyList()
        val all = vectors()
        val seed = all.firstOrNull { it.songId == seedSongId } ?: return@withContext emptyList()
        withContext(Dispatchers.Default) { SmartEngine.similar(seed, all, limit).map { it.songId } }
    }

    /** Resolves song ids to library songs, keeping the order. */
    suspend fun songsFor(ids: List<String>): List<Song> = withContext(Dispatchers.IO) {
        val longIds = ids.mapNotNull { it.toLongOrNull() }
        if (longIds.isEmpty()) return@withContext emptyList()
        val byId = longIds.chunked(500).flatMap { musicDao.getSongsByIdsListSimple(it) }
            .associateBy { it.id.toString() }
        ids.mapNotNull { byId[it]?.toSong() }
    }

    /** Auto playlists, computed on demand and cached for a day. Empty when the feature is off. */
    suspend fun mixes(forceRefresh: Boolean = false): List<SmartMix> = withContext(Dispatchers.IO) {
        if (!preferences.isEnabled()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            memoryMixes?.let { (at, list) -> if (now - at < DAY_MS && list.isNotEmpty()) return@withContext list }
            preferences.cachedMixes()?.let { (json, at) ->
                if (now - at < DAY_MS) {
                    val parsed = runCatching {
                        gson.fromJson<List<SmartMix>>(json, object : TypeToken<List<SmartMix>>() {}.type)
                    }.getOrNull()
                    if (!parsed.isNullOrEmpty()) {
                        memoryMixes = at to parsed
                        return@withContext parsed
                    }
                }
            }
        }
        val computed = try {
            computeMixes(now)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Timber.e(t, "Smart: mix computation failed")
            emptyList()
        }
        if (computed.isNotEmpty()) {
            memoryMixes = now to computed
            preferences.setCachedMixes(gson.toJson(computed), now)
        }
        computed
    }

    private suspend fun computeMixes(now: Long): List<SmartMix> {
        val all = vectors()
        if (all.size < SmartEngine.MIN_MIX) return emptyList()
        val h = history()
        return withContext(Dispatchers.Default) {
            val byId = all.associateBy { it.songId }
            val taste = SmartEngine.tasteCentroid(byId, h.signals, now)
            val weights = SmartEngine.songWeights(h.signals, now)
            val liked = all
                .filter { (weights[it.songId] ?: 0f) > 0.5f || it.songId in h.favorites }
                .sortedByDescending { weights[it.songId] ?: 0f }
                .take(400)
            buildList {
                addAll(SmartEngine.dailyMixes(liked, all))
                addAll(SmartEngine.moodMixes(all, taste))
                SmartEngine.discover(all, taste, h.playCounts)?.let { add(it) }
            }
        }
    }

    suspend fun moodBreakdown(songPlayCounts: Map<String, Int>): Map<String, Float> = withContext(Dispatchers.IO) {
        val moods = vectors().associate { it.songId to it.moods }
        SmartEngine.moodBreakdown(moods, songPlayCounts)
    }

    suspend fun insights(range: StatsTimeRange, nowMillis: Long = System.currentTimeMillis()): SmartInsights =
        withContext(Dispatchers.IO) {
            val all = vectors()
            val byId = all.associateBy { it.songId }
            val events = playbackStatsRepository.exportEventsForBackup()
            val zone = ZoneId.systemDefault()
            val (start, end) = bounds(range, events, nowMillis, zone)
            val length = (end - start).coerceAtLeast(DAY_MS)
            val prevStart = start - length

            withContext(Dispatchers.Default) {
                val inPeriod = events.filter { it.timestamp in start..end }
                val inPrevious = events.filter { it.timestamp in prevStart until start }
                val counts = inPeriod.groupingBy { it.songId }.eachCount()

                val moodMix = SmartEngine.moodBreakdown(byId.mapValues { it.value.moods }, counts)

                val energySum = DoubleArray(24)
                val energyCount = IntArray(24)
                inPeriod.forEach { e ->
                    val v = byId[e.songId] ?: return@forEach
                    val startTs = e.startTimestamp ?: (e.timestamp - e.durationMs)
                    val hour = Instant.ofEpochMilli(startTs).atZone(zone).hour
                    energySum[hour] += v.energy.toDouble()
                    energyCount[hour]++
                }
                val energyByHour = (0 until 24).map { h ->
                    if (energyCount[h] == 0) null else (energySum[h] / energyCount[h]).toFloat()
                }

                val genreTotals = HashMap<String, Float>()
                var genreSum = 0f
                inPeriod.forEach { e ->
                    val v = byId[e.songId] ?: return@forEach
                    v.genres.forEachIndexed { i, g ->
                        val w = if (i == 0) 1f else 0.5f
                        genreTotals[g] = (genreTotals[g] ?: 0f) + w
                        genreSum += w
                    }
                }
                val topGenres = genreTotals.entries.sortedByDescending { it.value }.take(5)
                    .map { it.key to if (genreSum > 0f) it.value / genreSum else 0f }

                val firstPlay = HashMap<String, Long>()
                events.forEach { e ->
                    val cur = firstPlay[e.songId]
                    if (cur == null || e.timestamp < cur) firstPlay[e.songId] = e.timestamp
                }
                val discovered = inPeriod.count { (firstPlay[it.songId] ?: Long.MAX_VALUE) >= start }
                val discoveryRate = if (inPeriod.isEmpty()) 0f else discovered.toFloat() / inPeriod.size

                val current = SmartEngine.centroidOf(byId, inPeriod.map { it.songId })
                val previous = SmartEngine.centroidOf(byId, inPrevious.map { it.songId })
                val drift = SmartEngine.drift(current, previous)

                val covered = inPeriod.count { byId.containsKey(it.songId) }
                SmartInsights(
                    moodMix = moodMix,
                    energyByHour = energyByHour,
                    topGenres = topGenres,
                    discoveryRate = discoveryRate,
                    tasteDrift = drift,
                    tasteDriftLabel = SmartEngine.driftLabel(drift),
                    coverage = if (inPeriod.isEmpty()) 0f else covered.toFloat() / inPeriod.size,
                    playCount = inPeriod.size,
                )
            }
        }

    private fun bounds(
        range: StatsTimeRange,
        events: List<PlaybackStatsRepository.PlaybackEvent>,
        now: Long,
        zone: ZoneId,
    ): Pair<Long, Long> {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = when (range) {
            StatsTimeRange.DAY -> date.atStartOfDay(zone).toInstant().toEpochMilli()
            StatsTimeRange.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone).toInstant().toEpochMilli()
            StatsTimeRange.MONTH -> YearMonth.from(date).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            StatsTimeRange.YEAR -> date.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
            StatsTimeRange.ALL -> events.minOfOrNull { it.timestamp } ?: (now - 30 * DAY_MS)
            StatsTimeRange.CUSTOM -> now - 30 * DAY_MS
        }
        return start to now
    }

    companion object {
        private val DAY_MS = TimeUnit.DAYS.toMillis(1)
    }
}
