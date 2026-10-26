package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Favourite (liked) songs for PlayerViewModel: the id set, toggles and batch like/unlike. */
internal class PlayerFavoritesActions(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val playbackStateHolder: PlaybackStateHolder,
    private val multiSelectionStateHolder: MultiSelectionStateHolder
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _toastEvents get() = shared.toastEvents
    private val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState

    val favoriteSongIds: StateFlow<Set<String>> = musicRepository
        .getFavoriteSongIdsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isCurrentSongFavorite: StateFlow<Boolean> = combine(
        stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged(),
        favoriteSongIds
    ) { songId, ids ->
        songId?.let { ids.contains(it) } ?: false
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Observes a song by ID from Room DB, combined with the latest favorite status.
     * Uses direct Room query instead of scanning the full in-memory list.
     */
    fun observeSong(songId: String?): Flow<Song?> {
        if (songId == null) return flowOf(null)
        return combine(
            musicRepository.getSong(songId),
            favoriteSongIds
        ) { song, favorites ->
            song?.copy(isFavorite = favorites.contains(songId))
        }.distinctUntilChanged()
    }

    private suspend fun setFavoriteStatusEverywhere(songId: String, isFavorite: Boolean) {
        musicRepository.setFavoriteStatus(songId, isFavorite)
    }

    fun toggleFavorite() {
        playbackStateHolder.stablePlayerState.value.currentSong?.id?.let { songId ->
            scope.launch {
                val currentlyFavorite = favoriteSongIds.value.contains(songId)
                setFavoriteStatusEverywhere(songId, !currentlyFavorite)
            }
        }
    }

    fun toggleFavoriteSpecificSong(song: Song, removing: Boolean = false) {
        scope.launch {
            val currentlyFavorite = favoriteSongIds.value.contains(song.id)
            val targetFavoriteState = if (removing) false else !currentlyFavorite
            setFavoriteStatusEverywhere(song.id, targetFavoriteState)
        }
    }

    /**
     * Adds all selected songs to favorites.
     * Clears selection after liking.
     */
    fun likeSelectedSongs(songs: List<Song>) {
        scope.launch {
            val favIds = favoriteSongIds.value.toMutableSet()
            var likedCount = 0
            songs.forEach { song ->
                if (!favIds.contains(song.id)) {
                    setFavoriteStatusEverywhere(song.id, true)
                    favIds.add(song.id)
                    likedCount++
                }
            }
            if (likedCount > 0) {
                _toastEvents.emit(
                    context.resources.getQuantityString(R.plurals.n_songs_added_to_favorites, likedCount, likedCount),
                )
            } else {
                _toastEvents.emit(context.getString(R.string.player_all_songs_already_in_favorites))
            }
            multiSelectionStateHolder.clearSelection()
        }
    }

    /**
     * Removes all selected songs from favorites.
     * Clears selection after unliking.
     */
    fun unlikeSelectedSongs(songs: List<Song>) {
        scope.launch {
            val favIds = favoriteSongIds.value.toMutableSet()
            var unlikedCount = 0
            songs.forEach { song ->
                if (favIds.contains(song.id)) {
                    setFavoriteStatusEverywhere(song.id, false)
                    favIds.remove(song.id)
                    unlikedCount++
                }
            }
            if (unlikedCount > 0) {
                _toastEvents.emit(
                    context.resources.getQuantityString(
                        R.plurals.n_songs_removed_from_favorites,
                        unlikedCount,
                        unlikedCount,
                    ),
                )
            } else {
                _toastEvents.emit(context.getString(R.string.player_no_songs_were_in_favorites))
            }
            multiSelectionStateHolder.clearSelection()
        }
    }
}
