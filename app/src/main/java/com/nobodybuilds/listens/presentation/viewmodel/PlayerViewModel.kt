package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Trace
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaRouter
import androidx.paging.PagingData
import com.nobodybuilds.listens.data.ai.SongMetadata
import com.nobodybuilds.listens.data.database.AlbumArtThemeDao
import com.nobodybuilds.listens.data.media.CoverArtUpdate
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.FolderSource
import com.nobodybuilds.listens.data.model.Genre
import com.nobodybuilds.listens.data.model.LibraryTabId
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.model.SearchFilterType
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.preferences.AiPreferencesRepository
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.data.preferences.AlbumArtQuality
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.LyricsSearchResult
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.data.worker.SyncManager
import com.nobodybuilds.listens.utils.AppShortcutManager
import com.nobodybuilds.listens.utils.ValidatedLyricsImport
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The player and library ViewModel. Its work lives in collaborators in this package
 * (PlayerQueuePlayback, PlayerTransportController, PlayerControllerListener, …) that share
 * [PlayerSharedState]; this class wires them up and keeps the public API the UI uses.
 */
@UnstableApi
@SuppressLint("LogNotTimber")
@OptIn(coil.annotation.ExperimentalCoilApi::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    val syncManager: SyncManager, // Inyectar SyncManager

    private val dualPlayerEngine: DualPlayerEngine,
    private val youTubeMusicRadio: com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRadio,
    private val jioSaavnRadio: com.nobodybuilds.listens.data.jiosaavn.JioSaavnRadio,
    private val appShortcutManager: AppShortcutManager,
    private val telegramCacheManagerProvider: Lazy<com.nobodybuilds.listens.data.telegram.TelegramCacheManager>,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val dailyMixStateHolder: DailyMixStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val castStateHolder: CastStateHolder,
    private val castRouteStateHolder: CastRouteStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val queueUndoStateHolder: QueueUndoStateHolder,
    private val playlistDismissUndoStateHolder: PlaylistDismissUndoStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val sleepTimerStateHolder: SleepTimerStateHolder,
    private val searchStateHolder: SearchStateHolder,
    private val aiStateHolder: AiStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val folderNavigationStateHolder: FolderNavigationStateHolder,
    private val libraryTabsStateHolder: LibraryTabsStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val songRemovalStateHolder: SongRemovalStateHolder,
    private val externalMediaStateHolder: ExternalMediaStateHolder,
    val themeStateHolder: ThemeStateHolder,
    val multiSelectionStateHolder: MultiSelectionStateHolder,
    val playlistSelectionStateHolder: PlaylistSelectionStateHolder,
    private val sessionToken: SessionToken,
    private val mediaControllerFactory: com.nobodybuilds.listens.data.media.MediaControllerFactory
) : ViewModel() {

    // --- Collaborators (each one only uses the ones declared before it) ---
    private val shared = PlayerSharedState(viewModelScope)
    private val preferences =
        PlayerPreferenceFlows(viewModelScope, userPreferencesRepository, themePreferencesRepository, aiPreferencesRepository)
    private val libraryFlows = PlayerLibraryFlows(viewModelScope, musicRepository, libraryStateHolder)
    private val currentSongSync = CurrentSongStateSync(shared, context, musicRepository, userPreferencesRepository,
        dualPlayerEngine, telegramCacheManagerProvider, playbackStateHolder, lyricsStateHolder, themeStateHolder,
        preferences.lyricsSourcePreference)
    private val songResolver = PlayerSongResolver(shared, musicRepository, libraryStateHolder) { mediaMapper }
    private val sheetNavigator = PlayerSheetNavigator(shared, context, musicRepository, playbackStateHolder)
    private val audioMetadataTracker = PlaybackAudioMetadataTracker(context, dualPlayerEngine, viewModelScope)
    private val playbackLauncher = PlayerPlaybackLauncher(shared, context, castStateHolder, castTransferStateHolder,
        playbackStateHolder, dualPlayerEngine, appShortcutManager, themeStateHolder, externalMediaStateHolder,
        currentSongSync, sheetNavigator)
    private val queuePlayback = PlayerQueuePlayback(shared, context, musicRepository, userPreferencesRepository,
        queueStateHolder, playbackStateHolder, castStateHolder, castTransferStateHolder, connectivityStateHolder,
        listeningStatsTracker, songResolver, playbackLauncher, sheetNavigator, currentSongSync)
    private val shuffleActions =
        PlayerShuffleActions(shared, context, musicRepository, libraryStateHolder, syncManager, songResolver, queuePlayback)
    private val transport = PlayerTransportController(shared, musicRepository, userPreferencesRepository, castStateHolder,
        castTransferStateHolder, playbackStateHolder, songResolver, playbackLauncher, queuePlayback)
    private val controllerListener = PlayerControllerListener(shared, context, musicRepository, userPreferencesRepository,
        dualPlayerEngine, youTubeMusicRadio, jioSaavnRadio, playbackStateHolder, lyricsStateHolder, themeStateHolder, libraryStateHolder,
        connectivityStateHolder, castStateHolder, sleepTimerStateHolder, currentSongSync, songResolver,
        audioMetadataTracker, playbackLauncher, transport)
    private val controllerConnection = PlayerMediaControllerConnection(shared, context, sessionToken,
        mediaControllerFactory, playbackStateHolder, controllerListener, transport)
    private val favoritesActions =
        PlayerFavoritesActions(shared, context, musicRepository, playbackStateHolder, multiSelectionStateHolder)
    private val selectionActions =
        PlayerSelectionActions(shared, context, musicRepository, multiSelectionStateHolder, queuePlayback, transport)
    private val songDeletion = PlayerSongDeletion(shared, context, playbackStateHolder, metadataEditStateHolder,
        songRemovalStateHolder, multiSelectionStateHolder, favoritesActions)
    private val metadataEditor = PlayerMetadataEditor(shared, context, musicRepository, albumArtThemeDao,
        metadataEditStateHolder, libraryStateHolder, playbackStateHolder, themeStateHolder, currentSongSync
    ) { imageCacheManager }
    private val libraryTabs = PlayerLibraryTabsController(shared, userPreferencesRepository, libraryStateHolder,
        libraryTabsStateHolder, folderNavigationStateHolder, preferences, songResolver)
    private val librarySync = PlayerLibraryStateSync(shared, context, musicRepository, userPreferencesRepository,
        syncManager, playbackStateHolder, libraryStateHolder, folderNavigationStateHolder, dailyMixStateHolder,
        searchStateHolder, aiStateHolder, favoritesActions, libraryTabs)
    private val castCoordinator = PlayerCastCoordinator(shared, context, castStateHolder, castRouteStateHolder,
        castTransferStateHolder, playbackStateHolder, libraryStateHolder, themeStateHolder, currentSongSync)
    private val undoActions = PlayerUndoActions(shared, playbackStateHolder, castStateHolder, castRouteStateHolder,
        castTransferStateHolder, queueUndoStateHolder, playlistDismissUndoStateHolder, queuePlayback)

    val playerUiState: StateFlow<PlayerUiState> = shared.playerUiState.asStateFlow()

    // Narrow queue flow so the MiniPlayer branch doesn't recompose on queue changes (see PlayerSharedState).
    val queueFlow: StateFlow<ImmutableList<Song>> = shared.queueFlow

    private val _showNoInternetDialog = shared.showNoInternetDialog
    val showNoInternetDialog: SharedFlow<Unit> = _showNoInternetDialog.asSharedFlow()

    val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState
    val albumArtPaletteStyle: StateFlow<AlbumArtPaletteStyle> = preferences.albumArtPaletteStyle
    /**
     * High-frequency playback position should not force global UI recomposition.
     * Keep a dedicated position flow for real-time UI elements (seek bars, lyrics timing).
     */
    val currentPlaybackPosition: StateFlow<Long> = playbackStateHolder.currentPosition
    val playbackHistory = listeningStatsTracker.playbackHistory

    /** Paginated songs for LibraryScreen (Paging 3, so large libraries stay cheap). */
    val paginatedSongs: Flow<PagingData<Song>> = libraryFlows.paginatedSongs
    val playlistPickerSongs: Flow<PagingData<Song>> = libraryFlows.playlistPickerSongs

    private val offlinePlaybackObserverJob = viewModelScope.launch {
        connectivityStateHolder.offlinePlaybackBlocked.collect {
            Timber.w("Received offline blocked event. Showing dialog.")
            _showNoInternetDialog.emit(Unit)
        }
    }

    val currentSongArtists: StateFlow<List<Artist>> = currentSongSync.currentSongArtists

    val sheetState: StateFlow<PlayerSheetState> = shared.sheetState.asStateFlow()
    val bottomBarHeight: StateFlow<Int> = sheetNavigator.bottomBarHeight
    val predictiveBackCollapseFraction: StateFlow<Float> = sheetNavigator.predictiveBackCollapseFraction
    val predictiveBackSwipeEdge: StateFlow<Int?> = sheetNavigator.predictiveBackSwipeEdge
    val isQueueSheetVisible: StateFlow<Boolean> = sheetNavigator.isQueueSheetVisible
    val isCastSheetVisible: StateFlow<Boolean> = sheetNavigator.isCastSheetVisible
    val playerContentExpansionFraction = sheetNavigator.playerContentExpansionFraction

    // AI Ecosystem: States delegated to AiStateHolder for centralized management
    val showAiPlaylistSheet: StateFlow<Boolean> = aiStateHolder.showAiPlaylistSheet
    val isGeneratingAiPlaylist: StateFlow<Boolean> = aiStateHolder.isGeneratingAiPlaylist
    val aiSuccess: StateFlow<Boolean> = aiStateHolder.aiSuccess
    val aiStatus: StateFlow<String?> = aiStateHolder.aiStatus
    val aiError: StateFlow<String?> = aiStateHolder.aiError

    // AI Metadata Generation States
    val isGeneratingAiMetadata: StateFlow<Boolean> = aiStateHolder.isGeneratingMetadata
    val aiMetadataSuccess: StateFlow<Boolean> = aiStateHolder.aiMetadataSuccess

    val selectedSongForInfo: StateFlow<Song?> = shared.selectedSongForInfo.asStateFlow()

    // Theme & Colors - delegated to ThemeStateHolder
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.currentAlbumArtColorSchemePair
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.activePlayerColorSchemePair
    val currentThemedAlbumArtUri: StateFlow<String?> = themeStateHolder.currentAlbumArtUri

    // Settings, read by PlayerPreferenceFlows
    val playerThemePreference: StateFlow<String> = preferences.playerThemePreference
    val navBarCornerRadius: StateFlow<Int> = preferences.navBarCornerRadius
    val navBarStyle: StateFlow<String> = preferences.navBarStyle
    val navBarCompactMode: StateFlow<Boolean> = preferences.navBarCompactMode
    val libraryNavigationMode: StateFlow<String> = preferences.libraryNavigationMode
    val carouselStyle: StateFlow<String> = preferences.carouselStyle
    val hasActiveAiProviderApiKey: StateFlow<Boolean> = preferences.hasActiveAiProviderApiKey
    val hasGeminiApiKey: StateFlow<Boolean> = preferences.hasGeminiApiKey
    val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks> = preferences.fullPlayerLoadingTweaks
    val showPlayerFileInfo: StateFlow<Boolean> = preferences.showPlayerFileInfo
    /** Whether tapping the background of the player sheet toggles its state. */
    val tapBackgroundClosesPlayer: StateFlow<Boolean> = preferences.tapBackgroundClosesPlayer
    val hapticsEnabled: StateFlow<Boolean> = preferences.hapticsEnabled

    // Lyrics sync offset - now managed by LyricsStateHolder
    val currentSongLyricsSyncOffset: StateFlow<Int> = lyricsStateHolder.currentSongSyncOffset

    // Lyrics source preference (API_FIRST, EMBEDDED_FIRST, LOCAL_FIRST)
    val lyricsSourcePreference: StateFlow<LyricsSourcePreference> = preferences.lyricsSourcePreference
    val immersiveLyricsEnabled: StateFlow<Boolean> = preferences.immersiveLyricsEnabled
    val immersiveLyricsTimeout: StateFlow<Long> = preferences.immersiveLyricsTimeout

    private val _isImmersiveTemporarilyDisabled = MutableStateFlow(false)
    val isImmersiveTemporarilyDisabled: StateFlow<Boolean> = _isImmersiveTemporarilyDisabled.asStateFlow()

    fun setImmersiveTemporarilyDisabled(disabled: Boolean) { _isImmersiveTemporarilyDisabled.value = disabled }

    val albumArtQuality: StateFlow<AlbumArtQuality> = preferences.albumArtQuality

    fun setLyricsSyncOffset(songId: String, offsetMs: Int) { lyricsStateHolder.setSyncOffset(songId, offsetMs) }

    val useSmoothCorners: StateFlow<Boolean> = preferences.useSmoothCorners

    val isEndOfTrackTimerActive: StateFlow<Boolean> = sleepTimerStateHolder.isEndOfTrackTimerActive
    val activeTimerValueDisplay: StateFlow<String?> = sleepTimerStateHolder.activeTimerValueDisplay
    val activeTimerDurationMinutes: StateFlow<Int?> = sleepTimerStateHolder.activeTimerDurationMinutes
    val playCount: StateFlow<Float> = sleepTimerStateHolder.playCount
    /** Wall-clock end time of a duration sleep timer, or null. */
    val sleepTimerEndTimeMillis: StateFlow<Long?> = sleepTimerStateHolder.sleepTimerEndTimeMillis

    // Lyrics search UI state - managed by LyricsStateHolder
    val lyricsSearchUiState: StateFlow<LyricsSearchUiState> = lyricsStateHolder.searchUiState

    // Toast Events
    private val _toastEvents = shared.toastEvents
    val toastEvents = _toastEvents.asSharedFlow()
    // MediaStore write/delete permission requests (no MANAGE_EXTERNAL_STORAGE needed)
    val writePermissionRequest: SharedFlow<android.content.IntentSender> = shared.writePermissionRequest.asSharedFlow()
    val deletePermissionRequest: SharedFlow<android.content.IntentSender> = shared.deletePermissionRequest.asSharedFlow()

    val albumNavigationRequests = sheetNavigator.albumNavigationRequests
    val artistNavigationRequests = sheetNavigator.artistNavigationRequests
    val searchNavDoubleTapEvents = sheetNavigator.searchNavDoubleTapEvents
    // New event for scrolling to a specific index in the songs list
    val scrollToIndexEvent = sheetNavigator.scrollToIndexEvent

    fun requestLocateCurrentSong() { sheetNavigator.requestLocateCurrentSong() }

    fun showAndPlaySongFromLibrary(song: Song, queueName: String = "Library", isVoluntaryPlay: Boolean = true) {
        queuePlayback.showAndPlaySongFromLibrary(song, queueName, isVoluntaryPlay)
    }

    fun showAndPlaySongFromFavorites(song: Song, queueName: String = "Liked Songs", isVoluntaryPlay: Boolean = true) {
        queuePlayback.showAndPlaySongFromFavorites(song, queueName, isVoluntaryPlay)
    }

    suspend fun getSongsForCurrentLibrarySelection(): List<Song> = queuePlayback.getSongsForCurrentLibrarySelection()

    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = castStateHolder.castRoutes
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = castStateHolder.selectedRoute
    /** Pre-mapped so UI composables don't create a new Flow on every recomposition. */
    val selectedRouteName: StateFlow<String?> = castCoordinator.selectedRouteName
    val routeVolume: StateFlow<Int> = castStateHolder.routeVolume
    val isRefreshingRoutes: StateFlow<Boolean> = castStateHolder.isRefreshingRoutes

    // Connectivity state delegated to ConnectivityStateHolder
    val isWifiEnabled: StateFlow<Boolean> = connectivityStateHolder.isWifiEnabled
    val isWifiRadioOn: StateFlow<Boolean> = connectivityStateHolder.isWifiRadioOn
    val wifiName: StateFlow<String?> = connectivityStateHolder.wifiName
    val isBluetoothEnabled: StateFlow<Boolean> = connectivityStateHolder.isBluetoothEnabled
    val bluetoothName: StateFlow<String?> = connectivityStateHolder.bluetoothName
    val bluetoothAudioDeviceStates: StateFlow<List<BluetoothAudioDeviceState>> = connectivityStateHolder.bluetoothAudioDeviceStates
    val bluetoothAudioDevices: StateFlow<List<String>> = connectivityStateHolder.bluetoothAudioDevices

    val isRemotePlaybackActive: StateFlow<Boolean> = castStateHolder.isRemotePlaybackActive
    val isCastConnecting: StateFlow<Boolean> = castStateHolder.isCastConnecting
    val remotePosition: StateFlow<Long> = castStateHolder.remotePosition

    val trackVolume: StateFlow<Float> = shared.trackVolume.asStateFlow()

    @Inject lateinit var mediaMapper: com.nobodybuilds.listens.data.media.MediaMapper
    @Inject lateinit var imageCacheManager: com.nobodybuilds.listens.data.media.ImageCacheManager

    init {
        // Initialize helper classes with our coroutine scope
        listeningStatsTracker.initialize(viewModelScope)
        dailyMixStateHolder.initialize(viewModelScope)
        lyricsStateHolder.initialize(viewModelScope, currentSongSync.lyricsLoadCallback, playbackStateHolder.stablePlayerState)
        playbackStateHolder.initialize(viewModelScope)
        themeStateHolder.initialize(viewModelScope)

        // Palette preload, artwork colours, lyrics and repository hydration of the current song.
        currentSongSync.start()
    }

    fun setTrackVolume(volume: Float) { transport.setTrackVolume(volume) }

    fun sendToast(message: String) { shared.sendToast(message) }

    fun onSearchNavIconDoubleTapped() { sheetNavigator.onSearchNavIconDoubleTapped() }

    // Last Library Tab Index
    val lastLibraryTabIndexFlow: StateFlow<Int> = preferences.lastLibraryTabIndexFlow
    val libraryTabsFlow: StateFlow<List<String>> = preferences.libraryTabsFlow
    val currentLibraryTabId: StateFlow<LibraryTabId> = libraryTabs.currentLibraryTabId
    val isSortingSheetVisible: StateFlow<Boolean> = libraryTabs.isSortingSheetVisible
    val availableSortOptions: StateFlow<List<SortOption>> = libraryTabs.availableSortOptions
    val isSyncingStateFlow: StateFlow<Boolean> = librarySync.isSyncingStateFlow

    // Library State - delegated to LibraryStateHolder
    val allSongsFlow: StateFlow<ImmutableList<Song>> = libraryStateHolder.allSongs
    val genres: StateFlow<ImmutableList<Genre>> = libraryFlows.genres
    val paletteRegenerationTargets: StateFlow<List<Song>> = libraryFlows.paletteRegenerationTargets
    val homeMixPreviewSongs: StateFlow<ImmutableList<Song>> = libraryFlows.homeMixPreviewSongs
    val songCountFlow: StateFlow<Int> = libraryFlows.songCountFlow
    val albumsFlow: StateFlow<ImmutableList<Album>> = libraryStateHolder.albums
    val artistsFlow: StateFlow<ImmutableList<Artist>> = libraryStateHolder.artists

    var searchQuery by mutableStateOf("")
        private set

    fun updateSearchQuery(query: String) { searchQuery = query }

    val isMediaControllerReady: StateFlow<Boolean> = controllerConnection.isMediaControllerReady
    val playbackAudioMetadata: StateFlow<PlaybackAudioMetadata> = audioMetadataTracker.playbackAudioMetadata
    val favoriteSongIds: StateFlow<Set<String>> = favoritesActions.favoriteSongIds
    val isCurrentSongFavorite: StateFlow<Boolean> = favoritesActions.isCurrentSongFavorite

    private val settingsSlices = PlayerSettingsSlices(
        viewModelScope, currentSongArtists, currentSongLyricsSyncOffset, albumArtQuality, playbackAudioMetadata,
        showPlayerFileInfo, immersiveLyricsEnabled, immersiveLyricsTimeout, isImmersiveTemporarilyDisabled,
        isRemotePlaybackActive, selectedRouteName, isBluetoothEnabled, bluetoothName, navBarCornerRadius,
        navBarStyle, carouselStyle, fullPlayerLoadingTweaks, tapBackgroundClosesPlayer, useSmoothCorners,
        playerThemePreference
    )
    /** The full player's settings in one subscription (see [FullPlayerSlice]). */
    val fullPlayerSlice: StateFlow<FullPlayerSlice> = settingsSlices.fullPlayerSlice
    /** The player sheet's config preferences in one subscription (see [PlayerConfigSlice]). */
    val playerConfigSlice: StateFlow<PlayerConfigSlice> = settingsSlices.playerConfigSlice

    // Favorites now use paginated flow from LibraryStateHolder (DB-level sort & filter)
    val favoritesPagingFlow = libraryStateHolder.favoritesPagingFlow

    // Daily mix state is now managed by DailyMixStateHolder
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.dailyMixSongs
    val yourMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.yourMixSongs

    fun removeFromDailyMix(songId: String) { dailyMixStateHolder.removeFromDailyMix(songId) }

    /** Observes a song by ID from Room DB, combined with the latest favorite status. */
    fun observeSong(songId: String?): Flow<Song?> = favoritesActions.observeSong(songId)

    fun shuffleAllSongs(queueName: String = "All Songs (Shuffled)") { shuffleActions.shuffleAllSongs(queueName) }
    /** Called from the Quick Settings tile; works before the MediaController is connected. */
    fun triggerShuffleAllFromTile() { shuffleActions.triggerShuffleAllFromTile() }
    fun playRandomSong() { shuffleActions.playRandomSong() }
    fun shuffleFavoriteSongs() { shuffleActions.shuffleFavoriteSongs() }
    fun shuffleRandomAlbum() { shuffleActions.shuffleRandomAlbum() }
    fun shuffleRandomArtist() { shuffleActions.shuffleRandomArtist() }

    fun forceUpdateDailyMix() { dailyMixStateHolder.forceUpdate(favoriteSongIdsFlow = favoriteSongIds) }

    fun updatePredictiveBackCollapseFraction(fraction: Float) { sheetNavigator.updatePredictiveBackCollapseFraction(fraction) }
    fun updatePredictiveBackSwipeEdge(edge: Int?) { sheetNavigator.updatePredictiveBackSwipeEdge(edge) }
    fun resetPredictiveBackState() { sheetNavigator.resetPredictiveBackState() }
    fun updateQueueSheetVisibility(visible: Boolean) { sheetNavigator.updateQueueSheetVisibility(visible) }
    fun updateCastSheetVisibility(visible: Boolean) { sheetNavigator.updateCastSheetVisibility(visible) }

    // Connectivity refresh delegated to ConnectivityStateHolder
    fun refreshLocalConnectionInfo(refreshBluetoothDevices: Boolean = false) {
        connectivityStateHolder.refreshLocalConnectionInfo(refreshBluetoothDevices)
    }

    init {
        Log.i("PlayerViewModel", "init started.")

        castCoordinator.restoreExistingSession()

        // Migrations, folder and view settings, blocked folders, current tab, saved sort options.
        librarySync.startPreferenceObservers()

        // launchColorSchemeProcessor() - Handled by ThemeStateHolder and on-demand calls

        dailyMixStateHolder.loadPersistedDailyMix()
        loadSearchHistory()

        librarySync.startInitialDataObservers()

        controllerConnection.connect()

        // Start Cast discovery
        castStateHolder.startDiscovery()

        // Observe selection for HTTP server management
        castCoordinator.observeRouteForHttpServer()

        // Initialize connectivity monitoring (WiFi/Bluetooth)
        connectivityStateHolder.initialize()

        // Initialize sleep timer state holder
        sleepTimerStateHolder.initialize(
            scope = viewModelScope,
            toastEmitter = { msg -> _toastEvents.emit(msg) },
            mediaControllerProvider = { shared.mediaController },
            currentSongIdProvider = { stablePlayerState.map { it.currentSong?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null) },
            songTitleResolver = { songId -> libraryStateHolder.allSongsById.value[songId]?.title ?: "Unknown" }
        )

        // Initialize SearchStateHolder and collect its flows
        searchStateHolder.initialize(viewModelScope)
        librarySync.collectSearchState()

        // Initialize AiStateHolder and collect its flows
        aiStateHolder.initialize(
            scope = viewModelScope,
            allSongsProvider = { musicRepository.getAllSongsOnce() },
            favoriteSongIdsProvider = { favoriteSongIds.value },
            toastEmitter = { msg -> viewModelScope.launch { _toastEvents.emit(msg) } },
            playSongsCallback = { songs, startSong, queueName -> playSongs(songs, startSong, queueName) },
            openPlayerSheetCallback = { shared.isSheetVisible.value = true }
        )
        librarySync.collectAiState()

        // Initialize LibraryStateHolder and sync folders, loading states, sort options and filters
        libraryStateHolder.initialize(viewModelScope)
        librarySync.collectLibraryState()

        castCoordinator.initializeTransfer()

        // Restore the saved repeat mode; keep the session's shuffle flag in sync.
        transport.startSessionSync()

        // Auto-hide undo bar when a new song starts playing
        undoActions.observeDismissUndo()

        Trace.endSection() // End PlayerViewModel.init
    }

    fun onMainActivityStart() { librarySync.onMainActivityStart() }

    fun loadSongsIfNeeded() = libraryStateHolder.loadSongsIfNeeded()
    fun loadAlbumsIfNeeded() = libraryStateHolder.loadAlbumsIfNeeded()
    fun loadArtistsIfNeeded() = libraryStateHolder.loadArtistsIfNeeded()
    fun loadFoldersFromRepository() = libraryStateHolder.loadFoldersFromRepository()

    fun setStorageFilter(filter: com.nobodybuilds.listens.data.model.StorageFilter) { libraryStateHolder.setStorageFilter(filter) }
    fun setHideLocalMedia(hide: Boolean) { libraryTabs.setHideLocalMedia(hide) }
    fun toggleStorageFilter() { libraryTabs.toggleStorageFilter() }

    fun showAndPlaySong(
        song: Song, contextSongs: List<Song>, queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true, cancelPendingQueueBuild: Boolean = true
    ) { queuePlayback.showAndPlaySong(song, contextSongs, queueName, isVoluntaryPlay, cancelPendingQueueBuild) }

    fun showAndPlaySong(song: Song) { queuePlayback.showAndPlaySong(song) }
    fun playAlbum(album: Album) { queuePlayback.playAlbum(album) }
    fun playArtist(artist: Artist) { queuePlayback.playArtist(artist) }

    fun removeSongFromQueue(songId: String) { undoActions.removeSongFromQueue(songId) }
    fun undoRemoveSongFromQueue() { undoActions.undoRemoveSongFromQueue() }
    fun hideQueueItemUndoBar() { undoActions.hideQueueItemUndoBar() }
    fun reorderQueueItem(fromIndex: Int, toIndex: Int) { transport.reorderQueueItem(fromIndex, toIndex) }

    fun togglePlayerSheetState(resetPredictiveState: Boolean = true) { sheetNavigator.togglePlayerSheetState(resetPredictiveState) }
    fun expandPlayerSheet(resetPredictiveState: Boolean = true) { sheetNavigator.expandPlayerSheet(resetPredictiveState) }
    fun collapsePlayerSheet(resetPredictiveState: Boolean = true) { sheetNavigator.collapsePlayerSheet(resetPredictiveState) }
    fun triggerAlbumNavigationFromPlayer(albumId: Long) { sheetNavigator.triggerAlbumNavigationFromPlayer(albumId) }
    fun triggerArtistNavigationFromPlayer(artistId: Long) { sheetNavigator.triggerArtistNavigationFromPlayer(artistId) }
    suspend fun awaitSheetState(target: PlayerSheetState) { sheetNavigator.awaitSheetState(target) }
    suspend fun awaitPlayerCollapse(threshold: Float = 0.1f, timeoutMillis: Long = 800L) {
        sheetNavigator.awaitPlayerCollapse(threshold, timeoutMillis)
    }

    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        queuePlayback.playSongs(songsToPlay, startSong, queueName, playlistId)
    }

    // Start playback with shuffle enabled in one coroutine to avoid racing queue updates
    fun playSongsShuffled(
        songsToPlay: List<Song>, queueName: String = "None", playlistId: String? = null, startAtZero: Boolean = false
    ) { queuePlayback.playSongsShuffled(songsToPlay, queueName, playlistId, startAtZero) }

    fun playExternalUri(uri: Uri) { playbackLauncher.playExternalUri(uri) }
    fun showPlayer() { sheetNavigator.showPlayer() }
    fun playSong(song: Song) { playbackLauncher.playSong(song) }
    fun prepareBenchmarkPlayerFromLibrary() { queuePlayback.prepareBenchmarkPlayerFromLibrary() }

    fun toggleShuffle(currentSongOverride: Song? = null) { transport.toggleShuffle(currentSongOverride) }
    fun cycleRepeatMode() { playbackStateHolder.cycleRepeatMode() }
    fun playPause() { transport.playPause() }
    fun seekTo(position: Long) { playbackStateHolder.seekTo(position) }
    fun nextSong() { playbackStateHolder.nextSong() }
    fun previousSong() { playbackStateHolder.previousSong() }
    fun addSongToQueue(song: Song) { transport.addSongToQueue(song) }
    fun addSongNextToQueue(song: Song) { transport.addSongNextToQueue(song) }
    fun clearQueueExceptCurrent() { transport.clearQueueExceptCurrent() }

    fun toggleFavorite() { favoritesActions.toggleFavorite() }
    fun toggleFavoriteSpecificSong(song: Song, removing: Boolean = false) { favoritesActions.toggleFavoriteSpecificSong(song, removing) }

    // Multi-selection batch operations
    fun playSelectedSongs(songs: List<Song>) { selectionActions.playSelectedSongs(songs) }
    fun addSelectedToQueue(songs: List<Song>) { selectionActions.addSelectedToQueue(songs) }
    fun addSelectedAsNext(songs: List<Song>) { selectionActions.addSelectedAsNext(songs) }
    fun playSelectedAlbums(albums: List<Album>) { selectionActions.playSelectedAlbums(albums) }
    fun addSelectedAlbumsAsNext(albums: List<Album>) { selectionActions.addSelectedAlbumsAsNext(albums) }
    fun addSelectedAlbumsToQueue(albums: List<Album>) { selectionActions.addSelectedAlbumsToQueue(albums) }
    fun queueAndPlaySelectedAlbums(albums: List<Album>) { selectionActions.queueAndPlaySelectedAlbums(albums) }
    fun likeSelectedSongs(songs: List<Song>) { favoritesActions.likeSelectedSongs(songs) }
    fun unlikeSelectedSongs(songs: List<Song>) { favoritesActions.unlikeSelectedSongs(songs) }
    fun shareSelectedAsZip(songs: List<Song>) { selectionActions.shareSelectedAsZip(songs) }

    fun deleteSelectedFromDevice(activity: Activity, songs: List<Song>, onComplete: () -> Unit) {
        songDeletion.deleteSelectedFromDevice(activity, songs, onComplete)
    }
    fun deleteFromDevice(activity: Activity, song: Song, onResult: (Boolean) -> Unit = {}) {
        songDeletion.deleteFromDevice(activity, song, onResult)
    }

    /** Called from the UI after the user approves or denies the MediaStore delete request. */
    fun onDeletePermissionResult(granted: Boolean) { songDeletion.onDeletePermissionResult(granted) }
    suspend fun removeSong(song: Song) { songDeletion.removeSong(song) }

    fun observeSongs(songIds: List<String>): Flow<List<Song>> = musicRepository.getSongsByIds(songIds)
    fun searchSongs(query: String): Flow<List<Song>> = musicRepository.searchSongs(query)
    suspend fun getSongs(songIds: List<String>): List<Song> = musicRepository.getSongsByIds(songIds).first()

    //Sorting
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) { libraryStateHolder.sortSongs(sortOption, persist) }
    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) { libraryStateHolder.sortAlbums(sortOption, persist) }
    fun sortArtists(sortOption: SortOption, persist: Boolean = true) { libraryStateHolder.sortArtists(sortOption, persist) }
    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) { libraryStateHolder.sortFavoriteSongs(sortOption, persist) }
    fun sortFolders(sortOption: SortOption, persist: Boolean = true) { libraryStateHolder.sortFolders(sortOption, persist) }

    fun setFoldersPlaylistView(isPlaylistView: Boolean) { libraryTabs.setFoldersPlaylistView(isPlaylistView) }
    fun setFoldersSource(source: FolderSource) { libraryTabs.setFoldersSource(source) }
    fun navigateToFolder(path: String) { libraryTabs.navigateToFolder(path) }
    fun navigateBackFolder() { libraryTabs.navigateBackFolder() }
    fun setAlbumsListView(isList: Boolean) { libraryTabs.setAlbumsListView(isList) }

    // Search - delegated to SearchStateHolder
    fun updateSearchFilter(filterType: SearchFilterType) { searchStateHolder.updateSearchFilter(filterType) }
    fun loadSearchHistory(limit: Int = 15) { searchStateHolder.loadSearchHistory(limit) }
    fun onSearchQuerySubmitted(query: String) { searchStateHolder.onSearchQuerySubmitted(query) }
    fun performSearch(query: String) { searchStateHolder.performSearch(query) }
    fun deleteSearchHistoryItem(query: String) { searchStateHolder.deleteSearchHistoryItem(query) }
    fun clearSearchHistory() { searchStateHolder.clearSearchHistory() }

    // --- AI Playlist Generation ---
    fun showAiPlaylistSheet() { aiStateHolder.showAiPlaylistSheet() }
    fun dismissAiPlaylistSheet() { aiStateHolder.dismissAiPlaylistSheet() }
    fun clearAiPlaylistError() { aiStateHolder.clearAiPlaylistError() }

    fun generateAiPlaylist(
        prompt: String, minLength: Int, maxLength: Int, saveAsPlaylist: Boolean = false, playlistName: String? = null
    ) {
        aiStateHolder.generateAiPlaylist(
            prompt = prompt, minLength = minLength, maxLength = maxLength,
            saveAsPlaylist = saveAsPlaylist, playlistName = playlistName
        )
    }

    fun regenerateDailyMixWithPrompt(prompt: String) { aiStateHolder.regenerateDailyMixWithPrompt(prompt) }
    fun retryLastPlaylistGeneration() { aiStateHolder.retryLastPlaylistGeneration() }
    fun retryLastMetadataGeneration() { aiStateHolder.retryLastMetadataGeneration() }
    suspend fun generateAiMetadata(song: Song, fields: List<String>): Result<SongMetadata> = aiStateHolder.generateAiMetadata(song, fields)

    // Cast routes - delegated to CastRouteStateHolder
    fun selectRoute(route: MediaRouter.RouteInfo) {
        castRouteStateHolder.selectRoute(route) { message ->
            viewModelScope.launch { _toastEvents.emit(message) }
        }
    }

    fun disconnect(resetConnecting: Boolean = true) { castRouteStateHolder.disconnect(resetConnecting = resetConnecting) }
    fun setRouteVolume(volume: Int) { castRouteStateHolder.setRouteVolume(volume) }
    fun refreshCastRoutes() { castRouteStateHolder.refreshCastRoutes(viewModelScope) }

    override fun onCleared() {
        controllerConnection.release()
        super.onCleared()
        queuePlayback.onCleared()
        castCoordinator.onCleared()
        playbackStateHolder.stopProgressUpdates()
        castTransferStateHolder.onCleared()
        castStateHolder.onCleared()
        searchStateHolder.onCleared()
        aiStateHolder.onCleared()
        libraryStateHolder.onCleared()
        sleepTimerStateHolder.onCleared()
        connectivityStateHolder.onCleared()
        queueUndoStateHolder.onCleared()
        playlistDismissUndoStateHolder.onCleared()
    }

    // Sleep Timer Control Functions - delegated to SleepTimerStateHolder
    fun setSleepTimer(durationMinutes: Int) { sleepTimerStateHolder.setSleepTimer(durationMinutes) }
    fun playCounted(count: Int) { sleepTimerStateHolder.playCounted(count) }
    fun cancelCountedPlay() { sleepTimerStateHolder.cancelCountedPlay() }

    fun setEndOfTrackTimer(enable: Boolean) {
        val currentSongId = stablePlayerState.value.currentSong?.id
        sleepTimerStateHolder.setEndOfTrackTimer(enable, currentSongId)
    }
    fun cancelSleepTimer(overrideToastMessage: String? = null, suppressDefaultToast: Boolean = false) {
        sleepTimerStateHolder.cancelSleepTimer(overrideToastMessage, suppressDefaultToast)
    }

    fun dismissPlaylistAndShowUndo() { undoActions.dismissPlaylistAndShowUndo() }
    fun hideDismissUndoBar() { undoActions.hideDismissUndoBar() }
    fun undoDismissPlaylist() { undoActions.undoDismissPlaylist() }

    fun getSongUrisForGenre(genreId: String): Flow<List<String>> {
        return musicRepository.getMusicByGenre(genreId).map { songs ->
            songs.take(4).mapNotNull { it.albumArtUriString?.takeIf { uri -> uri.isNotBlank() } }
        }
    }

    fun saveLastLibraryTabIndex(tabIndex: Int) { libraryTabs.saveLastLibraryTabIndex(tabIndex) }
    fun showSortingSheet() { libraryTabs.showSortingSheet() }
    fun hideSortingSheet() { libraryTabs.hideSortingSheet() }
    fun onLibraryTabSelected(tabIndex: Int) { libraryTabs.onLibraryTabSelected(tabIndex) }
    fun saveLibraryTabsOrder(tabs: List<String>) { libraryTabs.saveLibraryTabsOrder(tabs) }
    fun resetLibraryTabsOrder() { libraryTabs.resetLibraryTabsOrder() }

    fun selectSongForInfo(song: Song) { currentSongSync.selectSongForInfo(song) }

    fun editSongMetadata(
        song: Song, newTitle: String, newArtist: String, newAlbum: String, newGenre: String, newLyrics: String,
        newTrackNumber: Int, newDiscNumber: Int?, newReplayGainTrackGainDb: String? = null,
        newReplayGainAlbumGainDb: String? = null, coverArtUpdate: CoverArtUpdate?,
    ) {
        metadataEditor.editSongMetadata(
            song, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, newDiscNumber,
            newReplayGainTrackGainDb, newReplayGainAlbumGainDb, coverArtUpdate
        )
    }

    /** Called from the UI after the user approves or denies the MediaStore write permission. */
    fun onWritePermissionResult(granted: Boolean) { metadataEditor.onWritePermissionResult(granted) }
    fun saveLyricsToFile(song: Song, lyrics: Lyrics, preferSynced: Boolean) { metadataEditor.saveLyricsToFile(song, lyrics, preferSynced) }
    suspend fun forceRegenerateAlbumPaletteForSong(song: Song): Boolean = metadataEditor.forceRegenerateAlbumPaletteForSong(song)
    fun batchEditGenre(songs: List<Song>, newGenre: String) { metadataEditor.batchEditGenre(songs, newGenre) }

    // Lyrics - see CurrentSongStateSync
    fun fetchLyricsForCurrentSong(forcePickResults: Boolean = false) { currentSongSync.fetchLyricsForCurrentSong(forcePickResults) }
    fun searchLyricsManually(title: String, artist: String? = null) { currentSongSync.searchLyricsManually(title, artist) }
    fun acceptLyricsSearchResultForCurrentSong(result: LyricsSearchResult) {
        currentSongSync.acceptLyricsSearchResultForCurrentSong(result)
    }
    fun resetLyricsForCurrentSong() { currentSongSync.resetLyricsForCurrentSong() }
    fun resetAllLyrics() { currentSongSync.resetAllLyrics() }
    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport) {
        currentSongSync.importLyricsFromFile(songId, validatedImport)
    }
    fun resetLyricsSearchState() { currentSongSync.resetLyricsSearchState() }

    // Custom Genres Names
    val customGenres: StateFlow<Set<String>> = preferences.customGenres
    val customGenreIcons: StateFlow<Map<String, Int>> = preferences.customGenreIcons
    val isGenreGridView: StateFlow<Boolean> = preferences.isGenreGridView

    fun toggleGenreViewMode() { libraryTabs.toggleGenreViewMode() }
    fun addCustomGenre(genre: String, iconResId: Int? = null) { libraryTabs.addCustomGenre(genre, iconResId) }
}
