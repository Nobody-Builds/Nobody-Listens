package com.nobodybuilds.listens.presentation.ytmusic

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicLibrary
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRepository
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.presentation.streaming.StreamingResultSection
import com.nobodybuilds.listens.presentation.streaming.StreamingSearchResults
import com.nobodybuilds.listens.presentation.streaming.StreamingSearchViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the YouTube chip of the main Search screen. */
@HiltViewModel
class YouTubeMusicSearchViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
    private val library: YouTubeMusicLibrary,
) : StreamingSearchViewModel<YouTubeMusicTrack>() {

    override suspend fun loadResults(query: String): List<StreamingResultSection<YouTubeMusicTrack>> {
        val linkedVideoId = YouTubeMusicUris.videoIdFromLink(query)
        val page = if (linkedVideoId != null) repository.trackPage(linkedVideoId) else repository.search(query)
        return page.shelves.map { StreamingResultSection(it.title, it.tracks) }
    }

    override suspend fun loadSuggestions(query: String): List<String> = repository.searchSuggestions(query)

    override fun isLink(query: String): Boolean = YouTubeMusicUris.videoIdFromLink(query) != null

    override fun prepareQueue(tracks: List<YouTubeMusicTrack>, start: YouTubeMusicTrack) {
        library.remember(tracks)
        viewModelScope.launch { library.saveIfQueued(start.videoId) }
    }

    override fun saveForOptions(track: YouTubeMusicTrack) {
        library.remember(listOf(track))
        viewModelScope.launch { library.saveIfQueued(track.videoId) }
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
    StreamingSearchResults(
        query = query,
        viewModel = viewModel,
        serviceName = "YouTube",
        emptyBody = "Try another search, or paste a YouTube link.",
        toSong = YouTubeMusicUris::toSong,
        playerViewModel = playerViewModel,
        currentPlayingSongId = currentPlayingSongId,
        isPlaying = isPlaying,
        onSongMoreOptionsClick = onSongMoreOptionsClick,
        onSuggestionPick = onSuggestionPick,
        modifier = modifier,
    )
}
