package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Trace
import android.util.Log
import com.nobodybuilds.listens.data.model.FolderSource
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.worker.SyncManager
import com.nobodybuilds.listens.utils.StorageType
import com.nobodybuilds.listens.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Startup work of PlayerViewModel for the library: restores saved sort options, folder and
 * view settings, loads the library after sync, and mirrors the library, search and AI state
 * holders into [PlayerUiState].
 */
@SuppressLint("LogNotTimber")
internal class PlayerLibraryStateSync(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
    private val playbackStateHolder: PlaybackStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val folderNavigationStateHolder: FolderNavigationStateHolder,
    private val dailyMixStateHolder: DailyMixStateHolder,
    private val searchStateHolder: SearchStateHolder,
    private val aiStateHolder: AiStateHolder,
    private val favoritesActions: PlayerFavoritesActions,
    private val libraryTabs: PlayerLibraryTabsController
) {
    private data class SortOptionsSnapshot(
        val songSort: SortOption,
        val albumSort: SortOption,
        val artistSort: SortOption,
        val folderSort: SortOption,
        val favoriteSort: SortOption,
    )

    private data class AiUiSnapshot(
        val showAiPlaylistSheet: Boolean,
        val isGeneratingAiPlaylist: Boolean,
        val aiStatus: String?,
        val aiError: String?,
        val isGeneratingAiMetadata: Boolean,
    )

    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val favoriteSongIds: StateFlow<Set<String>> get() = favoritesActions.favoriteSongIds

    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isInitialDataLoaded = MutableStateFlow(false)
    private val _isInitialThemePreloadComplete = MutableStateFlow(false)
    private var lastBlockedDirectories: Set<String>? = null

    // Helper to resolve stored sort keys against the allowed group
    private fun resolveSortOption(
        optionKey: String?,
        allowed: Collection<SortOption>,
        fallback: SortOption
    ): SortOption {
        return SortOption.fromStorageKey(optionKey, allowed, fallback)
    }

    private data class FolderSourceState(
        val source: FolderSource,
        val rootPath: String,
        val isSdCardAvailable: Boolean
    )

    private fun resolveFolderSourceState(preferredSource: FolderSource): FolderSourceState {
        val storages = StorageUtils.getAvailableStorages(context)
        val internalPath = storages
            .firstOrNull { it.storageType == StorageType.INTERNAL }
            ?.path
            ?.path
            ?: android.os.Environment.getExternalStorageDirectory().path
        val sdPath = StorageUtils.getSdCardStorage(context)
            ?.path
            ?.path

        val effectiveSource = if (!ENABLE_FOLDERS_SOURCE_SWITCHING) {
            FolderSource.INTERNAL
        } else if (preferredSource == FolderSource.SD_CARD && sdPath == null) {
            FolderSource.INTERNAL
        } else {
            preferredSource
        }

        val resolvedRootPath = if (effectiveSource == FolderSource.SD_CARD) sdPath!! else internalPath
        return FolderSourceState(
            source = effectiveSource,
            rootPath = resolvedRootPath,
            isSdCardAvailable = sdPath != null
        )
    }

    /** Restores saved library settings and starts their observers. Called from PlayerViewModel's init. */
    fun startPreferenceObservers() {
        scope.launch {
            userPreferencesRepository.migrateTabOrder()
        }

        scope.launch {
            userPreferencesRepository.ensureLibrarySortDefaults()
        }

        scope.launch {
            val legacyFavoriteIds = userPreferencesRepository.favoriteSongIdsFlow.first()
            if (legacyFavoriteIds.isNotEmpty()) {
                val roomFavoriteIds = musicRepository.getFavoriteSongIdsOnce()
                if (roomFavoriteIds.isEmpty()) {
                    legacyFavoriteIds.forEach { songId ->
                        musicRepository.setFavoriteStatus(songId, true)
                    }
                }
                userPreferencesRepository.clearFavoriteSongIds()
            }
        }

        scope.launch {
            userPreferencesRepository.isFoldersPlaylistViewFlow.collect { isPlaylistView ->
                folderNavigationStateHolder.setFoldersPlaylistViewState(
                    isPlaylistView = isPlaylistView,
                    updateUiState = { mutation -> _playerUiState.update(mutation) }
                )
            }
        }

        scope.launch {
            userPreferencesRepository.foldersSourceFlow.collect { preferredSource ->
                val resolved = resolveFolderSourceState(preferredSource)
                if (resolved.source != preferredSource) {
                    userPreferencesRepository.setFoldersSource(resolved.source)
                }

                _playerUiState.update { currentState ->
                    val sourceChanged = currentState.folderSource != resolved.source ||
                            currentState.folderSourceRootPath != resolved.rootPath
                    currentState.copy(
                        folderSource = resolved.source,
                        folderSourceRootPath = resolved.rootPath,
                        isSdCardAvailable = resolved.isSdCardAvailable,
                        currentFolderPath = if (sourceChanged) null else currentState.currentFolderPath,
                        currentFolder = if (sourceChanged) null else currentState.currentFolder
                    )
                }
            }
        }

        scope.launch {
            combine(
                userPreferencesRepository.folderBackGestureNavigationFlow,
                userPreferencesRepository.isAlbumsListViewFlow,
            ) { gestureNav, albumsList ->
                Pair(gestureNav, albumsList)
            }.collect { (gestureNav, albumsList) ->
                _playerUiState.update {
                    it.copy(
                        folderBackGestureNavigationEnabled = gestureNav,
                        isAlbumsListView = albumsList,
                    )
                }
            }
        }

        scope.launch {
            userPreferencesRepository.blockedDirectoriesFlow
                .distinctUntilChanged()
                .collect { blocked ->
                    if (lastBlockedDirectories == null) {
                        lastBlockedDirectories = blocked
                        return@collect
                    }

                    if (blocked != lastBlockedDirectories) {
                        lastBlockedDirectories = blocked
                        onBlockedDirectoriesChanged()
                    }
                }
        }

        libraryTabs.startCurrentTabObserver()

        // Load initial sort options ONCE at startup.
        scope.launch {
            val initialSongSort = resolveSortOption(
                userPreferencesRepository.songsSortOptionFlow.first(),
                SortOption.SONGS,
                SortOption.SongTitleAZ
            )
            val initialAlbumSort = resolveSortOption(
                userPreferencesRepository.albumsSortOptionFlow.first(),
                SortOption.ALBUMS,
                SortOption.AlbumTitleAZ
            )
            val initialArtistSort = resolveSortOption(
                userPreferencesRepository.artistsSortOptionFlow.first(),
                SortOption.ARTISTS,
                SortOption.ArtistNameAZ
            )
            val initialFolderSort = resolveSortOption(
                userPreferencesRepository.foldersSortOptionFlow.first(),
                SortOption.FOLDERS,
                SortOption.FolderNameAZ
            )
            val initialLikedSort = resolveSortOption(
                userPreferencesRepository.likedSongsSortOptionFlow.first(),
                SortOption.LIKED,
                SortOption.LikedSongDateLiked
            )

            _playerUiState.update {
                it.copy(
                    currentSongSortOption = initialSongSort,
                    currentAlbumSortOption = initialAlbumSort,
                    currentArtistSortOption = initialArtistSort,
                    currentFolderSortOption = initialFolderSort,
                    currentFavoriteSortOption = initialLikedSort
                )
            }
            // Also update the dedicated flow for favorites to ensure consistency
            // _currentFavoriteSortOptionStateFlow.value = initialLikedSort // Delegated to LibraryStateHolder

            libraryStateHolder.sortSongs(initialSongSort, persist = false)
            libraryStateHolder.sortAlbums(initialAlbumSort, persist = false)
            libraryStateHolder.sortArtists(initialArtistSort, persist = false)
            libraryStateHolder.sortFolders(initialFolderSort, persist = false)
            libraryStateHolder.sortFavoriteSongs(initialLikedSort, persist = false)
        }

        scope.launch {
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (isPersistent) {
                // If persistent shuffle is on, read the last used shuffle state (On/Off)
                val savedShuffle = userPreferencesRepository.isShuffleOnFlow.first()
                // Update the UI state so the shuffle button reflects the saved setting immediately
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = savedShuffle) }
            }
        }
    }

    /** Loads the library once sync is idle, and again after each sync. Called from PlayerViewModel's init. */
    fun startInitialDataObservers() {
        scope.launch {
            isSyncingStateFlow.collect { isSyncing ->
                val oldSyncingLibraryState = _playerUiState.value.isSyncingLibrary
                _playerUiState.update { it.copy(isSyncingLibrary = isSyncing) }

                if (oldSyncingLibraryState && !isSyncing) {
                    Log.i("PlayerViewModel", "Sync completed. Calling resetAndLoadInitialData from isSyncingStateFlow observer.")
                    resetAndLoadInitialData("isSyncingStateFlow observer")
                }
            }
        }

        scope.launch {
            if (!isSyncingStateFlow.value && !_isInitialDataLoaded.value && libraryStateHolder.allSongs.value.isEmpty()) {
                Log.i("PlayerViewModel", "Initial check: Sync not active and initial data not loaded. Calling resetAndLoadInitialData.")
                resetAndLoadInitialData("Initial Check")
            }
        }
    }

    fun collectSearchState() {
        scope.launch {
            combine(
                searchStateHolder.searchResults,
                searchStateHolder.selectedSearchFilter,
                searchStateHolder.searchHistory,
            ) { results, filter, history ->
                Triple(results, filter, history)
            }.collect { (results, filter, history) ->
                _playerUiState.update {
                    it.copy(
                        searchResults = results,
                        selectedSearchFilter = filter,
                        searchHistory = history,
                    )
                }
            }
        }
    }

    fun collectAiState() {
        scope.launch {
            combine(
                aiStateHolder.showAiPlaylistSheet,
                aiStateHolder.isGeneratingAiPlaylist,
                aiStateHolder.aiStatus,
                aiStateHolder.aiError,
                aiStateHolder.isGeneratingMetadata,
            ) { show, generating, status, error, generatingMetadata ->
                AiUiSnapshot(
                    showAiPlaylistSheet = show,
                    isGeneratingAiPlaylist = generating,
                    aiStatus = status,
                    aiError = error,
                    isGeneratingAiMetadata = generatingMetadata
                )
            }.collect { snapshot ->
                _playerUiState.update {
                    it.copy(isGeneratingAiMetadata = snapshot.isGeneratingAiMetadata)
                }
            }
        }
    }

    fun collectLibraryState() {
        // Sync library folders and loading states
        scope.launch {
            combine(
                libraryStateHolder.musicFolders,
                libraryStateHolder.isLoadingLibrary,
                libraryStateHolder.isLoadingCategories,
            ) { folders, loadingLibrary, loadingCategories ->
                Triple(folders, loadingLibrary, loadingCategories)
            }.collect { (folders, loadingLibrary, loadingCategories) ->
                _playerUiState.update {
                    it.copy(
                        musicFolders = folders,
                        isLoadingInitialSongs = loadingLibrary,
                        isLoadingLibraryCategories = loadingCategories,
                    )
                }
            }
        }

        // Sync sort options and storage filter
        scope.launch {
            combine(
                libraryStateHolder.currentSongSortOption,
                libraryStateHolder.currentAlbumSortOption,
                libraryStateHolder.currentArtistSortOption,
                libraryStateHolder.currentFolderSortOption,
                libraryStateHolder.currentFavoriteSortOption,
            ) { songSort, albumSort, artistSort, folderSort, favoriteSort ->
                SortOptionsSnapshot(songSort, albumSort, artistSort, folderSort, favoriteSort)
            }.collect { snapshot ->
                _playerUiState.update {
                    it.copy(
                        currentSongSortOption = snapshot.songSort,
                        currentAlbumSortOption = snapshot.albumSort,
                        currentArtistSortOption = snapshot.artistSort,
                        currentFolderSortOption = snapshot.folderSort,
                        currentFavoriteSortOption = snapshot.favoriteSort,
                    )
                }
            }
        }
        scope.launch {
            libraryStateHolder.currentStorageFilter.collect { filter ->
                _playerUiState.update { it.copy(currentStorageFilter = filter) }
            }
        }
        scope.launch {
            userPreferencesRepository.hideLocalMediaFlow.collect { hide ->
                _playerUiState.update { it.copy(hideLocalMedia = hide) }
            }
        }
    }

    fun onMainActivityStart() {
        Trace.beginSection("PlayerViewModel.onMainActivityStart")
        try {
            preloadThemesAndInitialData()
            checkAndUpdateDailyMixIfNeeded()
        } finally {
            Trace.endSection()
        }
    }


    private fun checkAndUpdateDailyMixIfNeeded() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.checkAndUpdateIfNeeded(
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    private fun preloadThemesAndInitialData() {
        Trace.beginSection("PlayerViewModel.preloadThemesAndInitialData")
        try {
            scope.launch {
                _isInitialThemePreloadComplete.value = false
                if (isSyncingStateFlow.value && !_isInitialDataLoaded.value) {
                    // Sync is active - defer to sync completion handler
                } else if (!_isInitialDataLoaded.value && libraryStateHolder.allSongs.value.isEmpty()) {
                    resetAndLoadInitialData("preloadThemesAndInitialData")
                }
                _isInitialThemePreloadComplete.value = true
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun loadInitialLibraryDataParallel() {
        libraryStateHolder.loadSongsFromRepository()
        libraryStateHolder.loadAlbumsFromRepository()
        libraryStateHolder.loadArtistsFromRepository()
        libraryStateHolder.loadFoldersFromRepository()
    }

    private fun resetAndLoadInitialData(caller: String = "Unknown") {
        Trace.beginSection("PlayerViewModel.resetAndLoadInitialData")
        try {
            Log.d("PlayerViewModel", "resetAndLoadInitialData called by $caller")
            loadInitialLibraryDataParallel()
            updateDailyMix()
        } finally {
            Trace.endSection()
        }
    }

    private fun updateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.updateDailyMix(
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    private fun onBlockedDirectoriesChanged() {
        scope.launch {
            musicRepository.invalidateCachesDependentOnAllowedDirectories()
            resetAndLoadInitialData("Blocked directories changed")
        }
    }
}
