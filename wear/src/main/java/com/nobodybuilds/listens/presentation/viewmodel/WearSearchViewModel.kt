package com.nobodybuilds.listens.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.WearDeviceMusicRepository
import com.nobodybuilds.listens.data.WearDeviceSong
import com.nobodybuilds.listens.data.WearLibraryRepository
import com.nobodybuilds.listens.data.WearLocalPlayerRepository
import com.nobodybuilds.listens.data.WearOutputTarget
import com.nobodybuilds.listens.data.WearQueueSong
import com.nobodybuilds.listens.data.WearSearchMatcher
import com.nobodybuilds.listens.data.WearStateRepository
import com.nobodybuilds.listens.data.WearTransferRepository
import com.nobodybuilds.listens.data.WearYouTubeRepository
import com.nobodybuilds.listens.data.local.LocalSongEntity
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicFormats
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.shared.WearBrowseRequest
import com.nobodybuilds.listens.shared.WearLibraryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    /** Songs saved to the watch by the app. */
    val savedSongs: List<LocalSongEntity> = emptyList(),
    /** Other audio files on the watch's storage. */
    val deviceSongs: List<WearDeviceSong> = emptyList(),
    val phoneSongs: List<WearLibraryItem> = emptyList(),
    val phoneError: String? = null,
    /** Straight from YouTube over the watch's own connection. */
    val youTubeTracks: List<YouTubeMusicTrack> = emptyList(),
    val isSearchingYouTube: Boolean = false,
)

/**
 * Searches the watch first, so results show without the phone, then the phone's library when it's reachable,
 * and YouTube Music when the watch has internet.
 */
@HiltViewModel
class WearSearchViewModel @Inject constructor(
    private val transferRepository: WearTransferRepository,
    private val deviceMusicRepository: WearDeviceMusicRepository,
    private val libraryRepository: WearLibraryRepository,
    private val localPlayerRepository: WearLocalPlayerRepository,
    private val stateRepository: WearStateRepository,
    private val youTubeRepository: WearYouTubeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WearSearchState())
    val state: StateFlow<WearSearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        _state.value = WearSearchState(query = trimmed, isSearching = true, isSearchingYouTube = true)
        searchJob = viewModelScope.launch {
            launch {
                val tracks = runCatching { youTubeRepository.search(trimmed) }.getOrDefault(emptyList())
                _state.update { it.copy(youTubeTracks = tracks.take(MAX_YOUTUBE_RESULTS), isSearchingYouTube = false) }
            }
            val saved = transferRepository.localSongs.first()
                .filter { WearSearchMatcher.matches(trimmed, it.title, it.artist, it.album) }
            // Without the audio permission the scan fails; the saved songs still show.
            val device = runCatching { deviceMusicRepository.scanDeviceSongs() }.getOrDefault(emptyList())
                .filter { WearSearchMatcher.matches(trimmed, it.title, it.artist, it.album) }
            _state.update { it.copy(savedSongs = saved, deviceSongs = device) }

            val phone = libraryRepository.browse(WearBrowseRequest.SEARCH, trimmed)
            _state.update {
                it.copy(
                    isSearching = false,
                    phoneSongs = phone.items.filter { item -> item.type == WearLibraryItem.TYPE_SONG },
                    phoneError = phone.error,
                )
            }
        }
    }

    fun playSavedSong(songId: String) {
        val songs = _state.value.savedSongs
        val index = songs.indexOfFirst { it.songId == songId }
        if (index < 0) return
        localPlayerRepository.playLocalSongs(songs, index)
        stateRepository.setOutputTarget(WearOutputTarget.WATCH)
    }

    fun playDeviceSong(songId: String) {
        val songs = _state.value.deviceSongs
        val index = songs.indexOfFirst { it.songId == songId }
        if (index < 0) return
        localPlayerRepository.playUriSongs(
            songs = songs.map { WearQueueSong(it.songId, it.title, it.artist, it.album, it.contentUri) },
            startIndex = index,
        )
        stateRepository.setOutputTarget(WearOutputTarget.WATCH)
    }

    /** Streams the YouTube results on the watch, starting at [videoId]. */
    fun playYouTubeTrack(videoId: String) {
        val tracks = _state.value.youTubeTracks
        val index = tracks.indexOfFirst { it.videoId == videoId }
        if (index < 0) return
        localPlayerRepository.playUriSongs(
            songs = tracks.map { track ->
                WearQueueSong(
                    songId = WearYouTubeRepository.songId(track.videoId),
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    uri = WearYouTubeRepository.trackUri(track.videoId),
                    artworkUrl = YouTubeMusicFormats.largeThumbnail(track.thumbnailUrl),
                )
            },
            startIndex = index,
        )
        stateRepository.setOutputTarget(WearOutputTarget.WATCH)
    }

    private companion object {
        const val MAX_YOUTUBE_RESULTS = 20
    }
}
