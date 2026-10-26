package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.navigation.navigateSafelyReplacing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.size.Size
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.PlaylistBottomSheet
import com.nobodybuilds.listens.presentation.components.QueuePlaylistSongItem
import com.nobodybuilds.listens.presentation.components.SongPickerBottomSheet
import com.nobodybuilds.listens.presentation.components.ExpressiveScrollBar
import com.nobodybuilds.listens.presentation.components.SmartImage
import com.nobodybuilds.listens.presentation.components.SongInfoBottomSheet
import com.nobodybuilds.listens.presentation.components.resolveNavBarOccupiedHeight
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel.Companion.FOLDER_PLAYLIST_PREFIX
import com.nobodybuilds.listens.presentation.utils.LocalAppHapticsConfig
import com.nobodybuilds.listens.presentation.utils.performAppCompatHapticFeedback
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistSongsOrderMode
import com.nobodybuilds.listens.utils.formatSongCount
import com.nobodybuilds.listens.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.nobodybuilds.listens.presentation.components.LibrarySortBottomSheet
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.model.PlaylistShapeType
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import com.nobodybuilds.listens.ui.theme.NobodyListensStatusBarStyle
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbActionRow
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbChip
import com.nobodybuilds.listens.presentation.nb.NbCollectionHeader
import com.nobodybuilds.listens.presentation.nb.NbDetailAppBar
import com.nobodybuilds.listens.presentation.nb.NbDialog
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbEmptyState
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbPlayShuffleRow
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbSheetHandle
import com.nobodybuilds.listens.presentation.nb.NbSheetShape
import com.nobodybuilds.listens.presentation.nb.NbTextField
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
    val playlistOptionsTitle = stringResource(R.string.presentation_batch_b_playlist_options_title)
    val editPlaylistLabel = stringResource(R.string.presentation_batch_b_edit_playlist)
    val deletePlaylistLabel = stringResource(R.string.presentation_batch_b_delete_playlist)
    val setDefaultTransitionLabel = stringResource(R.string.presentation_batch_b_set_default_transition)
    val exportPlaylistLabel = stringResource(R.string.presentation_batch_b_export_playlist)
    val deletePlaylistConfirmTitle = stringResource(R.string.presentation_batch_b_delete_playlist_confirm_title)
    val deletePlaylistConfirmBody = stringResource(R.string.presentation_batch_b_delete_playlist_confirm_body)
    val sortSheetTitle = stringResource(R.string.presentation_batch_b_sort_songs)
    val toastAddedToQueue = stringResource(R.string.toast_added_to_queue)
    val toastPlayingNext = stringResource(R.string.toast_playing_next)
    val currentPlaylist = uiState.currentPlaylistDetails
    val isFolderPlaylist = currentPlaylist?.id?.startsWith(FOLDER_PLAYLIST_PREFIX) == true
    val songsInPlaylist = uiState.currentPlaylistSongs

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }

    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var isRemoveModeEnabled by remember { mutableStateOf(false) }
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val m3uExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri?.let {
            currentPlaylist?.let { playlist ->
                playlistViewModel.exportM3u(playlist, it, context)
            }
        }
    }

    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle() // Reintroducir favoriteIds aquí
    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
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

    if (showAddSongsSheet && currentPlaylist != null && !isFolderPlaylist) {
        SongPickerBottomSheet(
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedIds ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIds.toList())
                showAddSongsSheet = false
            }
        )
    }
    if (showPlaylistOptionsSheet && !isFolderPlaylist) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        com.nobodybuilds.listens.presentation.haptics.HapticBottomSheetEffect(sheetState)
        ModalBottomSheet(
            onDismissRequest = { showPlaylistOptionsSheet = false },
            sheetState = sheetState,
            containerColor = Nb.canvas,
            tonalElevation = 0.dp,
            shape = NbSheetShape,
            dragHandle = { NbSheetHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = playlistOptionsTitle,
                        style = NbType.h3,
                        color = Nb.text
                    )
                    currentPlaylist?.name?.let {
                        Text(
                            text = it,
                            style = NbType.meta,
                            color = Nb.textMuted
                        )
                    }
                }
                NbDivider(Modifier.padding(vertical = 6.dp))
                NbActionRow(
                    icon = painterResource(R.drawable.rounded_edit_24),
                    text = editPlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showEditPlaylistDialog = true
                    }
                )
                NbActionRow(
                    icon = painterResource(R.drawable.rounded_graph_1_24),
                    text = setDefaultTransitionLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        navController.navigateSafely(Screen.EditTransition.createRoute(playlistId))
                    }
                )
                NbActionRow(
                    icon = painterResource(R.drawable.rounded_attach_file_24),
                    text = exportPlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        m3uExportLauncher.launch("${currentPlaylist?.name ?: fallbackPlaylistName}.m3u")
                    }
                )
                NbDivider(Modifier.padding(vertical = 4.dp))
                NbActionRow(
                    icon = painterResource(R.drawable.rounded_delete_24),
                    text = deletePlaylistLabel,
                    danger = true,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showDeleteConfirmation = true
                    }
                )
            }
        }
    }
    
    if (showEditPlaylistDialog && currentPlaylist != null) {
        val initialShapeType = try {
            currentPlaylist.coverShapeType?.let { PlaylistShapeType.valueOf(it) } ?: PlaylistShapeType.Circle
        } catch (e: Exception) {
            PlaylistShapeType.Circle
        }
        
        EditPlaylistDialog(
            visible = showEditPlaylistDialog,
            currentName = currentPlaylist.name,
            currentImageUri = currentPlaylist.coverImageUri,
            currentColor = currentPlaylist.coverColorArgb,
            currentIconName = currentPlaylist.coverIconName,
            currentShapeType = initialShapeType,
            currentShapeDetail1 = currentPlaylist.coverShapeDetail1,
            currentShapeDetail2 = currentPlaylist.coverShapeDetail2,
            currentShapeDetail3 = currentPlaylist.coverShapeDetail3,
            currentShapeDetail4 = currentPlaylist.coverShapeDetail4,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { name, imageUri, color, icon, scale, panX, panY, shapeType, d1, d2, d3, d4 ->
                playlistViewModel.updatePlaylistParameters(
                    playlistId = currentPlaylist.id,
                    name = name,
                    coverImageUri = imageUri,
                    coverColor = color,
                    coverIcon = icon,
                    cropScale = scale,
                    cropPanX = panX,
                    cropPanY = panY,
                    coverShapeType = shapeType,
                    coverShapeDetail1 = d1,
                    coverShapeDetail2 = d2,
                    coverShapeDetail3 = d3,
                    coverShapeDetail4 = d4
                )
                showEditPlaylistDialog = false
            }
        )
    }
    if (showDeleteConfirmation && currentPlaylist != null) {
        NbDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = deletePlaylistConfirmTitle,
            body = deletePlaylistConfirmBody,
            actions = {
                NbButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeleteConfirmation = false },
                    style = NbButtonStyle.Secondary
                )
                NbButton(
                    text = stringResource(R.string.delete_action),
                    onClick = {
                        playlistViewModel.deletePlaylist(currentPlaylist.id)
                        onDeletePlayListClick()
                        showDeleteConfirmation = false
                    },
                    style = NbButtonStyle.Danger
                )
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            derivedStateOf {
                currentSong?.let {
                    favoriteIds.contains(
                        it.id
                    )
                }
            }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                    playerViewModel.toggleFavoriteSpecificSong(currentSong) // Assumes such a method exists or will be added to PlayerViewModel
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(toastAddedToQueue)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(toastPlayingNext)
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafelyReplacing(
                            route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                            patternToPop = Screen.GenreDetail.route
                        )
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                },
                removeFromListTrigger = {
                    playlistViewModel.removeSongFromPlaylist(playlistId, currentSong.id)
                }
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(currentSong),
                    onDismiss = {
                        showPlaylistBottomSheet = false
                    },
                    currentPlaylistId = playlistId,
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }

    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()

    if (isSortSheetVisible) {
        // Check if playlist is in Manual mode (which corresponds to Default Order)
        val isManualMode = uiState.playlistSongsOrderMode is PlaylistSongsOrderMode.Manual
        val rawOption = uiState.currentPlaylistSongsSortOption
        // If in Manual mode, show SongDefaultOrder as selected; otherwise use the stored sort option
        val currentSortOption = if (isManualMode) {
            SortOption.SongDefaultOrder
        } else if (currentPlaylist != null) {
            rawOption
        } else {
            SortOption.SongTitleAZ
        }

        // Build options list inline to avoid potential static initialization issues
        val songSortOptions = listOf(
            SortOption.SongDefaultOrder,
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        )

        LibrarySortBottomSheet(
            title = sortSheetTitle,
            options = songSortOptions,
            selectedOption = currentSortOption,
            onDismiss = { playerViewModel.hideSortingSheet() },
            onOptionSelected = { option ->
                 playlistViewModel.sortPlaylistSongs(option)
                 playerViewModel.hideSortingSheet()
                 // Auto-scroll to first item after sorting (delay to allow list to update)
                 scope.launch {
                     kotlinx.coroutines.delay(100)
                     listState.animateScrollToItem(0)
                 }
            },
            onDirectionToggle = { option ->
                playlistViewModel.sortPlaylistSongs(option)
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(0)
                }
            },
            showViewToggle = false 
        )
    }
}


