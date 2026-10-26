package com.nobodybuilds.listens.presentation.streaming

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.stream.StreamingCollection
import com.nobodybuilds.listens.data.stream.StreamingShelf
import com.nobodybuilds.listens.data.stream.StreamingTrack
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.SmartImage
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * The screen of a streaming service: Feed, Library and Search tabs, and playlist,
 * album and artist pages. [toSong] turns a track into the library song the player
 * queues; [onWatch] adds a video button to tracks when the service has video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : StreamingTrack, C : StreamingCollection> StreamingDashboardScreen(
    viewModel: StreamingDashboardViewModel<T, C>,
    playerViewModel: PlayerViewModel,
    toSong: (T) -> Song,
    searchLabel: String,
    signInBody: String,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    onWatch: ((T) -> Unit)? = null,
) {
    val loggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val serviceName = viewModel.serviceName
    val page = state.openPage
    val shelves = currentShelves(state)
    val itemCount = shelves.sumOf { it.tracks.size + it.playlists.size }
    val busy = state.status?.let { it.startsWith("Downloading") || it.startsWith("Importing") } == true

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )
    val listPadding = PaddingValues(
        start = 16.dp,
        top = 4.dp,
        end = 16.dp,
        bottom = MiniPlayerHeight + 16.dp,
    )
    val play: (List<T>, T, String) -> Unit = { tracks, start, queueName ->
        viewModel.prepareQueue(tracks, start)
        playerViewModel.playSongs(tracks.map(toSong), toSong(start), queueName)
    }

    LaunchedEffect(loggedIn) {
        viewModel.loadFeedTab()
    }
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        page?.title?.takeIf { it.isNotBlank() } ?: serviceName,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (page != null) viewModel.closePage() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.reload() },
                        enabled = !state.loading
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = "Reload",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (loggedIn) {
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Log out")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = state.status != null,
                enter = slideInVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeIn(),
                exit = fadeOut()
            ) {
                state.status?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.contains("fail", ignoreCase = true) || message.startsWith("Error"))
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayName = accountName.ifBlank { serviceName }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.first().toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (page != null) "${page.tracks.size} songs" else "$itemCount items",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (page == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StreamingTab.entries.forEach { tab ->
                        FilterChip(
                            selected = state.tab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            shape = CircleShape,
                            border = BorderStroke(color = Color.Transparent, width = 0.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.24f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            label = {
                                Text(
                                    when (tab) {
                                        StreamingTab.FEED -> "Feed"
                                        StreamingTab.LIBRARY -> "Library"
                                        StreamingTab.SEARCH -> "Search"
                                    },
                                    fontFamily = GoogleSansRounded
                                )
                            }
                        )
                    }
                }
            }

            if (state.tab == StreamingTab.SEARCH && page == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.suggest(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    shape = cardShape,
                    label = { Text(searchLabel, fontFamily = GoogleSansRounded) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.search(query) }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }
                )
                StreamingSearchSuggestions(
                    suggestions = state.suggestions,
                    onPick = { suggestion ->
                        query = suggestion
                        viewModel.search(suggestion)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (page == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (state.tab) {
                            StreamingTab.FEED -> "Home"
                            StreamingTab.LIBRARY -> "Playlists"
                            StreamingTab.SEARCH -> "Results"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                    if (shelves.isEmpty() && !state.loading) {
                        TextButton(onClick = { viewModel.reload() }) {
                            Icon(
                                Icons.Rounded.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reload", fontFamily = GoogleSansRounded)
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // Feed, search, play and download work signed out; only your library needs an account.
                    !loggedIn && state.tab == StreamingTab.LIBRARY && page == null -> {
                        SignInPrompt(serviceName = serviceName, body = signInBody, onSignIn = onSignIn)
                    }
                    state.error != null && shelves.isEmpty() && page == null && !state.loading -> {
                        Text(
                            state.error.orEmpty(),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = GoogleSansRounded
                        )
                    }
                    page != null && page.shelves.isNotEmpty() -> {
                        // Artist page: top songs, albums, singles and similar artists.
                        ShelfList(
                            shelves = page.shelves,
                            serviceName = serviceName,
                            cardShape = cardShape,
                            contentPadding = listPadding,
                            onPlay = { shelf, track -> play(shelf.tracks.ifEmpty { listOf(track) }, track, page.title) },
                            onOpen = viewModel::openCollection,
                            onDownload = { viewModel.download(listOf(it)) },
                            onWatch = onWatch,
                        )
                    }
                    page != null -> {
                        PageTracks(
                            tracks = page.tracks,
                            serviceName = serviceName,
                            loading = state.loading,
                            cardShape = cardShape,
                            contentPadding = listPadding,
                            onPlay = { track -> play(page.tracks, track, page.title) },
                            onDownload = { viewModel.download(listOf(it)) },
                            onWatch = onWatch,
                            onDownloadAll = { viewModel.download(page.tracks) },
                            onImport = viewModel::importOpenPage.takeIf { page.source?.importable == true },
                        )
                    }
                    shelves.isEmpty() && !state.loading -> {
                        EmptyState()
                    }
                    else -> {
                        ShelfList(
                            shelves = shelves,
                            serviceName = serviceName,
                            cardShape = cardShape,
                            contentPadding = listPadding,
                            onPlay = { shelf, track ->
                                play(shelf.tracks.ifEmpty { listOf(track) }, track, shelf.title.ifBlank { serviceName })
                            },
                            onOpen = viewModel::openCollection,
                            onDownload = { viewModel.download(listOf(it)) },
                            onWatch = onWatch,
                        )
                    }
                }

                if (state.loading && (shelves.isNotEmpty() || page?.tracks?.isNotEmpty() == true)) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (state.loading && shelves.isEmpty() && page == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

private fun <T : StreamingTrack, C : StreamingCollection> currentShelves(
    state: StreamingDashboardState<T, C>,
): List<StreamingShelf<T, C>> =
    when (state.tab) {
        StreamingTab.FEED -> state.feed
        StreamingTab.LIBRARY -> state.library
        StreamingTab.SEARCH -> state.searchResults
    }
