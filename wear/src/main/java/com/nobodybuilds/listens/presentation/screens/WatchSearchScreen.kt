package com.nobodybuilds.listens.presentation.screens

import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.viewmodel.WearBrowseViewModel
import com.nobodybuilds.listens.data.WatchStreamingService
import com.nobodybuilds.listens.presentation.viewmodel.OnlineResults
import com.nobodybuilds.listens.presentation.viewmodel.WearSearchState
import com.nobodybuilds.listens.presentation.viewmodel.WearSearchViewModel
import com.nobodybuilds.listens.shared.WearBrowseRequest

private const val QUERY_KEY = "query"

/** Search songs on the watch and, when it's connected, on the phone. Opens the watch keyboard/voice input. */
@Composable
fun WatchSearchScreen(
    viewModel: WearSearchViewModel = hiltViewModel(),
    browseViewModel: WearBrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val inputLabel = stringResource(R.string.wear_search_hint)
    val input = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let(RemoteInput::getResultsFromIntent)?.getCharSequence(QUERY_KEY)
        if (text != null) viewModel.search(text.toString())
    }
    val openInput = { input.launch(searchInputIntent(inputLabel)) }
    var openedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!openedOnce && state.query.isEmpty()) openInput()
        openedOnce = true
    }

    WatchListScaffold {
        item { Spacer(modifier = Modifier.height(2.dp)) }
        item { WatchListTitle(stringResource(R.string.wear_search)) }
        item {
            WatchListChip(
                title = state.query.ifEmpty { inputLabel },
                subtitle = "",
                icon = Icons.Rounded.Search,
                onClick = openInput,
            )
        }
        searchResults(
            state = state,
            onSavedSong = viewModel::playSavedSong,
            onDeviceSong = viewModel::playDeviceSong,
            onPhoneSong = { songId -> browseViewModel.playFromContext(songId, WearBrowseRequest.SEARCH, state.query) },
        )
        WatchStreamingService.entries.forEach { service ->
            onlineResults(
                query = state.query,
                service = service,
                results = state.online[service],
                onSearch = { viewModel.searchOnline(service) },
                onTrack = { key -> viewModel.playOnlineTrack(service, key) },
            )
        }
    }
}

private fun ScalingLazyListScope.searchResults(
    state: WearSearchState,
    onSavedSong: (String) -> Unit,
    onDeviceSong: (String) -> Unit,
    onPhoneSong: (String) -> Unit,
) {
    if (state.query.isEmpty()) return
    val onWatch = state.savedSongs.isNotEmpty() || state.deviceSongs.isNotEmpty()
    if (onWatch) {
        item { SearchSectionHeader(R.string.wear_search_on_watch) }
        items(state.savedSongs.size) { index ->
            val song = state.savedSongs[index]
            WatchListChip(song.title, song.artist, Icons.Rounded.MusicNote) { onSavedSong(song.songId) }
        }
        items(state.deviceSongs.size) { index ->
            val song = state.deviceSongs[index]
            WatchListChip(song.title, song.artist, Icons.Rounded.MusicNote) { onDeviceSong(song.songId) }
        }
    }
    if (state.phoneSongs.isNotEmpty()) {
        item { SearchSectionHeader(R.string.wear_search_on_phone) }
        items(state.phoneSongs.size) { index ->
            val song = state.phoneSongs[index]
            WatchListChip(song.title, song.subtitle, Icons.Rounded.PhoneAndroid) { onPhoneSong(song.id) }
        }
    }
    val foundElsewhere = onWatch || state.online.values.any { it.tracks.isNotEmpty() }
    val searchingOnline = state.online.values.any { it.searching }
    when {
        state.isSearching -> item {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(indicatorColor = LocalWearPalette.current.textSecondary)
            }
        }
        state.phoneSongs.isNotEmpty() -> Unit
        state.phoneError != null && foundElsewhere -> item {
            WatchListMessage(stringResource(R.string.wear_search_phone_offline))
        }
        !foundElsewhere && !searchingOnline -> item {
            WatchListMessage(stringResource(R.string.wear_search_no_results))
        }
    }
}

/**
 * One streaming service: a chip that searches it on request, then its results. The same section serves
 * YouTube Music and JioSaavn.
 */
private fun ScalingLazyListScope.onlineResults(
    query: String,
    service: WatchStreamingService,
    results: OnlineResults?,
    onSearch: () -> Unit,
    onTrack: (String) -> Unit,
) {
    if (query.isEmpty()) return
    if (results == null) {
        item {
            WatchListChip(
                title = stringResource(R.string.wear_search_online, service.label),
                subtitle = "",
                icon = Icons.Rounded.Public,
                onClick = onSearch,
            )
        }
        return
    }
    item { DownloadsSectionHeader(title = service.label, color = LocalWearPalette.current.textSecondary) }
    if (results.tracks.isEmpty() && !results.searching) {
        item { WatchListMessage(stringResource(R.string.wear_search_no_results)) }
        return
    }
    items(results.tracks.size) { index ->
        val track = results.tracks[index]
        WatchListChip(track.title, track.artist, Icons.Rounded.Public) { onTrack(track.key) }
    }
    if (results.searching) item {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(indicatorColor = LocalWearPalette.current.textSecondary)
        }
    }
}

private fun searchInputIntent(label: String): Intent {
    val remoteInput = RemoteInput.Builder(QUERY_KEY)
        .setLabel(label)
        .wearableExtender {
            setEmojisAllowed(false)
            setInputActionType(EditorInfo.IME_ACTION_SEARCH)
        }
        .build()
    return RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
    }
}

@Composable
private fun SearchSectionHeader(textRes: Int) {
    DownloadsSectionHeader(title = stringResource(textRes), color = LocalWearPalette.current.textSecondary)
}
