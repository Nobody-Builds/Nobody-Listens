@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.SortOption
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.components.subcomps.LibraryActionRow
import com.nobodybuilds.listens.presentation.components.subcomps.SelectionActionRow
import com.nobodybuilds.listens.presentation.components.subcomps.SelectionCountPill
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.data.model.LibraryTabId
import com.nobodybuilds.listens.data.model.toLibraryTabIdOrNull
import com.nobodybuilds.listens.presentation.viewmodel.LibraryViewModel
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import com.nobodybuilds.listens.data.model.Playlist

private const val ENABLE_FOLDERS_STORAGE_FILTER = false

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun LibraryScreenContent(
    innerScaffoldPadding: PaddingValues,
    screenState: LibraryScreenState,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel,
    libraryViewModel: LibraryViewModel,
    headerContainerColor: Color,
    pagerState: PagerState,
    tabTitles: List<String>,
    currentTabId: LibraryTabId,
    currentTabIndex: Int,
    isCompactNavigation: Boolean,
    bottomBarHeightDp: Dp,
    /** Read only where the button draws, so the 300 ms tab animation doesn't recompose this screen. */
    iconRotation: () -> Float,
    isSortSheetVisible: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    inlineSyncVisible: Boolean,
    scope: CoroutineScope,
    m3uImportLauncher: ManagedActivityResultLauncher<String, Uri?>,
    stableOnMoreOptionsClick: (Song) -> Unit,
    selectedSongs: List<Song>,
    selectedSongIds: Set<String>,
    isSelectionMode: Boolean,
    onSongLongPress: (Song) -> Unit,
    onSongSelectionToggle: (Song) -> Unit,
    selectedAlbumIds: Set<Long>,
    isAlbumSelectionMode: Boolean,
    onAlbumLongPress: (Album) -> Unit,
    onAlbumSelectionToggle: (Album) -> Unit,
    getAlbumSelectionIndex: (Long) -> Int?,
    selectedPlaylists: List<Playlist>,
    selectedPlaylistIds: Set<String>,
    isPlaylistSelectionMode: Boolean,
    onPlaylistLongPress: (Playlist) -> Unit,
    onPlaylistSelectionToggle: (Playlist) -> Unit
) {
    val context = LocalContext.current
    val syncManager = playerViewModel.syncManager
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val playlistMultiSelectionState = playerViewModel.playlistSelectionStateHolder
    var showPlaylistCreationTypeDialog by screenState.showPlaylistCreationTypeDialog
    var showMultiSelectionSheet by screenState.showMultiSelectionSheet
    var selectedAlbums by screenState.selectedAlbums
    var showAlbumMultiSelectionSheet by screenState.showAlbumMultiSelectionSheet
    val songsShowLocateButton by screenState.songsShowLocateButton
    val likedShowLocateButton by screenState.likedShowLocateButton
    val foldersShowLocateButton by screenState.foldersShowLocateButton
    val songsLocateAction by screenState.songsLocateAction
    val likedLocateAction by screenState.likedLocateAction
    val foldersLocateAction by screenState.foldersLocateAction
    var showPlaylistMultiSelectionSheet by screenState.showPlaylistMultiSelectionSheet
    val playerUiState by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState -> uiState.toLibraryScreenProjection() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = LibraryScreenPlayerProjection())
    val isLibraryContentEmpty by remember(playerViewModel) {
        combine(
            playerViewModel.songCountFlow,
            playerViewModel.albumsFlow,
            playerViewModel.artistsFlow
        ) { songCount, albums, artists ->
            songCount == 0 && albums.isEmpty() && artists.isEmpty()
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = true)

    Box(
        modifier = Modifier
            .padding(top = innerScaffoldPadding.calculateTopPadding())
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .background(color = headerContainerColor)
                .fillMaxSize()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 0.dp, vertical = 0.dp), // Removed horizontal padding for more space
                color = MaterialTheme.colorScheme.surface,
                shape = androidx.compose.ui.graphics.RectangleShape
                // shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 24.dp, smoothnessAsPercentTR = 60, /*...*/) // Your custom shape
            ) {
                Column(Modifier.fillMaxSize()) {
                    // OPTIMIZACIÓN: La lógica de ordenamiento ahora es más eficiente.
                    val availableSortOptions by playerViewModel.availableSortOptions.collectAsStateWithLifecycle()
                    val sanitizedSortOptions = remember(availableSortOptions, currentTabId) {
                        val cleaned = availableSortOptions.filterIsInstance<SortOption>()
                        val ensured = if (cleaned.any { option ->
                                option.storageKey == currentTabId.defaultSort.storageKey
                            }
                        ) {
                            cleaned
                        } else {
                            buildList {
                                add(currentTabId.defaultSort)
                                addAll(cleaned)
                            }
                        }

                        val distinctByKey = ensured.distinctBy { it.storageKey }
                        distinctByKey.ifEmpty { listOf(currentTabId.defaultSort) }
                    }

                    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
                    val visiblePlaylists = remember(
                        playlistUiState.playlists,
                        playlistUiState.showTelegramCloudPlaylists,
                        playlistUiState.telegramTopicDisplayMode
                    ) {
                        val mode = playlistUiState.telegramTopicDisplayMode
                        val allPlaylists = playlistUiState.playlists

                        // When Telegram cloud is hidden, remove all Telegram playlists
                        if (!playlistUiState.showTelegramCloudPlaylists) {
                            return@remember allPlaylists.filterNot {
                                it.source == "TELEGRAM" || it.source == "TELEGRAM_TOPIC"
                            }
                        }

                        allPlaylists.filter { playlist ->
                            when (playlist.source) {
                                "TELEGRAM_TOPIC" -> when (mode) {
                                    com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.CHANNELS_ONLY ->
                                        false
                                    com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.TOPICS_ONLY,
                                    com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.CHANNELS_AND_TOPICS ->
                                        playlist.songIds.isNotEmpty()
                                }
                                "TELEGRAM" -> when (mode) {
                                    com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.CHANNELS_ONLY ->
                                        true
                                    com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.TOPICS_ONLY -> {
                                        // Hide combined playlist only for forum channels
                                        // (those that have at least one topic playlist)
                                        val chatId = playlist.id
                                            .removePrefix("telegram_channel:")
                                            .toLongOrNull()
                                        if (chatId != null) {
                                            allPlaylists.none { p ->
                                                p.source == "TELEGRAM_TOPIC" &&
                                                        p.id.startsWith("telegram_topic:${chatId}_")
                                            }
                                        } else true
                                    }
                                    com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.CHANNELS_AND_TOPICS ->
                                        true
                                }
                                else -> true
                            }
                        }
                    }
                    val allSongsLazyPagingItems = libraryViewModel.songsPagingFlow.collectAsLazyPagingItems()
                    val albumsLazyPagingItems = libraryViewModel.albumsPagingFlow.collectAsLazyPagingItems()
                    val artistsLazyPagingItems = libraryViewModel.artistsPagingFlow.collectAsLazyPagingItems()
                    val favoritePagingItems = libraryViewModel.favoritesPagingFlow.collectAsLazyPagingItems()
                    val isLibraryLoading by libraryViewModel.isLoadingLibrary.collectAsStateWithLifecycle()
                    val hasCurrentSong by remember(playerViewModel) {
                        playerViewModel.stablePlayerState
                            .map { state -> state.currentSong != null && state.currentSong != Song.emptySong() }
                            .distinctUntilChanged()
                    }.collectAsStateWithLifecycle(initialValue = false)
                    val isShuffleEnabled by remember(playerViewModel) {
                        playerViewModel.stablePlayerState
                            .map { it.isShuffleEnabled }
                            .distinctUntilChanged()
                    }.collectAsStateWithLifecycle(initialValue = false)

                    LaunchedEffect(
                        playlistUiState.showTelegramCloudPlaylists,
                        selectedPlaylists
                    ) {
                        if (playlistUiState.showTelegramCloudPlaylists) return@LaunchedEffect

                        selectedPlaylists
                            .filter { it.source == "TELEGRAM" || it.source == "TELEGRAM_TOPIC" }
                            .forEach { playlist ->
                                playlistMultiSelectionState.removeFromSelection(playlist.id)
                            }
                    }

                    val currentSelectedSortOption: SortOption? = when (currentTabId) {
                        LibraryTabId.SONGS -> playerUiState.currentSongSortOption
                        LibraryTabId.ALBUMS -> playerUiState.currentAlbumSortOption
                        LibraryTabId.ARTISTS -> playerUiState.currentArtistSortOption
                        LibraryTabId.PLAYLISTS -> playlistUiState.currentPlaylistSortOption
                        LibraryTabId.LIKED -> playerUiState.currentFavoriteSortOption
                        LibraryTabId.FOLDERS -> playerUiState.currentFolderSortOption
                    }

                    val showLocateButton = when (currentTabId) {
                        LibraryTabId.SONGS -> songsShowLocateButton
                        LibraryTabId.LIKED -> likedShowLocateButton
                        LibraryTabId.FOLDERS -> foldersShowLocateButton
                        else -> false
                    }
                    val locateAction = when (currentTabId) {
                        LibraryTabId.SONGS -> songsLocateAction
                        LibraryTabId.LIKED -> likedLocateAction
                        LibraryTabId.FOLDERS -> foldersLocateAction
                        else -> null
                    }

                    val onSortOptionChanged: (SortOption) -> Unit = remember(playerViewModel, playlistViewModel, currentTabId) {
                        { option ->
                            when (currentTabId) {
                                LibraryTabId.SONGS -> playerViewModel.sortSongs(option)
                                LibraryTabId.ALBUMS -> playerViewModel.sortAlbums(option)
                                LibraryTabId.ARTISTS -> playerViewModel.sortArtists(option)
                                LibraryTabId.PLAYLISTS -> playlistViewModel.sortPlaylists(option)
                                LibraryTabId.LIKED -> playerViewModel.sortFavoriteSongs(option)
                                LibraryTabId.FOLDERS -> playerViewModel.sortFolders(option)
                            }
                        }
                    }

                    // Switch between normal action row and selection action row
                    AnimatedContent(
                        targetState = isSelectionMode || isPlaylistSelectionMode || isAlbumSelectionMode,
                        label = "ActionRowModeSwitch",
                        transitionSpec = {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut())
                        },
                        modifier = Modifier
                            .padding(
                                top = 4.dp,
                                start = 12.dp,
                                end = 16.dp
                            )
                            .heightIn(min = 52.dp)
                    ) { inSelectionMode ->
                        if (inSelectionMode) {
                            // Playlist selection row
                            if (currentTabId == LibraryTabId.PLAYLISTS && isPlaylistSelectionMode) {
                                SelectionActionRow(
                                    selectedCount = selectedPlaylists.size,
                                    onSelectAll = {
                                        playerViewModel.playlistSelectionStateHolder.selectAll(visiblePlaylists)
                                    },
                                    onDeselect = { playerViewModel.playlistSelectionStateHolder.clearSelection() },
                                    onOptionsClick = { showPlaylistMultiSelectionSheet = true }
                                )
                            } else if (currentTabId == LibraryTabId.ALBUMS && isAlbumSelectionMode) {
                                SelectionActionRow(
                                    selectedCount = selectedAlbums.size,
                                    onSelectAll = {
                                        val remaining = MAX_ALBUM_MULTI_SELECTION - selectedAlbums.size
                                        if (remaining <= 0) {
                                            playerViewModel.sendToast(
                                                context.getString(
                                                    R.string.presentation_batch_d_max_albums_selection,
                                                    MAX_ALBUM_MULTI_SELECTION
                                                )
                                            )
                                        } else {
                                            val albumsToAppend = playerViewModel.albumsFlow.value
                                                .filterNot { selectedAlbumIds.contains(it.id) }
                                                .take(remaining)
                                            if (albumsToAppend.isNotEmpty()) {
                                                selectedAlbums = selectedAlbums + albumsToAppend
                                            }
                                        }
                                    },
                                    onDeselect = { selectedAlbums = emptyList() },
                                    onOptionsClick = { showAlbumMultiSelectionSheet = true }
                                )
                            } else {
                                // Song selection row
                                SelectionActionRow(
                                    selectedCount = selectedSongs.size,
                                    onSelectAll = {
                                        when (tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull()) {
                                            LibraryTabId.LIKED -> {
                                                multiSelectionState.selectAll(favoritePagingItems.itemSnapshotList.items)
                                            }
                                            LibraryTabId.FOLDERS -> {
                                                val songsToSelect =
                                                    playerViewModel.playerUiState.value.currentFolder?.songs ?: emptyList()
                                                multiSelectionState.selectAll(songsToSelect)
                                            }
                                            LibraryTabId.SONGS -> {
                                                scope.launch {
                                                    val songsToSelect =
                                                        playerViewModel.getSongsForCurrentLibrarySelection()
                                                    multiSelectionState.selectAll(songsToSelect)
                                                }
                                            }
                                            else -> Unit
                                        }
                                    },
                                    onDeselect = { multiSelectionState.clearSelection() },
                                    onOptionsClick = { showMultiSelectionSheet = true }
                                )
                            }
                        } else {
                            LibraryActionRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp),
                                onMainActionClick = {
                                    when (tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull()) {
                                        LibraryTabId.PLAYLISTS -> showPlaylistCreationTypeDialog = true
                                        LibraryTabId.LIKED -> playerViewModel.shuffleFavoriteSongs()
                                        LibraryTabId.ALBUMS -> playerViewModel.shuffleRandomAlbum()
                                        LibraryTabId.ARTISTS -> playerViewModel.shuffleRandomArtist()
                                        else -> playerViewModel.shuffleAllSongs()
                                    }
                                },
                                iconRotation = iconRotation(),
                                showSortButton = sanitizedSortOptions.isNotEmpty(),
                                showLocateButton = showLocateButton,
                                onSortClick = { playerViewModel.showSortingSheet() },
                                onLocateClick = { locateAction?.invoke() },
                                isPlaylistTab = currentTabId == LibraryTabId.PLAYLISTS,
                                isFoldersTab = currentTabId == LibraryTabId.FOLDERS && (!playerUiState.isFoldersPlaylistView || playerUiState.currentFolder != null),
                                onImportM3uClick = { m3uImportLauncher.launch("audio/x-mpegurl") },
                                currentFolder = playerUiState.currentFolder,
                                folderRootPath = playerUiState.folderSourceRootPath.ifBlank {
                                    Environment.getExternalStorageDirectory().path
                                },
                                folderRootLabel = playerUiState.folderSource.displayName,
                                onFolderClick = { playerViewModel.navigateToFolder(it) },
                                onNavigateBack = { playerViewModel.navigateBackFolder() },
                                isShuffleEnabled = isShuffleEnabled,
                                showStorageFilterButton = currentTabId == LibraryTabId.SONGS ||
                                        currentTabId == LibraryTabId.ALBUMS ||
                                        currentTabId == LibraryTabId.ARTISTS ||
                                        currentTabId == LibraryTabId.LIKED ||
                                        (ENABLE_FOLDERS_STORAGE_FILTER && currentTabId == LibraryTabId.FOLDERS),
                                currentStorageFilter = playerUiState.currentStorageFilter,
                                onStorageFilterClick = { playerViewModel.toggleStorageFilter() },
                                sortLabel = currentSelectedSortOption?.displayName
                            )
                        }
                    }

                    // Slim inline sync indicator. Automatic startup syncs use this
                    // instead of pulling the list down, and manual refreshes hand off
                    // to it when the worker takes longer than the pull gesture window.
                    LibraryInlineSyncIndicator(
                        visible = inlineSyncVisible && !isLibraryContentEmpty,
                        syncManager = syncManager
                    )

                    if (isSortSheetVisible && sanitizedSortOptions.isNotEmpty()) {
                        LibraryTabSortSheet(
                            sanitizedSortOptions = sanitizedSortOptions,
                            currentSelectedSortOption = currentSelectedSortOption,
                            currentTabId = currentTabId,
                            playerUiState = playerUiState,
                            playlistUiState = playlistUiState,
                            playerViewModel = playerViewModel,
                            playlistViewModel = playlistViewModel,
                            onSortOptionChanged = onSortOptionChanged
                        )
                    }

                    // Box wrapper to allow floating SelectionCountPill overlay
                    Box(modifier = Modifier.fillMaxSize()) {
                        LibraryTabsPager(
                            screenState = screenState,
                            navController = navController,
                            playerViewModel = playerViewModel,
                            pagerState = pagerState,
                            tabTitles = tabTitles,
                            isCompactNavigation = isCompactNavigation,
                            bottomBarHeightDp = bottomBarHeightDp,
                            playerUiState = playerUiState,
                            playlistUiState = playlistUiState,
                            visiblePlaylists = visiblePlaylists,
                            allSongsLazyPagingItems = allSongsLazyPagingItems,
                            albumsLazyPagingItems = albumsLazyPagingItems,
                            artistsLazyPagingItems = artistsLazyPagingItems,
                            favoritePagingItems = favoritePagingItems,
                            isLibraryLoading = isLibraryLoading,
                            hasCurrentSong = hasCurrentSong,
                            isRefreshing = isRefreshing,
                            onRefresh = onRefresh,
                            stableOnMoreOptionsClick = stableOnMoreOptionsClick,
                            isSelectionMode = isSelectionMode,
                            selectedSongIds = selectedSongIds,
                            onSongLongPress = onSongLongPress,
                            onSongSelectionToggle = onSongSelectionToggle,
                            isAlbumSelectionMode = isAlbumSelectionMode,
                            selectedAlbumIds = selectedAlbumIds,
                            onAlbumLongPress = onAlbumLongPress,
                            onAlbumSelectionToggle = onAlbumSelectionToggle,
                            getAlbumSelectionIndex = getAlbumSelectionIndex,
                            isPlaylistSelectionMode = isPlaylistSelectionMode,
                            selectedPlaylistIds = selectedPlaylistIds,
                            onPlaylistLongPress = onPlaylistLongPress,
                            onPlaylistSelectionToggle = onPlaylistSelectionToggle
                        )

                        // Floating selection count pill overlay
                        val selectionCount = when {
                            currentTabId == LibraryTabId.PLAYLISTS && isPlaylistSelectionMode -> selectedPlaylists.size
                            currentTabId == LibraryTabId.ALBUMS && isAlbumSelectionMode -> selectedAlbums.size
                            else -> selectedSongs.size
                        }
                        SelectionCountPill(
                            selectedCount = selectionCount,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(1f)
                        )
                    }
                }
            }
            if (playerUiState.isGeneratingAiMetadata) {
                Surface( // Fondo semitransparente para el indicador
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator(modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.presentation_batch_d_generating_ai_metadata),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else if (
                isLibraryContentEmpty &&
                (
                        playerUiState.isSyncingLibrary ||
                                playerUiState.isLoadingInitialSongs ||
                                playerUiState.isLoadingLibraryCategories
                        )
            ) {
                // The full-screen overlay is reserved for first-launch / empty library
                // states. Once the user has content, in-place indicators (pull-to-refresh
                // spinner + LibraryInlineSyncIndicator) handle sync feedback so the
                // list stays visible.
                LibrarySyncOverlay(syncManager = syncManager)
            }
        }
        //Grad box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(170.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.2f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                            1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {

        }
    }
}
