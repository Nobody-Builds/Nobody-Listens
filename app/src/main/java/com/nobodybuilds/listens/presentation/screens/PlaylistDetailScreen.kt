package com.nobodybuilds.listens.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.QueuePlaylistSongItem
import com.nobodybuilds.listens.presentation.components.ExpressiveScrollBar
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel.Companion.FOLDER_PLAYLIST_PREFIX
import com.nobodybuilds.listens.presentation.utils.LocalAppHapticsConfig
import com.nobodybuilds.listens.utils.formatSongCount
import com.nobodybuilds.listens.utils.formatTotalDuration
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import com.nobodybuilds.listens.ui.theme.NobodyListensStatusBarStyle
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbChip
import com.nobodybuilds.listens.presentation.nb.NbCollectionHeader
import com.nobodybuilds.listens.presentation.nb.NbDetailAppBar
import com.nobodybuilds.listens.presentation.nb.NbEmptyState
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbPlayShuffleRow
import com.nobodybuilds.listens.presentation.nb.NbType

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fallbackPlaylistName = stringResource(R.string.shortcut_playlist_short)
    val sortSongsLabel = stringResource(R.string.presentation_batch_b_sort_songs)
    val moreOptionsLabel = stringResource(R.string.presentation_batch_b_more_options)
    val playItLabel = stringResource(R.string.presentation_batch_b_play_it)
    val shuffleLabel = stringResource(R.string.shortcut_shuffle_short)
    val addSongsCd = stringResource(R.string.presentation_batch_b_add_songs)
    val addLabel = stringResource(R.string.presentation_batch_b_add)
    val removeLabel = stringResource(R.string.cd_remove)
    val removeSongsCd = stringResource(R.string.presentation_batch_b_remove_songs)
    val reorderLabel = stringResource(R.string.presentation_batch_b_reorder)
    val reorderSongsCd = stringResource(R.string.presentation_batch_b_reorder_songs)
    val reorderSongCd = stringResource(R.string.presentation_batch_b_reorder_song)
    val playlistEmptyTitle = stringResource(R.string.presentation_batch_b_playlist_empty_title)
    val playlistEmptyFolder = stringResource(R.string.presentation_batch_b_playlist_empty_folder_body)
    val playlistEmptyAddHint = stringResource(R.string.presentation_batch_b_playlist_empty_add_hint)
    val editPlaylistLabel = stringResource(R.string.presentation_batch_b_edit_playlist)
    val currentPlaylist = uiState.currentPlaylistDetails
    val isFolderPlaylist = currentPlaylist?.id?.startsWith(FOLDER_PLAYLIST_PREFIX) == true
    val songsInPlaylist = uiState.currentPlaylistSongs

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    val detailSheets = remember { PlaylistDetailSheetsState() }
    var showAddSongsSheet by detailSheets.showAddSongsSheet

    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var isRemoveModeEnabled by remember { mutableStateOf(false) }
    var showSongInfoBottomSheet by detailSheets.showSongInfoBottomSheet
    var showPlaylistOptionsSheet by detailSheets.showPlaylistOptionsSheet
    var showEditPlaylistDialog by detailSheets.showEditPlaylistDialog

    val m3uExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri?.let {
            currentPlaylist?.let { playlist ->
                playlistViewModel.exportM3u(playlist, it, context)
            }
        }
    }

    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    var localReorderableSongs by remember(songsInPlaylist) { mutableStateOf(songsInPlaylist) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            // The list has header rows above the songs, so map by key, not by list index.
            val fromIndex = localReorderableSongs.indexOfFirst { it.id == from.key }
            val toIndex = localReorderableSongs.indexOfFirst { it.id == to.key }
            if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
            localReorderableSongs = localReorderableSongs.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = fromIndex
            }
            lastMovedTo = toIndex
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging, isFolderPlaylist) {
        if (!isFolderPlaylist && !reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            currentPlaylist?.let {
                playlistViewModel.reorderSongsInPlaylist(it.id, lastMovedFrom!!, lastMovedTo!!)
            }
            lastMovedFrom = null
            lastMovedTo = null
        } else if (isFolderPlaylist && !reorderableState.isAnyItemDragging) {
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    val headerScrolledAway by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    NobodyListensStatusBarStyle(color = MaterialTheme.colorScheme.surface)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nb.canvas)
    ) {
        NbDetailAppBar(
            onBack = onBackClick,
            title = if (headerScrolledAway) currentPlaylist?.name ?: fallbackPlaylistName else null,
            backContentDescription = stringResource(R.string.auth_cd_back),
            actions = {
                NbIconButton(
                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = sortSongsLabel,
                    onClick = { playerViewModel.showSortingSheet() }
                )
                if (!isFolderPlaylist) {
                    NbIconButton(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = editPlaylistLabel,
                        onClick = { showEditPlaylistDialog = true }
                    )
                    NbIconButton(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = moreOptionsLabel,
                        onClick = { showPlaylistOptionsSheet = true }
                    )
                }
            }
        )
        if (uiState.isLoading && currentPlaylist == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Nb.ink) }
        } else if (uiState.playlistNotFound) {
            Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                Text(stringResource(id = R.string.playlist_not_found), style = NbType.body, color = Nb.textSecondary)
            }
        } else if (currentPlaylist == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Nb.ink) }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                    )
                ) {
                    item(key = "playlist_header", contentType = "playlist_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            NbCollectionHeader(
                                overline = if (isFolderPlaylist) "Folder" else "Playlist",
                                title = currentPlaylist.name,
                                caption = stringResource(
                                    R.string.presentation_batch_f_status_bullet_step,
                                    formatSongCount(songsInPlaylist.size),
                                    formatTotalDuration(songsInPlaylist)
                                ).replace(" • ", " · ")
                            ) {
                                PlaylistMosaicArt(
                                    name = currentPlaylist.name,
                                    coverImageUri = currentPlaylist.coverImageUri,
                                    songs = songsInPlaylist
                                )
                            }
                            NbPlayShuffleRow(
                                enabled = localReorderableSongs.isNotEmpty(),
                                playLabel = playItLabel,
                                shuffleLabel = shuffleLabel,
                                onPlay = {
                                    if (localReorderableSongs.isNotEmpty()) {
                                        playerViewModel.playSongs(
                                            localReorderableSongs,
                                            localReorderableSongs.first(),
                                            currentPlaylist.name
                                        )
                                        if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                    }
                                },
                                onShuffle = {
                                    if (localReorderableSongs.isNotEmpty()) {
                                        playerViewModel.playSongsShuffled(
                                            songsToPlay = localReorderableSongs,
                                            queueName = currentPlaylist.name,
                                            playlistId = currentPlaylist.id,
                                            startAtZero = true,
                                        )
                                    }
                                }
                            )
                            if (!isFolderPlaylist && localReorderableSongs.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NbChip(
                                        text = reorderLabel,
                                        selected = isReorderModeEnabled,
                                        onClick = { isReorderModeEnabled = !isReorderModeEnabled },
                                        icon = rememberVectorPainter(Icons.Rounded.DragIndicator)
                                    )
                                    NbChip(
                                        text = removeLabel,
                                        selected = isRemoveModeEnabled,
                                        onClick = { isRemoveModeEnabled = !isRemoveModeEnabled },
                                        icon = rememberVectorPainter(Icons.Rounded.RemoveCircleOutline)
                                    )
                                }
                            }
                        }
                    }

                    if (!isFolderPlaylist) {
                        item(key = "playlist_add_songs", contentType = "playlist_add") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { showAddSongsSheet = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Nb.muted)
                                        .border(1.5.dp, Nb.borderStrong, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = addSongsCd, tint = Nb.text)
                                }
                                Text(addSongsCd, style = NbType.title, color = Nb.textSecondary)
                            }
                        }
                    }

                    if (localReorderableSongs.isEmpty()) {
                        item(key = "playlist_empty", contentType = "playlist_empty") {
                            NbEmptyState(
                                title = playlistEmptyTitle,
                                body = if (isFolderPlaylist) playlistEmptyFolder else playlistEmptyAddHint,
                                icon = rememberVectorPainter(Icons.Rounded.MusicOff),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }

                    itemsIndexed(
                        localReorderableSongs,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "playlist_song" }) { _, song ->
                        ReorderableItem(
                            state = reorderableState,
                            key = song.id,
                        ) { isDragging ->
                            QueuePlaylistSongItem(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    playerViewModel.playSongs(
                                        localReorderableSongs,
                                        song,
                                        currentPlaylist.name,
                                        currentPlaylist.id
                                    )
                                },
                                song = song,
                                isCurrentSong = playerStableState.currentSong?.id == song.id,
                                isPlaying = playerStableState.isPlaying,
                                isDragging = isDragging,
                                onRemoveClick = {
                                    if (!isFolderPlaylist) {
                                        currentPlaylist.let {
                                            playlistViewModel.removeSongFromPlaylist(it.id, song.id)
                                        }
                                    }
                                },
                                isFromPlaylist = true,
                                isReorderModeEnabled = isReorderModeEnabled,
                                isDragHandleVisible = isReorderModeEnabled,
                                isRemoveButtonVisible = isRemoveModeEnabled,
                                onMoreOptionsClick = stableOnMoreOptionsClick,
                                dragHandle = {
                                    Box(
                                        modifier = Modifier
                                            .draggableHandle(
                                                onDragStarted = {
                                                    com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().dragLift()
                                                },
                                                onDragStopped = {
                                                    com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().dragDrop()
                                                }
                                            )
                                            .size(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = reorderSongCd,
                                            tint = Nb.textMuted
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (headerScrolledAway) {
                    ExpressiveScrollBar(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                bottom = if (playerStableState.currentSong != null) MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                                end = 4.dp,
                                top = 12.dp
                            )
                    )
                }
            }
        }
    }

    PlaylistDetailSheets(
        detailSheets = detailSheets,
        playlistId = playlistId,
        uiState = uiState,
        currentPlaylist = currentPlaylist,
        isFolderPlaylist = isFolderPlaylist,
        playerViewModel = playerViewModel,
        playlistViewModel = playlistViewModel,
        navController = navController,
        onDeletePlayListClick = onDeletePlayListClick,
        m3uExportLauncher = m3uExportLauncher,
        scope = scope,
        listState = listState
    )
}
