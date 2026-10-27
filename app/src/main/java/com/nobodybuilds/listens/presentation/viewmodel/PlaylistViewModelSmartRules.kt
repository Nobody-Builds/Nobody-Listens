package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.DailyMixManager
import com.nobodybuilds.listens.data.model.SmartPlaylistRule
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import java.util.concurrent.TimeUnit

internal suspend fun buildSmartPlaylistSongIds(
    musicRepository: MusicRepository,
    dailyMixManager: DailyMixManager,
    rule: SmartPlaylistRule,
    limit: Int
): List<String> {
    val allSongs = musicRepository.getAllSongsOnce()
    if (allSongs.isEmpty()) return emptyList()

    val engagements = dailyMixManager.getAllEngagementStats()
    val now = System.currentTimeMillis()
    val songById = allSongs.associateBy { it.id }
    val favoriteIds = musicRepository.getFavoriteSongIdsOnce()
    val safeLimit = limit.coerceAtLeast(1).coerceAtMost(allSongs.size)

    val pickedSongs = when (rule) {
        SmartPlaylistRule.TOP_PLAYED -> {
            engagements.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, DailyMixManager.SongEngagementStats>> { it.value.playCount }
                        .thenByDescending { it.value.totalPlayDurationMs }
                        .thenByDescending { it.value.lastPlayedTimestamp }
                )
                .mapNotNull { (songId, _) -> songById[songId] }
                .take(safeLimit)
        }

        SmartPlaylistRule.RECENTLY_PLAYED -> {
            engagements.entries
                .filter { it.value.lastPlayedTimestamp > 0L }
                .sortedByDescending { it.value.lastPlayedTimestamp }
                .mapNotNull { (songId, _) -> songById[songId] }
                .take(safeLimit)
        }

        SmartPlaylistRule.FORGOTTEN_FAVORITES -> {
            val staleThreshold = now - TimeUnit.DAYS.toMillis(30)
            allSongs
                .asSequence()
                .filter { favoriteIds.contains(it.id) }
                .sortedWith(
                    compareBy<Song> { engagements[it.id]?.lastPlayedTimestamp ?: 0L }
                        .thenBy { it.title.lowercase() }
                )
                .filter { song ->
                    (engagements[song.id]?.lastPlayedTimestamp ?: 0L) < staleThreshold
                }
                .take(safeLimit)
                .toList()
        }

        SmartPlaylistRule.NEW_GEMS -> {
            allSongs
                .asSequence()
                .sortedWith(
                    compareByDescending<Song> { it.dateAdded }
                        .thenBy { engagements[it.id]?.playCount ?: 0 }
                )
                .filter { song -> (engagements[song.id]?.playCount ?: 0) <= 2 }
                .take(safeLimit)
                .toList()
        }
    }

    if (pickedSongs.isNotEmpty()) {
        return pickedSongs.map { it.id }.distinct()
    }

    return allSongs
        .sortedByDescending { it.dateAdded }
        .take(safeLimit)
        .map { it.id }
}
