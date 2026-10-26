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
import com.nobodybuilds.listens.data.WatchStreamingService
import com.nobodybuilds.listens.data.WearStreamingCatalog
import com.nobodybuilds.listens.data.local.LocalSongEntity
import com.nobodybuilds.listens.data.stream.StreamingTrack
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
    /** Streaming services searched for this query, only once asked for (offline first). */
    val online: Map<WatchStreamingService, OnlineResults> = emptyMap(),
)

data class OnlineResults(val tracks: List<StreamingTrack> = emptyList(), val searching: Boolean = true)

/**
 * Searches the watch first, so results show without the phone, then the phone's library when it's reachable.
 * Like the phone's Search, nothing goes online unless asked: a streaming service (YouTube Music, JioSaavn) is
 * searched only when its chip is tapped, over the watch's own connection.
 */
@HiltViewModel
class WearSearchViewModel @Inject constructor(
    private val transferRepository: WearTransferRepository,
    private val deviceMusicRepository: WearDeviceMusicRepository,
    private val libraryRepository: WearLibraryRepository,
    private val localPlayerRepository: WearLocalPlayerRepository,
    private val stateRepository: WearStateRepository,
    private val streaming: WearStreamingCatalog,
) : ViewModel() {

    private val _state = MutableStateFlow(WearSearchState())
    val state: StateFlow<WearSearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        _state.value = WearSearchState(query = trimmed, isSearching = true)
        searchJob = viewModelScope.launch {
            val saved = transferRepository.localSongs.first()
                .filter { WearSearchMatcher.matches(trimmed, it.title, it.artist, it.album) }
            // Without the audio permission the scan fails; the saved songs still show.
            val device = runCatching { deviceMusicRepository.scanDeviceSongs() }.getOrDefault(emptyList())
                .filter { WearSearchMatcher.matches(trimmed, it.title, it.artist, it.album) }
            updateIfCurrent(trimmed) { it.copy(savedSongs = saved, deviceSongs = device) }

            val phone = libraryRepository.browse(WearBrowseRequest.SEARCH, trimmed)
            updateIfCurrent(trimmed) {
                it.copy(
                    isSearching = false,
                    phoneSongs = phone.items.filter { item -> item.type == WearLibraryItem.TYPE_SONG },
                    phoneError = phone.error,
                )
            }
        }
    }

    /** Searches one streaming service for the current query; only when asked, so search stays offline first. */
    fun searchOnline(service: WatchStreamingService) {
        val query = _state.value.query
        if (query.isEmpty() || _state.value.online[service]?.searching == true) return
        _state.update { it.copy(online = it.online + (service to OnlineResults())) }
        viewModelScope.launch {
            val tracks = runCatching { streaming.search(service, query) }.getOrDefault(emptyList())
            updateIfCurrent(query) {
                it.copy(online = it.online + (service to OnlineResults(tracks.take(MAX_ONLINE_RESULTS), searching = false)))
            }
        }
    }

    /** Lookups swallow cancellation, so a replaced search can still finish; drop its results. */
    private fun updateIfCurrent(query: String, change: (WearSearchState) -> WearSearchState) {
        _state.update { if (it.query == query) change(it) else it }
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

    /** Streams one service's results on the watch, starting at the track with [key]. */
    fun playOnlineTrack(service: WatchStreamingService, key: String) {
        val tracks = _state.value.online[service]?.tracks.orEmpty()
        val index = tracks.indexOfFirst { it.key == key }
        if (index < 0) return
        localPlayerRepository.playUriSongs(
            songs = tracks.map { streaming.queueSong(service, it) },
            startIndex = index,
        )
        stateRepository.setOutputTarget(WearOutputTarget.WATCH)
    }

    private companion object {
        const val MAX_ONLINE_RESULTS = 20
    }
}
