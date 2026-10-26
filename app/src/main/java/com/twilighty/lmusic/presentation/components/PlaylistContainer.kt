package com.twilighty.lmusic.presentation.components

import com.twilighty.lmusic.presentation.navigation.navigateSafely

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.model.Playlist
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.data.model.SortOption
import com.twilighty.lmusic.presentation.components.subcomps.SineWaveLine
import com.twilighty.lmusic.presentation.navigation.Screen
import com.twilighty.lmusic.presentation.screens.PlayerSheetCollapsedCornerRadius
import com.twilighty.lmusic.presentation.viewmodel.PlayerViewModel
import com.twilighty.lmusic.presentation.viewmodel.PlaylistUiState
import com.twilighty.lmusic.presentation.viewmodel.PlaylistSelectionStateHolder
import com.twilighty.lmusic.utils.formatSongCount
import com.twilighty.lmusic.ui.theme.GoogleSansRounded
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.flow.map

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistContainer(
    playlistUiState: PlaylistUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    currentSong: Song? = null,
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    filteredPlaylists: List<Playlist> = playlistUiState.playlists,
    currentSortOption: SortOption? = null,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {},
    playlistSelectionStateHolder: PlaylistSelectionStateHolder? = null
) {

    Column(modifier = Modifier.fillMaxSize()) {
        if (playlistUiState.isLoading && filteredPlaylists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (filteredPlaylists.isEmpty() && !playlistUiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SineWaveLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        alpha = 0.95f,
                        strokeWidth = 3.dp,
                        amplitude = 4.dp,
                        waves = 7.6f,
                        phase = 0f
                    )
                    Spacer(Modifier.height(16.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.presentation_batch_e_no_playlist_created),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.presentation_batch_e_new_playlist_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (isAddingToPlaylist) {
                PlaylistItems(
                    currentSong = currentSong,
                    bottomBarHeight = bottomBarHeight,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    isAddingToPlaylist = true,
                    filteredPlaylists = filteredPlaylists,
                    selectedPlaylists = selectedPlaylists,
                    currentSortOption = currentSortOption
                )
            } else {
                val playlistPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = playlistPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = playlistPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    PlaylistItems(
                        bottomBarHeight = bottomBarHeight,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        filteredPlaylists = filteredPlaylists,
                        currentSortOption = currentSortOption,
                        isSelectionMode = isSelectionMode,
                        selectedPlaylistIds = selectedPlaylistIds,
                        onPlaylistLongPress = onPlaylistLongPress,
                        onPlaylistSelectionToggle = onPlaylistSelectionToggle
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItems(
    bottomBarHeight: Dp,
    navController: NavController?,
    currentSong: Song? = null,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    filteredPlaylists: List<Playlist>,
    currentSortOption: SortOption? = null,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {}
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val playlistFastScrollLabelProvider = remember(filteredPlaylists, currentSortOption) {
        { index: Int ->
            playlistFastScrollLabel(
                playlist = filteredPlaylists.getOrNull(index),
                sortOption = currentSortOption
            )
        }
    }
    var lastHandledPlaylistSortKey by remember { mutableStateOf(currentSortOption?.storageKey) }
    var pendingPlaylistSortScrollReset by remember { mutableStateOf(false) }

    LaunchedEffect(currentSortOption) {
        val currentSortKey = currentSortOption?.storageKey ?: return@LaunchedEffect
        if (currentSortKey == lastHandledPlaylistSortKey) return@LaunchedEffect
        lastHandledPlaylistSortKey = currentSortKey
        pendingPlaylistSortScrollReset = true
        listState.scrollToItem(0)
    }

    LaunchedEffect(filteredPlaylists, pendingPlaylistSortScrollReset) {
        if (!pendingPlaylistSortScrollReset) return@LaunchedEffect
        listState.scrollToItem(0)
        pendingPlaylistSortScrollReset = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp, bottom = 6.dp)
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = PlayerSheetCollapsedCornerRadius,
                        bottomEnd = PlayerSheetCollapsedCornerRadius
                    )
                ),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
        ) {
            items(filteredPlaylists, key = { it.id }) { playlist ->
                val rememberedOnClick = remember(playlist.id) {
                    {
                        if (isAddingToPlaylist && currentSong != null && selectedPlaylists != null) {
                            val currentSelection = selectedPlaylists[playlist.id] ?: false
                            selectedPlaylists[playlist.id] = !currentSelection
                        } else if (isSelectionMode) {
                            onPlaylistSelectionToggle(playlist)
                        } else {
                            navController?.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                        }
                    }
                }
                val selectionIndex = remember(playlist.id, selectedPlaylistIds) {
                    if (selectedPlaylistIds.contains(playlist.id)) {
                        selectedPlaylistIds.toList().indexOf(playlist.id)
                    } else {
                        -1
                    }
                }
                PlaylistItem(
                    playlist = playlist,
                    playerViewModel = playerViewModel,
                    onClick = { rememberedOnClick() },
                    isAddingToPlaylist = isAddingToPlaylist,
                    selectedPlaylists = selectedPlaylists,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPlaylistIds.contains(playlist.id),
                    selectionIndex = selectionIndex,
                    onLongPress = { onPlaylistLongPress(playlist) },
                    onPlaylistSelectionToggle = { onPlaylistSelectionToggle(playlist) }
                )
            }
        }
        
        val bottomPadding = if (stablePlayerState.currentSong != null && stablePlayerState.currentSong != Song.emptySong()) 
            bottomBarHeight + MiniPlayerHeight + 16.dp 
        else 
            bottomBarHeight + 16.dp

        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
            listState = listState,
            dragLabelProvider = playlistFastScrollLabelProvider
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    isAddingToPlaylist: Boolean,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int = -1,
    onLongPress: () -> Unit = {},
    onPlaylistSelectionToggle: () -> Unit = {}
) {
    val playlistPreviewSongIds = remember(playlist.songIds) {
        playlist.songIds.take(4)
    }
    val playlistSongsInitialValue = remember(playlistPreviewSongIds) {
        if (playlistPreviewSongIds.isEmpty()) emptyList<Song>() else null
    }
    val playlistSongs by remember(playlistPreviewSongIds, playerViewModel) {
        playerViewModel.observeSongs(playlistPreviewSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = playlistSongsInitialValue)

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playlistSelectionScaleAnimation"
    )

    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "playlistSelectionBorderAnimation"
    )

    // Daylight playlist row: flat, no card fill; a muted fill marks selection.
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> com.twilighty.lmusic.presentation.nb.Nb.muted
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 300),
        label = "playlistContainerColorAnimation"
    )
    val rowShape = com.twilighty.lmusic.presentation.nb.NbShapes.lg

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .clip(rowShape)
            .background(containerColor)
            .then(
                if (isSelected && !isAddingToPlaylist) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = com.twilighty.lmusic.presentation.nb.Nb.ink,
                        shape = rowShape
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onPlaylistSelectionToggle()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongPress()
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs ?: emptyList(),
                size = 56.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playlist.name,
                        style = com.twilighty.lmusic.presentation.nb.NbType.title,
                        color = com.twilighty.lmusic.presentation.nb.Nb.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.isAiGenerated) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.gemini_ai),
                            contentDescription = "AI Generated",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (playlist.source == "NETEASE") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                            contentDescription = "Netease Cloud Music",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (playlist.source == "TELEGRAM" || playlist.source == "TELEGRAM_TOPIC") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.telegram),
                            contentDescription = "Telegram",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (playlist.source == "TELEGRAM_TOPIC") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Topic,
                            contentDescription = "Topic",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (playlist.source == "QQMUSIC") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = "QQ Music",
                            tint = com.twilighty.lmusic.presentation.nb.Nb.successText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (playlist.source == "NAVIDROME") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_navidrome),
                            contentDescription = "Navidrome",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = formatSongCount(playlist.songIds.size),
                    style = com.twilighty.lmusic.presentation.nb.NbType.meta,
                    color = com.twilighty.lmusic.presentation.nb.Nb.textMuted
                )
            }

            if (isSelected && isSelectionMode) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex >= 0) {
                        Text(
                            text = (selectionIndex + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.presentation_batch_g_cd_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (isAddingToPlaylist && selectedPlaylists != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                    checked = selectedPlaylists[playlist.id] ?: false,
                    onCheckedChange = { isChecked -> selectedPlaylists[playlist.id] = isChecked }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialogRedesigned(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onGenerateClick: () -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    com.twilighty.lmusic.presentation.nb.NbDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.create_playlist_dialog_title),
        actions = {
            com.twilighty.lmusic.presentation.nb.NbButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = com.twilighty.lmusic.presentation.nb.NbButtonStyle.Secondary
            )
            com.twilighty.lmusic.presentation.nb.NbButton(
                text = stringResource(R.string.action_create),
                onClick = { onCreate(playlistName) },
                enabled = playlistName.isNotBlank()
            )
        }
    ) {
        com.twilighty.lmusic.presentation.nb.NbTextField(
            value = playlistName,
            onValueChange = { playlistName = it },
            label = stringResource(R.string.create_playlist_name_label),
            placeholder = stringResource(R.string.create_playlist_name_placeholder),
            singleLine = true
        )
    }
}
