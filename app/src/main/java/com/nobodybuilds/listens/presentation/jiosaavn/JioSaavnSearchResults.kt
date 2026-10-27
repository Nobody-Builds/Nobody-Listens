package com.nobodybuilds.listens.presentation.jiosaavn

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnLibrary
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnRepository
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnTrack
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.streaming.StreamingResultSection
import com.nobodybuilds.listens.presentation.streaming.StreamingSearchResults
import com.nobodybuilds.listens.presentation.streaming.StreamingSearchViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the JioSaavn chip of the main Search screen. */
@HiltViewModel
class JioSaavnSearchViewModel @Inject constructor(
    private val repository: JioSaavnRepository,
    private val library: JioSaavnLibrary,
) : StreamingSearchViewModel<JioSaavnTrack>() {

    override suspend fun loadResults(query: String): List<StreamingResultSection<JioSaavnTrack>> {
        val link = JioSaavnUris.linkFrom(query)
        if (link != null) {
            val page = repository.linkPage(link.first, link.second)
            return listOf(StreamingResultSection(page.title.orEmpty(), page.shelves.flatMap { it.tracks }))
        }
        return repository.search(query).map { StreamingResultSection(it.title, it.tracks) }
    }

    override suspend fun loadSuggestions(query: String): List<String> = repository.searchSuggestions(query)

    override fun isLink(query: String): Boolean = JioSaavnUris.linkFrom(query) != null

    override fun prepareQueue(tracks: List<JioSaavnTrack>, start: JioSaavnTrack) {
        library.remember(tracks)
        viewModelScope.launch { library.saveIfQueued(start.id) }
    }

    override fun saveForOptions(track: JioSaavnTrack) {
        library.remember(listOf(track))
        viewModelScope.launch { library.saveIfQueued(track.id) }
    }
}

/** JioSaavn songs for the main Search screen, in the same rows as library songs. */
@Composable
fun JioSaavnSearchResults(
    query: String,
    playerViewModel: PlayerViewModel,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    onSuggestionPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JioSaavnSearchViewModel = hiltViewModel(),
) {
    StreamingSearchResults(
        query = query,
        viewModel = viewModel,
        serviceName = JioSaavnUris.SERVICE_NAME,
        emptyBody = "Try another search, or paste a JioSaavn link.",
        toSong = JioSaavnUris::toSong,
        playerViewModel = playerViewModel,
        currentPlayingSongId = currentPlayingSongId,
        isPlaying = isPlaying,
        onSongMoreOptionsClick = onSongMoreOptionsClick,
        onSuggestionPick = onSuggestionPick,
        modifier = modifier,
    )
}
