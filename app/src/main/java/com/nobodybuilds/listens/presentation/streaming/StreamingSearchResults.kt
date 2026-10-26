package com.nobodybuilds.listens.presentation.streaming

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.stream.StreamingTrack
import com.nobodybuilds.listens.presentation.components.subcomps.EnhancedSongListItem
import com.nobodybuilds.listens.presentation.screens.SearchResultSectionHeader
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.nb.NbEmptyState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A titled list of songs in the results. */
data class StreamingResultSection<T : StreamingTrack>(val title: String, val tracks: List<T>)

data class StreamingSearchState<T : StreamingTrack>(
    val query: String = "",
    val loading: Boolean = false,
    val sections: List<StreamingResultSection<T>> = emptyList(),
    val error: String? = null,
    val suggestions: List<String> = emptyList(),
)

/** Backs a streaming service's chip on the main Search screen. */
abstract class StreamingSearchViewModel<T : StreamingTrack> : ViewModel() {
    private val _state = MutableStateFlow(StreamingSearchState<T>())
    val state: StateFlow<StreamingSearchState<T>> = _state.asStateFlow()
    private var searchJob: Job? = null

    protected abstract suspend fun loadResults(query: String): List<StreamingResultSection<T>>
    protected abstract suspend fun loadSuggestions(query: String): List<String>
    /** Pasted links get no suggestions. */
    protected abstract fun isLink(query: String): Boolean

    /** Queues the section like the service's screen does; each song is saved as it plays. */
    abstract fun prepareQueue(tracks: List<T>, start: T)

    /** The options sheet can add to playlists and like, which need the song saved first. */
    abstract fun saveForOptions(track: T)

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed == _state.value.query && (_state.value.sections.isNotEmpty() || _state.value.loading)) return
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            _state.value = StreamingSearchState()
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS) // the query changes with every keystroke
            if (!isLink(trimmed)) {
                launch {
                    val suggestions = runCatching { loadSuggestions(trimmed) }.getOrDefault(emptyList())
                    _state.value = _state.value.copy(suggestions = suggestions.filterNot { it.equals(trimmed, ignoreCase = true) }.take(5))
                }
            }
            _state.value = _state.value.copy(query = trimmed, loading = true, error = null)
            runCatching { loadResults(trimmed) }
                .onSuccess { sections ->
                    _state.value = _state.value.copy(loading = false, sections = sections.filter { it.tracks.isNotEmpty() })
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(loading = false, sections = emptyList(), error = error.message ?: "Search failed")
                }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}

/** A streaming service's results for the main Search screen, in the same rows as library songs. */
@Composable
fun <T : StreamingTrack> StreamingSearchResults(
    query: String,
    viewModel: StreamingSearchViewModel<T>,
    serviceName: String,
    emptyBody: String,
    toSong: (T) -> Song,
    playerViewModel: PlayerViewModel,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    onSuggestionPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(query) { viewModel.search(query) }
    StreamingSearchSuggestions(
        suggestions = state.suggestions,
        onPick = onSuggestionPick,
    )

    when {
        state.loading && state.sections.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 32.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.sections.isEmpty() -> NbEmptyState(
            title = if (state.error != null) "Could not search $serviceName" else "No $serviceName results",
            body = state.error ?: emptyBody,
            icon = rememberVectorPainter(Icons.Rounded.Search),
            modifier = modifier.padding(top = 16.dp),
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            state.sections.forEachIndexed { sectionIndex, section ->
                item(key = "stream_header_$sectionIndex") {
                    SearchResultSectionHeader(title = section.title.ifBlank { serviceName })
                }
                items(section.tracks.distinctBy { it.key }, key = { "stream_${sectionIndex}_${it.key}" }) { track ->
                    val song = toSong(track)
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
                                viewModel.prepareQueue(section.tracks, track)
                                playerViewModel.playSongs(
                                    section.tracks.map(toSong),
                                    song,
                                    section.title.ifBlank { serviceName },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
