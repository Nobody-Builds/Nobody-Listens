@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import android.os.Trace
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.ui.theme.LocalNobodyListensDarkTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.resolveNavBarOccupiedHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.presentation.viewmodel.SongInfoBottomSheetViewModel
import com.nobodybuilds.listens.data.model.LibraryTabId
import com.nobodybuilds.listens.data.model.toLibraryTabIdOrNull
import com.nobodybuilds.listens.data.preferences.LibraryNavigationMode
import com.nobodybuilds.listens.presentation.viewmodel.LibraryViewModel
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.widget.Toast
import kotlinx.coroutines.flow.first

val ListExtraBottomGap = 30.dp
val PlayerSheetCollapsedCornerRadius = 32.dp
internal const val MAX_ALBUM_MULTI_SELECTION = 6
private const val PULL_REFRESH_MIN_VISIBLE_MS = 900L
private const val PULL_REFRESH_MAX_VISIBLE_MS = 1_500L
private const val INLINE_SYNC_MIN_VISIBLE_MS = 600L

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    songInfoBottomSheetViewModel: SongInfoBottomSheetViewModel = hiltViewModel()
) {
    // La recolección de estados de alto nivel se mantiene mínima.
    val context = LocalContext.current // Added context
    val haptic = LocalHapticFeedback.current
    val nobodyListensHaptics = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current
    val lastTabIndex by playerViewModel.lastLibraryTabIndexFlow.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle() // Reintroducir favoriteIds aquí
    val scope = rememberCoroutineScope() // Mantener si se usa para acciones de UI
    val syncManager = playerViewModel.syncManager
    val screenState = remember { LibraryScreenState() }
    var isRefreshing by remember { mutableStateOf(false) }
    // The pull-to-refresh spinner is reserved for user gestures. Automatic sync
    // and long-running refresh work move through the slim linear indicator under
    // LibraryActionRow so the list stays put.
    val isFetchingChanges by syncManager.isFetchingChanges
        .collectAsStateWithLifecycle(initialValue = false)
    val isSyncing by syncManager.isSyncing
        .collectAsStateWithLifecycle(initialValue = false)
    // NOTE: syncProgress is NOT collected here. It is collected inside LibrarySyncOverlay
    // to avoid triggering recomposition of the entire LibraryScreen on every progress tick.

    var showSongInfoBottomSheet by screenState.showSongInfoBottomSheet
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val tabTitles by playerViewModel.libraryTabsFlow.collectAsStateWithLifecycle()
    val currentTabId by playerViewModel.currentLibraryTabId.collectAsStateWithLifecycle()
    val libraryNavigationMode by playerViewModel.libraryNavigationMode.collectAsStateWithLifecycle()
    val isCompactNavigation = libraryNavigationMode == LibraryNavigationMode.COMPACT_PILL
    val tabCount = tabTitles.size.coerceAtLeast(1)
    val normalizedLastTabIndex = positiveMod(lastTabIndex, tabCount)
    val compactInitialPage = remember(tabCount, normalizedLastTabIndex) {
        infinitePagerInitialPage(tabCount, normalizedLastTabIndex)
    }
    val pagerState = if (isCompactNavigation) {
        rememberPagerState(initialPage = compactInitialPage) { Int.MAX_VALUE }
    } else {
        rememberPagerState(initialPage = normalizedLastTabIndex) { tabCount }
    }
    com.nobodybuilds.listens.presentation.haptics.HapticPagerEffect(pagerState, userDragOnly = true)
    val currentTabIndex by remember(pagerState, tabTitles, isCompactNavigation) {
        derivedStateOf {
            resolveTabIndex(
                page = pagerState.currentPage,
                tabCount = tabTitles.size,
                compactMode = isCompactNavigation
            )
        }
    }
    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()
    val isSendingToWatch by songInfoBottomSheetViewModel.isSendingToWatch.collectAsStateWithLifecycle()
    val activeWatchTransfer by songInfoBottomSheetViewModel.activeWatchTransfer.collectAsStateWithLifecycle()
    var showWatchTransferDialog by screenState.showWatchTransferDialog
    val canNavigateBackInFolders by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState -> uiState.currentFolder != null && uiState.folderBackGestureNavigationEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val hasActiveAiProviderApiKey by playerViewModel.hasActiveAiProviderApiKey.collectAsStateWithLifecycle()
    val isGeneratingAiPlaylist by playerViewModel.isGeneratingAiPlaylist.collectAsStateWithLifecycle()
    val aiError by playerViewModel.aiError.collectAsStateWithLifecycle()
    var showCreatePlaylistDialog by screenState.showCreatePlaylistDialog
    var showCreateAiPlaylistDialog by screenState.showCreateAiPlaylistDialog
    var aiGenerationRequestedFromDialog by screenState.aiGenerationRequestedFromDialog

    val m3uImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { playlistViewModel.importM3u(it) }
    }

    LaunchedEffect(activeWatchTransfer?.requestId) {
        if (activeWatchTransfer == null) {
            showWatchTransferDialog = false
        }
    }

    // Multi-selection state
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by screenState.showMultiSelectionSheet
    var selectedAlbums by screenState.selectedAlbums
    val selectedAlbumIds = remember(selectedAlbums) { selectedAlbums.map { it.id }.toSet() }
    val isAlbumSelectionMode = selectedAlbums.isNotEmpty()
    var showAlbumMultiSelectionSheet by screenState.showAlbumMultiSelectionSheet

    // Multi-selection callbacks
    val onSongLongPress: (Song) -> Unit = remember(multiSelectionState, haptic) {
        { song -> 
            // The row itself plays the long-press haptic (EnhancedSongListItem).
            multiSelectionState.toggleSelection(song) 
        }
    }

    val onSongSelectionToggle: (Song) -> Unit = remember(multiSelectionState) {
        { song -> multiSelectionState.toggleSelection(song) }
    }

    val toggleAlbumSelection: (Album) -> Unit = remember(selectedAlbums, playerViewModel, context) {
        { album ->
            val existingIndex = selectedAlbums.indexOfFirst { it.id == album.id }
            if (existingIndex >= 0) {
                selectedAlbums = selectedAlbums.toMutableList().also { it.removeAt(existingIndex) }
            } else if (selectedAlbums.size >= MAX_ALBUM_MULTI_SELECTION) {
                playerViewModel.sendToast(context.getString(R.string.presentation_batch_d_max_albums_selection, MAX_ALBUM_MULTI_SELECTION))
            } else {
                selectedAlbums = selectedAlbums + album
            }
        }
    }

    val onAlbumLongPress: (Album) -> Unit = remember(toggleAlbumSelection, haptic) {
        { album -> 
            nobodyListensHaptics.longPress()
            toggleAlbumSelection(album) 
        }
    }

    val onAlbumSelectionToggle: (Album) -> Unit = remember(toggleAlbumSelection) {
        { album -> toggleAlbumSelection(album) }
    }

    val getAlbumSelectionIndex: (Long) -> Int? = remember(selectedAlbums) {
        { albumId ->
            val index = selectedAlbums.indexOfFirst { it.id == albumId }
            if (index >= 0) index + 1 else null
        }
    }

    // Playlist multi-selection state and callbacks
    val playlistMultiSelectionState = playerViewModel.playlistSelectionStateHolder
    val selectedPlaylists by playlistMultiSelectionState.selectedPlaylists.collectAsStateWithLifecycle()
    val selectedPlaylistIds by playlistMultiSelectionState.selectedPlaylistIds.collectAsStateWithLifecycle()
    val isPlaylistSelectionMode by playlistMultiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    var showPlaylistMultiSelectionSheet by screenState.showPlaylistMultiSelectionSheet
    var showMergePlaylistDialog by screenState.showMergePlaylistDialog
    var pendingMergePlaylistIds by screenState.pendingMergePlaylistIds

    val onPlaylistLongPress: (com.nobodybuilds.listens.data.model.Playlist) -> Unit = remember(playlistMultiSelectionState, haptic) {
        { playlist ->
            nobodyListensHaptics.longPress()
            // Only toggle selection, don't show sheet immediately (similar to songs multi-selection)
            playlistMultiSelectionState.toggleSelection(playlist)
            android.util.Log.d("PlaylistMultiSelect", "Toggled: ${playlist.name}, total selected: ${playlistMultiSelectionState.selectedPlaylists.value.size}")
        }
    }

    val onPlaylistSelectionToggle: (com.nobodybuilds.listens.data.model.Playlist) -> Unit = remember(playlistMultiSelectionState) {
        { playlist -> playlistMultiSelectionState.toggleSelection(playlist) }
    }

    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    // Pull-to-refresh uses incremental sync for speed. The spinner gives manual
    // refreshes a short tactile confirmation, then longer work hands off to the
    // inline sync indicator.
    var isMinDelayActive by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableStateOf(0) }

    val onRefresh: () -> Unit = remember(scope, syncManager) {
        {
            nobodyListensHaptics.refreshTrigger()
            val currentRefreshGeneration = refreshGeneration + 1
            refreshGeneration = currentRefreshGeneration
            isMinDelayActive = true
            isRefreshing = true
            syncManager.incrementalSync()
            scope.launch {
                kotlinx.coroutines.delay(PULL_REFRESH_MIN_VISIBLE_MS)
                if (currentRefreshGeneration != refreshGeneration) return@launch
                isMinDelayActive = false
                // If the changes phase already finished while the tactile minimum was
                // still active, hide the spinner now.
                val stillFetching = syncManager.isFetchingChanges.first()
                if (!stillFetching) {
                    isRefreshing = false
                    return@launch
                }

                val remainingVisibleMs =
                    (PULL_REFRESH_MAX_VISIBLE_MS - PULL_REFRESH_MIN_VISIBLE_MS)
                        .coerceAtLeast(0L)
                if (remainingVisibleMs > 0L) {
                    kotlinx.coroutines.delay(remainingVisibleMs)
                }
                if (currentRefreshGeneration != refreshGeneration) return@launch
                // Long-running refresh work continues through the inline indicator.
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(isFetchingChanges) {
        if (!isFetchingChanges && !isMinDelayActive) {
            isRefreshing = false
        }
    }

    // Minimum-visible gate for the inline sync indicator. It covers automatic
    // startup syncs and manual refreshes once the pull spinner has handed off.
    // Fast no-op phases finish in tens of milliseconds, so the small linear bar is
    // held briefly to avoid single-frame flicker.
    var inlineSyncVisible by remember { mutableStateOf(false) }
    var inlineSyncShownAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(isSyncing, isRefreshing) {
        if (isSyncing && !isRefreshing) {
            if (!inlineSyncVisible) {
                inlineSyncShownAt = System.currentTimeMillis()
                inlineSyncVisible = true
            }
        } else if (isRefreshing) {
            inlineSyncVisible = false
            inlineSyncShownAt = null
        } else if (inlineSyncVisible) {
            val shownAt = inlineSyncShownAt
            val elapsed = if (shownAt != null) {
                System.currentTimeMillis() - shownAt
            } else {
                INLINE_SYNC_MIN_VISIBLE_MS
            }
            val remaining = INLINE_SYNC_MIN_VISIBLE_MS - elapsed
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
            }
            // If sync flipped back to visible during the delay this LaunchedEffect
            // is cancelled and re-runs, so reaching this line means we should hide.
            inlineSyncVisible = false
            inlineSyncShownAt = null
        }
    }

    // P1-1: derivedStateOf ensures BackHandler only recomposes when the boolean RESULT changes,
    // not every time any individual selection state emits.
    val hasSelectionInCurrentTab by remember {
        derivedStateOf {
            when (currentTabId) {
                LibraryTabId.PLAYLISTS -> isPlaylistSelectionMode
                LibraryTabId.ALBUMS -> isAlbumSelectionMode
                LibraryTabId.SONGS,
                LibraryTabId.LIKED,
                LibraryTabId.FOLDERS -> isSelectionMode
                LibraryTabId.ARTISTS -> false
            }
        }
    }
    val canHandleFolderBack by remember {
        derivedStateOf {
            currentTabId == LibraryTabId.FOLDERS &&
                    canNavigateBackInFolders &&
                    !isSortSheetVisible
        }
    }

    BackHandler(enabled = hasSelectionInCurrentTab || canHandleFolderBack) {
        when {
            hasSelectionInCurrentTab -> {
                when (currentTabId) {
                    LibraryTabId.PLAYLISTS -> {
                        playlistMultiSelectionState.clearSelection()
                        showPlaylistMultiSelectionSheet = false
                        showMergePlaylistDialog = false
                        pendingMergePlaylistIds = emptyList()
                    }

                    LibraryTabId.ALBUMS -> {
                        selectedAlbums = emptyList()
                        showAlbumMultiSelectionSheet = false
                    }

                    LibraryTabId.SONGS,
                    LibraryTabId.LIKED,
                    LibraryTabId.FOLDERS -> {
                        multiSelectionState.clearSelection()
                        showMultiSelectionSheet = false
                    }

                    LibraryTabId.ARTISTS -> Unit
                }
            }

            canHandleFolderBack -> {
                playerViewModel.navigateBackFolder()
            }
        }
    }

    // Feedback for Playlist Creation
    LaunchedEffect(Unit) {
        playlistViewModel.playlistCreationEvent.collect { success ->
            if (success) {
                showCreatePlaylistDialog = false
                Toast.makeText(context, context.getString(R.string.toast_playlist_created), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(
        showCreateAiPlaylistDialog,
        aiGenerationRequestedFromDialog,
        isGeneratingAiPlaylist,
        aiError
    ) {
        if (!showCreateAiPlaylistDialog || !aiGenerationRequestedFromDialog || isGeneratingAiPlaylist) {
            return@LaunchedEffect
        }

        if (aiError == null) {
            showCreateAiPlaylistDialog = false
            playerViewModel.clearAiPlaylistError()
        }
        aiGenerationRequestedFromDialog = false
    }

    LaunchedEffect(hasActiveAiProviderApiKey, showCreateAiPlaylistDialog) {
        if (!hasActiveAiProviderApiKey && showCreateAiPlaylistDialog) {
            showCreateAiPlaylistDialog = false
            aiGenerationRequestedFromDialog = false
            playerViewModel.clearAiPlaylistError()
        }
    }
    // La lógica de carga diferida (lazy loading) se mantiene.
    LaunchedEffect(Unit) {
        Trace.beginSection("LibraryScreen.InitialTabLoad")
        playerViewModel.onLibraryTabSelected(normalizedLastTabIndex)
        Trace.endSection()
    }

    LaunchedEffect(currentTabIndex) {
        Trace.beginSection("LibraryScreen.PageChangeTabLoad")
        playerViewModel.onLibraryTabSelected(currentTabIndex)
        Trace.endSection()

        // Clear selection when switching tabs
        multiSelectionState.clearSelection()
        playlistMultiSelectionState.clearSelection()
        selectedAlbums = emptyList()
        showMultiSelectionSheet = false
        showPlaylistMultiSelectionSheet = false
        showAlbumMultiSelectionSheet = false
    }

    val fabState by remember { derivedStateOf { currentTabIndex } } // UI sin cambios
    val transition = updateTransition(
        targetState = fabState,
        label = "Action Button Icon Transition"
    ) // UI sin cambios

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

    val dm = LocalNobodyListensDarkTheme.current

    val iconRotation by transition.animateFloat(
        label = "Action Button Icon Rotation",
        transitionSpec = {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        }
    ) { page ->
        when (tabTitles.getOrNull(page)?.toLibraryTabIdOrNull()) {
            LibraryTabId.PLAYLISTS -> 0f // Playlist icon (PlaylistAdd) usually doesn't rotate
            else -> 360f // Shuffle icon animates
        }
    }

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }

    val currentTab = tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull() ?: currentTabId
    val currentTabTitle = currentTab.displayTitle()

    // Daylight: flat canvas header, no tinted band or gradient.
    val headerContainerColor = MaterialTheme.colorScheme.surface
    val tabRowState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(currentTabIndex, isCompactNavigation) {
        if (!isCompactNavigation && currentTabIndex >= 0) {
            runCatching { tabRowState.animateScrollToItem((currentTabIndex - 1).coerceAtLeast(0)) }
        }
    }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LibraryTopBar(
                screenState = screenState,
                navController = navController,
                headerContainerColor = headerContainerColor,
                isCompactNavigation = isCompactNavigation,
                currentTab = currentTab,
                currentTabTitle = currentTabTitle,
                currentTabIndex = currentTabIndex,
                tabTitles = tabTitles,
                pagerState = pagerState,
                tabRowState = tabRowState,
                scope = scope,
                isSendingToWatch = isSendingToWatch,
                activeWatchTransfer = activeWatchTransfer
            )
        }
    ) { innerScaffoldPadding ->
        LibraryScreenContent(
            innerScaffoldPadding = innerScaffoldPadding,
            screenState = screenState,
            navController = navController,
            playerViewModel = playerViewModel,
            playlistViewModel = playlistViewModel,
            libraryViewModel = libraryViewModel,
            headerContainerColor = headerContainerColor,
            pagerState = pagerState,
            tabTitles = tabTitles,
            currentTabId = currentTabId,
            currentTabIndex = currentTabIndex,
            isCompactNavigation = isCompactNavigation,
            bottomBarHeightDp = bottomBarHeightDp,
            iconRotation = iconRotation,
            isSortSheetVisible = isSortSheetVisible,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            inlineSyncVisible = inlineSyncVisible,
            scope = scope,
            m3uImportLauncher = m3uImportLauncher,
            stableOnMoreOptionsClick = stableOnMoreOptionsClick,
            selectedSongs = selectedSongs,
            selectedSongIds = selectedSongIds,
            isSelectionMode = isSelectionMode,
            onSongLongPress = onSongLongPress,
            onSongSelectionToggle = onSongSelectionToggle,
            selectedAlbumIds = selectedAlbumIds,
            isAlbumSelectionMode = isAlbumSelectionMode,
            onAlbumLongPress = onAlbumLongPress,
            onAlbumSelectionToggle = onAlbumSelectionToggle,
            getAlbumSelectionIndex = getAlbumSelectionIndex,
            selectedPlaylists = selectedPlaylists,
            selectedPlaylistIds = selectedPlaylistIds,
            isPlaylistSelectionMode = isPlaylistSelectionMode,
            onPlaylistLongPress = onPlaylistLongPress,
            onPlaylistSelectionToggle = onPlaylistSelectionToggle
        )
    }

    LibraryScreenDialogs(
        screenState = screenState,
        navController = navController,
        playerViewModel = playerViewModel,
        playlistViewModel = playlistViewModel,
        songInfoBottomSheetViewModel = songInfoBottomSheetViewModel,
        hasActiveAiProviderApiKey = hasActiveAiProviderApiKey,
        isGeneratingAiPlaylist = isGeneratingAiPlaylist,
        aiError = aiError,
        activeWatchTransfer = activeWatchTransfer,
        selectedSongForInfo = selectedSongForInfo,
        favoriteIds = favoriteIds,
        bottomBarHeightDp = bottomBarHeightDp,
        selectedSongs = selectedSongs,
        selectedPlaylists = selectedPlaylists,
        selectedPlaylistIds = selectedPlaylistIds,
        tabTitles = tabTitles,
        currentTabIndex = currentTabIndex,
        pagerState = pagerState,
        scope = scope,
        isCompactNavigation = isCompactNavigation
    )
}
