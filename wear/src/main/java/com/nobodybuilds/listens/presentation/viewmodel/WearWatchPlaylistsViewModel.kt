package com.nobodybuilds.listens.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.WatchPlaylist
import com.nobodybuilds.listens.data.WearLocalPlayerRepository
import com.nobodybuilds.listens.data.WearOutputTarget
import com.nobodybuilds.listens.data.WearPlaylistRepository
import com.nobodybuilds.listens.data.WearStateRepository
import com.nobodybuilds.listens.data.WearTransferRepository
import com.nobodybuilds.listens.data.local.LocalPlaylistSongEntity
import com.nobodybuilds.listens.data.local.LocalSongEntity
import com.nobodybuilds.listens.shared.WearLibraryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A saved playlist entry; [saved] is null until the song's file is on the watch. */
data class WatchPlaylistSong(val entry: LocalPlaylistSongEntity, val saved: LocalSongEntity?)

@HiltViewModel
class WearWatchPlaylistsViewModel @Inject constructor(
    private val playlistRepository: WearPlaylistRepository,
    private val transferRepository: WearTransferRepository,
    private val localPlayerRepository: WearLocalPlayerRepository,
    private val stateRepository: WearStateRepository,
) : ViewModel() {

    val playlists: StateFlow<List<WatchPlaylist>> = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savingPlaylistIds: StateFlow<Set<String>> = playlistRepository.savingPlaylistIds

    fun savePlaylist(playlistId: String, name: String, items: List<WearLibraryItem>) =
        playlistRepository.savePlaylist(playlistId, name, items)

    fun songs(playlistId: String): Flow<List<WatchPlaylistSong>> = combine(
        playlistRepository.songs(playlistId),
        transferRepository.localSongs,
    ) { entries, localSongs ->
        val savedById = localSongs.associateBy { it.songId }
        entries.map { WatchPlaylistSong(it, savedById[it.songId]) }
    }

    /** Plays the playlist's saved songs in order, from [fromSongId] when given. */
    fun play(playlistId: String, fromSongId: String? = null) {
        viewModelScope.launch {
            val songs = songs(playlistId).first().mapNotNull { it.saved }
            if (songs.isEmpty()) return@launch
            val start = songs.indexOfFirst { it.songId == fromSongId }.coerceAtLeast(0)
            localPlayerRepository.playLocalSongs(songs, start)
            stateRepository.setOutputTarget(WearOutputTarget.WATCH)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { playlistRepository.deletePlaylist(playlistId) }
    }
}