@Composable
private fun PlaylistMosaicArt(
    name: String,
    coverImageUri: String?,
    songs: List<Song>,
) {
    val size = 132.dp
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val covers = remember(songs) {
        songs.distinctBy { it.albumId }.take(4)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(NbShapes.lg)
            .background(Nb.canvas)
    ) {
        when {
            !coverImageUri.isNullOrBlank() -> {
                NbArt(model = null, title = name, size = size, shape = NbShapes.lg)
                SmartImage(
                    model = coverImageUri,
                    contentDescription = name,
                    shape = NbShapes.lg,
                    targetSize = Size(sizePx, sizePx),
                    placeHolderBackgroundColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize()
                )
            }
            covers.size >= 4 -> {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    covers.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            pair.forEach { song ->
                                NbArt(
                                    model = song.albumArtUriString,
                                    title = song.album.ifBlank { song.title },
                                    size = (size - 2.dp) / 2,
                                    shape = RectangleShape
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                NbArt(
                    model = covers.firstOrNull()?.albumArtUriString,
                    title = name,
                    size = size,
                    shape = NbShapes.lg
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// SongPickerBottomSheet moved to com.nobodybuilds.listens.presentation.components
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(TextFieldValue(currentName)) }
    val renameTitle = stringResource(R.string.presentation_batch_b_rename_playlist_dialog_title)
    val newNameLabel = stringResource(R.string.presentation_batch_b_new_name)
    val renameAction = stringResource(R.string.action_rename)
    NbDialog(
        onDismissRequest = onDismiss,
        title = renameTitle,
        actions = {
            NbButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = NbButtonStyle.Secondary
            )
            NbButton(
                text = renameAction,
                onClick = { if (newName.text.isNotBlank()) onRename(newName.text) },
                enabled = newName.text.isNotBlank() && newName.text != currentName
            )
        }
    ) {
        NbTextField(
            value = newName,
            onValueChange = { newName = it },
            label = newNameLabel,
            singleLine = true
        )
    }
}
