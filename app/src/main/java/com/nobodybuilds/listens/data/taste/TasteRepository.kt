package com.nobodybuilds.listens.data.taste

import android.content.Context
import android.provider.Settings
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.smart.SmartRepository
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Builds your taste fingerprint from on-device listening data. Everything stays offline. */
@Singleton
class TasteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository,
    private val smartRepository: SmartRepository,
) {

    data class MyTaste(
        val fingerprint: TasteFingerprint,
        /** True when built from play history (false = library fallback for a fresh install). */
        val fromListening: Boolean,
    )

    suspend fun librarySongs(): List<Song> = withContext(Dispatchers.IO) { musicRepository.getAllSongsOnce() }

    fun defaultName(): String = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()?.takeIf { it.isNotBlank() }?.take(40) ?: "Friend"

    suspend fun buildMyTaste(name: String, songs: List<Song>): MyTaste = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val yearAgo = now - 365L * 24 * 60 * 60 * 1000
        var summary = playbackStatsRepository.loadSummary(
            range = StatsTimeRange.CUSTOM,
            songs = songs,
            nowMillis = now,
            customStartMillis = yearAgo,
            customEndMillis = now,
        )
        if (summary.songs.isEmpty()) {
            summary = playbackStatsRepository.loadSummary(StatsTimeRange.ALL, songs, now)
        }
        val byId = songs.associateBy { it.id }

        if (summary.songs.isEmpty()) {
            // Nothing played yet: describe the library instead (artists by song count, favourites first).
            val artistCounts = HashMap<String, Double>()
            songs.forEach { s -> artistNames(s).forEachIndexed { i, a -> artistCounts[a] = (artistCounts[a] ?: 0.0) + if (i == 0) 1.0 else 0.5 } }
            val top = songs.sortedByDescending { it.isFavorite }.take(TasteFingerprintBuilder.MAX_SONGS)
                .map { SongRef(it.title, artistNames(it).firstOrNull() ?: it.artist) }
            return@withContext MyTaste(TasteFingerprintBuilder.build(name, artistCounts, top), fromListening = false)
        }

        val artistMs = HashMap<String, Double>()
        summary.songs.forEach { s ->
            val names = byId[s.songId]?.let(::artistNames) ?: listOf(s.artist)
            names.forEachIndexed { i, a ->
                if (isUnknown(a)) return@forEachIndexed
                artistMs[a] = (artistMs[a] ?: 0.0) + s.totalDurationMs * if (i == 0) 1.0 else 0.5
            }
        }
        val topSongs = summary.songs
            .sortedWith(compareByDescending<PlaybackStatsRepository.SongPlaybackSummary> { it.playCount }.thenByDescending { it.totalDurationMs })
            .take(TasteFingerprintBuilder.MAX_SONGS * 2)
            .map { s -> SongRef(s.title, byId[s.songId]?.let(::artistNames)?.firstOrNull() ?: s.artist) }

        var moods: Map<String, Float> = emptyMap()
        var genres: Map<String, Float> = emptyMap()
        val smartOn = runCatching { smartRepository.isEnabled.first() }.getOrDefault(false)
        if (smartOn) {
            moods = runCatching { smartRepository.moodBreakdown(summary.songs.associate { it.songId to it.playCount }) }
                .onFailure { Timber.w(it, "Taste: mood mix failed") }.getOrDefault(emptyMap())
            genres = runCatching { smartRepository.insights(StatsTimeRange.ALL, now).topGenres.toMap() }
                .onFailure { Timber.w(it, "Taste: genre mix failed") }.getOrDefault(emptyMap())
        }
        MyTaste(TasteFingerprintBuilder.build(name, artistMs, topSongs, genres, moods), fromListening = true)
    }

    private fun artistNames(song: Song): List<String> {
        val names = if (song.artists.isNotEmpty()) {
            song.artists.sortedByDescending { it.isPrimary }.map { it.name }
        } else listOf(song.artist)
        return names.map { it.trim() }.filter { it.isNotEmpty() && !isUnknown(it) }.distinct()
    }

    private fun isUnknown(name: String): Boolean {
        val n = name.trim().lowercase()
        return n.isEmpty() || n == "<unknown>" || n == "unknown" || n == "unknown artist" || n == "various artists"
    }

    companion object {
        fun libraryKeys(songs: List<Song>): List<LibrarySongKey> = songs.map { s ->
            val names = if (s.artists.isNotEmpty()) s.artists.map { it.name } else listOf(s.artist)
            LibrarySongKey(s.id, s.title, names + listOfNotNull(s.albumArtist))
        }
    }
}
