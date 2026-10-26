package com.nobodybuilds.listens.presentation.ytmusic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicLibrary
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRepository
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicShelf
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.presentation.components.subcomps.EnhancedSongListItem
import com.nobodybuilds.listens.presentation.screens.SearchResultSectionHeader
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.nb.NbEmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouTubeMusicSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val shelves: List<YouTubeMusicShelf> = emptyList(),
    val error: String? = null,
    val suggestions: List<String> = emptyList(),
)

/** Backs the YouTube chip of the main Search screen. */
@HiltViewModel
class YouTubeMusicSearchViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
    private val library: YouTubeMusicLibrary,
) : ViewModel() {
    private val _state = MutableStateFlow(YouTubeMusicSearchState())
    val state: StateFlow<YouTubeMusicSearchState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed == _state.value.query && (_state.value.shelves.isNotEmpty() || _state.value.loading)) return
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            _state.value = YouTubeMusicSearchState()
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS) // the query changes with every keystroke
            if (YouTubeMusicUris.videoIdFromLink(trimmed) == null) {
                launch {
                    val suggestions = runCatching { repository.searchSuggestions(trimmed) }.getOrDefault(emptyList())
                    _state.value = _state.value.copy(suggestions = suggestions.filterNot { it.equals(trimmed, ignoreCase = true) }.take(5))
                }
            }
            _state.value = _state.value.copy(query = trimmed, loading = true, error = null)
            val linkedVideoId = YouTubeMusicUris.videoIdFromLink(trimmed)
            runCatching {
                if (linkedVideoId != null) repository.trackPage(linkedVideoId) else repository.search(trimmed)
            }.onSuccess { page ->
                _state.value = _state.value.copy(loading = false, shelves = page.shelves.filter { it.tracks.isNotEmpty() })
            }.onFailure { error ->
                _state.value = _state.value.copy(loading = false, shelves = emptyList(), error = error.message ?: "YouTube search failed")
            }
        }
    }

    /** Queues the shelf like the YouTube Music screen does; each song is saved as it plays. */
    fun prepareQueue(tracks: List<YouTubeMusicTrack>, start: YouTubeMusicTrack) {
        library.remember(tracks)
        viewModelScope.launch { library.saveIfQueued(start.videoId) }
    }

    /** The options sheet can add to playlists and like, which need the song saved first. */
    fun saveForOptions(track: YouTubeMusicTrack) {
        library.remember(listOf(track))
        viewModelScope.launch { library.saveIfQueued(track.videoId) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}

/**
 * YouTube results for the main Search screen, in the same rows as library songs:
 * YouTube Music songs first, then plain YouTube videos.
 */
@Composable
fun YouTubeMusicSearchResults(
    query: String,
    playerViewModel: PlayerViewModel,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    onSuggestionPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeMusicSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(query) { viewModel.search(query) }
    com.nobodybuilds.listens.presentation.ytmusic.dashboard.YouTubeSearchSuggestions(
        suggestions = state.suggestions,
        onPick = onSuggestionPick,
    )

    when {
        state.loading && state.shelves.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 32.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.shelves.isEmpty() -> NbEmptyState(
            title = if (state.error != null) "Could not search YouTube" else "No YouTube results",
            body = state.error ?: "Try another search, or paste a YouTube link.",
            icon = rememberVectorPainter(Icons.Rounded.Search),
            modifier = modifier.padding(top = 16.dp),
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            state.shelves.forEachIndexed { shelfIndex, shelf ->
                item(key = "yt_header_$shelfIndex") {
                    SearchResultSectionHeader(title = shelf.title.ifBlank { "YouTube Music" })
                }
                items(shelf.tracks, key = { "yt_${shelfIndex}_${it.videoId}" }) { track ->
                    val song = YouTubeMusicUris.toSong(track)
                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        EnhancedSongListItem(
                            song = song,
                            isPlaying = isPlaying,
                            isCurrentSong = currentPlayingSongId == song.id,
                            onMoreOptionsClick = {
                                viewModel.saveForOptions(track)
                                onSongMoreOptionsClick(song)
                            },
                            onClick = {
                                viewModel.prepareQueue(shelf.tracks, track)
                                playerViewModel.playSongs(
                                    shelf.tracks.map(YouTubeMusicUris::toSong),
                                    song,
                                    shelf.title.ifBlank { "YouTube Music" },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
