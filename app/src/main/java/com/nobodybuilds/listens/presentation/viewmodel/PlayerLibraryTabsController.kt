package com.nobodybuilds.listens.presentation.viewmodel

import android.os.Trace
import com.nobodybuilds.listens.data.model.FolderSource
import com.nobodybuilds.listens.data.model.LibraryTabId
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.model.toLibraryTabIdOrNull
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Library screen controls of PlayerViewModel: tabs and their order, the sort sheet, folder
 * navigation, list/grid views and the storage filter.
 */
internal class PlayerLibraryTabsController(
    private val shared: PlayerSharedState,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val libraryStateHolder: LibraryStateHolder,
    private val libraryTabsStateHolder: LibraryTabsStateHolder,
    private val folderNavigationStateHolder: FolderNavigationStateHolder,
    private val preferences: PlayerPreferenceFlows,
    private val songResolver: PlayerSongResolver
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val libraryTabsFlow: StateFlow<List<String>> get() = preferences.libraryTabsFlow
    private val isGenreGridView: StateFlow<Boolean> get() = preferences.isGenreGridView
    private val lastLibraryTabIndexFlow: StateFlow<Int> get() = preferences.lastLibraryTabIndexFlow

    private val _loadedTabs = MutableStateFlow(emptySet<String>())

    private val _currentLibraryTabId = MutableStateFlow(LibraryTabId.SONGS)
    val currentLibraryTabId: StateFlow<LibraryTabId> = _currentLibraryTabId.asStateFlow()

    private val _isSortingSheetVisible = MutableStateFlow(false)
    val isSortingSheetVisible: StateFlow<Boolean> = _isSortingSheetVisible.asStateFlow()

    val availableSortOptions: StateFlow<List<SortOption>> =
        currentLibraryTabId.map { tabId ->
            Trace.beginSection("PlayerViewModel.availableSortOptionsMapping")
            try {
                when (tabId) {
                    LibraryTabId.SONGS -> SortOption.SONGS
                    LibraryTabId.ALBUMS -> SortOption.ALBUMS
                    LibraryTabId.ARTISTS -> SortOption.ARTISTS
                    LibraryTabId.PLAYLISTS -> SortOption.PLAYLISTS
                    LibraryTabId.FOLDERS -> SortOption.FOLDERS
                    LibraryTabId.LIKED -> SortOption.LIKED
                }
            } finally {
                Trace.endSection()
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SortOption.SONGS
        )

    fun setHideLocalMedia(hide: Boolean) {
        scope.launch {
            userPreferencesRepository.setHideLocalMedia(hide)
        }
    }

    fun toggleStorageFilter() {
        val current = _playerUiState.value.currentStorageFilter
        val next = com.nobodybuilds.listens.data.model.LibrarySourceFilter.next(current)
        libraryStateHolder.setStorageFilter(next)
    }

    fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        scope.launch {
            userPreferencesRepository.setFoldersPlaylistView(isPlaylistView)
            folderNavigationStateHolder.setFoldersPlaylistViewState(
                isPlaylistView = isPlaylistView,
                updateUiState = { mutation -> _playerUiState.update(mutation) }
            )
        }
    }

    fun setFoldersSource(source: FolderSource) {
        if (!ENABLE_FOLDERS_SOURCE_SWITCHING) return
        scope.launch {
            userPreferencesRepository.setFoldersSource(source)
        }
    }

    fun navigateToFolder(path: String) {
        folderNavigationStateHolder.navigateToFolder(
            path = path,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onFolderChanged = { folderPath ->
                folderNavigationStateHolder.hydrateCurrentFolderSongsIfNeeded(
                    scope = scope,
                    folderPath = folderPath,
                    getUiState = { _playerUiState.value },
                    updateUiState = { mutation -> _playerUiState.update(mutation) },
                    requiresHydration = { song -> song.requiresHydration() },
                    hydrateSongs = { songs -> songResolver.hydrateSongsIfNeeded(songs) }
                )
            }
        )
    }

    fun navigateBackFolder() {
        folderNavigationStateHolder.navigateBackFolder(
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onFolderChanged = { folderPath ->
                folderNavigationStateHolder.hydrateCurrentFolderSongsIfNeeded(
                    scope = scope,
                    folderPath = folderPath,
                    getUiState = { _playerUiState.value },
                    updateUiState = { mutation -> _playerUiState.update(mutation) },
                    requiresHydration = { song -> song.requiresHydration() },
                    hydrateSongs = { songs -> songResolver.hydrateSongsIfNeeded(songs) }
                )
            }
        )
    }

    fun setAlbumsListView(isList: Boolean) {
        scope.launch {
            userPreferencesRepository.setAlbumsListView(isList)
        }
    }

    fun saveLastLibraryTabIndex(tabIndex: Int) {
        scope.launch {
            userPreferencesRepository.saveLastLibraryTabIndex(tabIndex)
        }
    }

    fun showSortingSheet() {
        libraryTabsStateHolder.showSortingSheet(_isSortingSheetVisible)
    }

    fun hideSortingSheet() {
        libraryTabsStateHolder.hideSortingSheet(_isSortingSheetVisible)
    }

    fun onLibraryTabSelected(tabIndex: Int) {
        libraryTabsStateHolder.onLibraryTabSelected(
            tabIndex = tabIndex,
            libraryTabs = libraryTabsFlow.value,
            loadedTabs = _loadedTabs,
            currentLibraryTabId = _currentLibraryTabId,
            saveLastTabIndex = { index -> userPreferencesRepository.saveLastLibraryTabIndex(index) },
            scope = scope,
            loadSongs = { libraryStateHolder.loadSongsIfNeeded() },
            loadAlbums = { libraryStateHolder.loadAlbumsIfNeeded() },
            loadArtists = { libraryStateHolder.loadArtistsIfNeeded() },
            loadFolders = { libraryStateHolder.loadFoldersFromRepository() }
        )
    }

    fun saveLibraryTabsOrder(tabs: List<String>) {
        scope.launch {
            val orderJson = Json.encodeToString(tabs)
            userPreferencesRepository.saveLibraryTabsOrder(orderJson)
        }
    }

    fun resetLibraryTabsOrder() {
        scope.launch {
            userPreferencesRepository.resetLibraryTabsOrder()
        }
    }

    fun toggleGenreViewMode() {
        scope.launch {
            userPreferencesRepository.setGenreGridView(!isGenreGridView.value)
        }
    }

    fun addCustomGenre(genre: String, iconResId: Int? = null) {
        scope.launch {
            userPreferencesRepository.addCustomGenre(genre, iconResId)
        }
    }

    /** Follows the saved tab order and index to the selected tab. Called from PlayerViewModel's init. */
    fun startCurrentTabObserver() {
        scope.launch {
            combine(libraryTabsFlow, lastLibraryTabIndexFlow) { tabs, index ->
                tabs.getOrNull(index)?.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
            }.collect { tabId ->
                _currentLibraryTabId.value = tabId
            }
        }
    }
}
