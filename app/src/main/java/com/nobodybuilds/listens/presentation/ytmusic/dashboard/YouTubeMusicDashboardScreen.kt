package com.nobodybuilds.listens.presentation.ytmusic.dashboard

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicShelf
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.ytmusic.auth.YouTubeMusicLoginActivity
import com.nobodybuilds.listens.presentation.ytmusic.video.YouTubeMusicVideoActivity
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeMusicDashboardScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: YouTubeMusicDashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val loggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val shelves = currentShelves(state)
    val itemCount = shelves.sumOf { it.tracks.size + it.playlists.size }
    val downloading = state.status?.startsWith("Downloading") == true

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

    val watchVideo: (YouTubeMusicTrack) -> Unit = { track ->
        context.startActivity(YouTubeMusicVideoActivity.intent(context, track.videoId, track.title))
    }

    LaunchedEffect(loggedIn) {
        viewModel.loadFeed()
    }
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.openPlaylistTitle.isNullOrBlank()) "YouTube Music" else state.openPlaylistTitle.orEmpty(),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.openPlaylistTitle != null) viewModel.closePlaylist() else onBack()
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
                            if (downloading) {
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
                    val displayName = accountName.ifBlank { "YouTube Music" }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.firstOrNull()?.toString() ?: "Y",
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
                            text = if (state.openPlaylistTitle != null) {
                                "${state.playlistTracks.size} songs"
                            } else {
                                "$itemCount items"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.openPlaylistTitle == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    YouTubeMusicTab.entries.forEach { tab ->
                        val selected = state.tab == tab
                        FilterChip(
                            selected = selected,
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
                                        YouTubeMusicTab.FEED -> "Feed"
                                        YouTubeMusicTab.LIBRARY -> "Library"
                                        YouTubeMusicTab.SEARCH -> "Search"
                                    },
                                    fontFamily = GoogleSansRounded
                                )
                            }
                        )
                    }
                }
            }

            if (state.tab == YouTubeMusicTab.SEARCH && state.openPlaylistTitle == null) {
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
                    label = { Text("Search or paste a YouTube link", fontFamily = GoogleSansRounded) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.search(query) }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }
                )
                YouTubeSearchSuggestions(
                    suggestions = state.suggestions,
                    onPick = { suggestion ->
                        query = suggestion
                        viewModel.search(suggestion)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.openPlaylistTitle == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (state.tab) {
                            YouTubeMusicTab.FEED -> "Home"
                            YouTubeMusicTab.LIBRARY -> "Playlists"
                            YouTubeMusicTab.SEARCH -> "Results"
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
                    !loggedIn && state.tab == YouTubeMusicTab.LIBRARY && state.openPlaylistTitle == null -> {
                        SignInPrompt(
                            onSignIn = {
                                context.startActivity(Intent(context, YouTubeMusicLoginActivity::class.java))
                            }
                        )
                    }
                    state.error != null && shelves.isEmpty() && state.openPlaylistTitle == null && !state.loading -> {
                        Text(
                            state.error.orEmpty(),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = GoogleSansRounded
                        )
                    }
                    state.openPlaylistTitle != null && state.pageShelves.isNotEmpty() -> {
                        // Artist page: top songs, albums, singles, videos and similar artists.
                        ShelfList(
                            shelves = state.pageShelves,
                            cardShape = cardShape,
                            contentPadding = listPadding,
                            onPlay = { shelf, track ->
                                val tracks = shelf.tracks.ifEmpty { listOf(track) }
                                play(viewModel, playerViewModel, tracks, track, state.openPlaylistTitle.orEmpty())
                            },
                            onOpenPlaylist = viewModel::openPlaylist,
                            onDownload = viewModel::download,
                            onWatch = watchVideo,
                        )
                    }
                    state.openPlaylistTitle != null -> {
                        PlaylistTracks(
                            tracks = state.playlistTracks,
                            loading = state.loading,
                            cardShape = cardShape,
                            contentPadding = listPadding,
                            onPlay = { track ->
                                play(viewModel, playerViewModel, state.playlistTracks, track, state.openPlaylistTitle.orEmpty())
                            },
                            onDownload = viewModel::download,
                            onWatch = watchVideo,
                            onDownloadAll = { viewModel.downloadAll(state.playlistTracks) },
                        )
                    }
                    shelves.isEmpty() && !state.loading -> {
                        EmptyState()
                    }
                    else -> {
                        ShelfList(
                            shelves = shelves,
                            cardShape = cardShape,
                            contentPadding = listPadding,
                            onPlay = { shelf, track ->
                                val tracks = shelf.tracks.ifEmpty { listOf(track) }
                                play(viewModel, playerViewModel, tracks, track, shelf.title.ifBlank { "YouTube Music" })
                            },
                            onOpenPlaylist = viewModel::openPlaylist,
                            onDownload = viewModel::download,
                            onWatch = watchVideo,
                        )
                    }
                }

                if (state.loading && (shelves.isNotEmpty() || state.playlistTracks.isNotEmpty())) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (state.loading && shelves.isEmpty() && state.openPlaylistTitle == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

private fun currentShelves(state: YouTubeMusicDashboardState): List<YouTubeMusicShelf> =
    when (state.tab) {
        YouTubeMusicTab.FEED -> state.feed
        YouTubeMusicTab.LIBRARY -> state.library
        YouTubeMusicTab.SEARCH -> state.searchResults
    }

private fun play(
    viewModel: YouTubeMusicDashboardViewModel,
    playerViewModel: PlayerViewModel,
    tracks: List<YouTubeMusicTrack>,
    start: YouTubeMusicTrack,
    queueName: String,
) {
    viewModel.prepareQueue(tracks, start)
    playerViewModel.playSongs(tracks.map(YouTubeMusicUris::toSong), YouTubeMusicUris.toSong(start), queueName)
}
