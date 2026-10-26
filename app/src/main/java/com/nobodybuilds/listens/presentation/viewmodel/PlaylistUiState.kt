package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.model.Playlist
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val showTelegramCloudPlaylists: Boolean = true,
    val telegramTopicDisplayMode: TelegramTopicDisplayMode = TelegramTopicDisplayMode.CHANNELS_AND_TOPICS,
    val currentPlaylistSongs: List<Song> = emptyList(),
    val currentPlaylistDetails: Playlist? = null,
    val isLoading: Boolean = false,
    val playlistNotFound: Boolean = false,

    //Sort option
    val currentPlaylistSortOption: SortOption = SortOption.PlaylistNameAZ,
    val currentPlaylistSongsSortOption: SortOption = SortOption.SongTitleAZ,
    val playlistSongsOrderMode: PlaylistSongsOrderMode = PlaylistSongsOrderMode.Sorted(SortOption.SongTitleAZ),
    val playlistOrderModes: Map<String, PlaylistSongsOrderMode> = emptyMap(),

    // AI Generation State
    val isAiGenerating: Boolean = false,
    val aiGenerationError: String? = null
)

sealed class PlaylistSongsOrderMode {
    object Manual : PlaylistSongsOrderMode()
    data class Sorted(val option: SortOption) : PlaylistSongsOrderMode()
}
