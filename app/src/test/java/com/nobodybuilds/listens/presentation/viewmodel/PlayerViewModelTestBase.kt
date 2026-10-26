package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.nobodybuilds.listens.data.database.AlbumArtThemeDao
import com.google.common.util.concurrent.ListenableFuture
import com.nobodybuilds.listens.data.model.SearchFilterType
import com.nobodybuilds.listens.data.model.SearchHistoryItem
import com.nobodybuilds.listens.data.model.SearchResultItem
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.model.StorageFilter
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.preferences.AiPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import io.mockk.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import com.nobodybuilds.listens.MainCoroutineExtension
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.data.telegram.TelegramCacheManager
import com.nobodybuilds.listens.data.worker.SyncManager
import com.nobodybuilds.listens.utils.AppShortcutManager
import com.nobodybuilds.listens.utils.MediaItemBuilder
import com.nobodybuilds.listens.presentation.viewmodel.*
import app.cash.turbine.Turbine

import androidx.core.content.ContextCompat

/**
 * Shared setup for the PlayerViewModel tests: mocks every collaborator, stubs the flows the
 * ViewModel collects at construction, and builds a fresh [playerViewModel] before each test.
 */
@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class)
abstract class PlayerViewModelTestBase {

    protected lateinit var playerViewModel: PlayerViewModel
    protected val mockMusicRepository: MusicRepository = mockk()
    protected val mockUserPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    protected val mockAiPreferencesRepository: AiPreferencesRepository = mockk(relaxed = true)
    protected val mockThemePreferencesRepository: ThemePreferencesRepository = mockk(relaxed = true)
    protected val mockAlbumArtThemeDao: AlbumArtThemeDao = mockk(relaxed = true)
    protected val mockContext: Context = mockk(relaxed = true)

    // New Mocks
    protected val mockSyncManager: SyncManager = mockk(relaxed = true)
    protected val mockDualPlayerEngine: DualPlayerEngine = mockk(relaxed = true)
    protected val mockAppShortcutManager: AppShortcutManager = mockk(relaxed = true)
    protected val mockTelegramCacheManager: TelegramCacheManager = mockk(relaxed = true)
    protected val mockTelegramCacheManagerProvider: Lazy<TelegramCacheManager> = mockk()
    protected val mockTelegramRepository: com.nobodybuilds.listens.data.telegram.TelegramRepository = mockk(relaxed = true)
    protected val mockListeningStatsTracker: ListeningStatsTracker = mockk(relaxed = true)
    protected val mockDailyMixStateHolder: DailyMixStateHolder = mockk(relaxed = true)
    protected val mockLyricsStateHolder: LyricsStateHolder = mockk(relaxed = true)
    protected val mockCastStateHolder: CastStateHolder = mockk(relaxed = true)
    protected val mockCastRouteStateHolder: CastRouteStateHolder = mockk(relaxed = true)
    protected val mockQueueStateHolder: QueueStateHolder = mockk(relaxed = true)
    protected val mockQueueUndoStateHolder: QueueUndoStateHolder = mockk(relaxed = true)
    protected val mockPlaylistDismissUndoStateHolder: PlaylistDismissUndoStateHolder = mockk(relaxed = true)
    protected val mockPlaybackStateHolder: PlaybackStateHolder = mockk(relaxed = true)
    protected val mockConnectivityStateHolder: ConnectivityStateHolder = mockk(relaxed = true)
    protected val mockSleepTimerStateHolder: SleepTimerStateHolder = mockk(relaxed = true)
    protected val mockSearchStateHolder: SearchStateHolder = mockk(relaxed = true)
    protected val mockAiStateHolder: AiStateHolder = mockk(relaxed = true)
    protected val mockLibraryStateHolder: LibraryStateHolder = mockk(relaxed = true)
    protected val mockFolderNavigationStateHolder: FolderNavigationStateHolder = mockk(relaxed = true)
    protected val mockLibraryTabsStateHolder: LibraryTabsStateHolder = mockk(relaxed = true)
    protected val mockCastTransferStateHolder: CastTransferStateHolder = mockk(relaxed = true)
    protected val mockMetadataEditStateHolder: MetadataEditStateHolder = mockk(relaxed = true)
    protected val mockSongRemovalStateHolder: SongRemovalStateHolder = mockk(relaxed = true)
    protected val mockExternalMediaStateHolder: ExternalMediaStateHolder = mockk(relaxed = true)
    protected val mockThemeStateHolder: ThemeStateHolder = mockk(relaxed = true)
    protected val mockMultiSelectionStateHolder: MultiSelectionStateHolder = mockk(relaxed = true)
    protected val mockPlaylistSelectionStateHolder: PlaylistSelectionStateHolder = mockk(relaxed = true)
    protected lateinit var mockMediaControllerFactory: com.nobodybuilds.listens.data.media.MediaControllerFactory

    protected val testDispatcher = StandardTestDispatcher()

    // Test Flows
    protected val _allSongsFlow = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    // Fix: Use ImmutableList for Search Flows as per SearchStateHolder definition
    protected val _searchHistoryFlow = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    protected val _searchResultsFlow = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    protected val _selectedSearchFilterFlow = MutableStateFlow(SearchFilterType.ALL)
    protected val _castSessionFlow = MutableStateFlow<com.google.android.gms.cast.framework.CastSession?>(null)
    protected val _repeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    protected lateinit var mockController: MediaController
    protected val controllerRepeatModeWrites = mutableListOf<Int>()
    protected var controllerRepeatMode = Player.REPEAT_MODE_OFF

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)

        mockkStatic(ContextCompat::class)
        // android.net.Uri is a stub on the JVM (parse returns null); return a mock instead.
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } returns mockk(relaxed = true)
        val directExecutor = java.util.concurrent.Executor { it.run() }
        every { ContextCompat.getMainExecutor(any()) } returns directExecutor
        every { mockTelegramCacheManager.embeddedArtUpdated } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { mockTelegramCacheManagerProvider.get() } returns mockTelegramCacheManager

        // Mock UserPreferences
        coEvery { mockUserPreferencesRepository.favoriteSongIdsFlow } returns flowOf(emptySet())
        coEvery { mockUserPreferencesRepository.songsSortOptionFlow } returns flowOf("SongTitleAZ")
        coEvery { mockUserPreferencesRepository.albumsSortOptionFlow } returns flowOf("AlbumTitleAZ")
        coEvery { mockUserPreferencesRepository.artistsSortOptionFlow } returns flowOf("ArtistNameAZ")
        coEvery { mockUserPreferencesRepository.likedSongsSortOptionFlow } returns flowOf("LikedSongTitleAZ")
        coEvery { mockUserPreferencesRepository.navBarCornerRadiusFlow } returns flowOf(32)
        coEvery { mockUserPreferencesRepository.navBarStyleFlow } returns flowOf("Default")
        coEvery { mockUserPreferencesRepository.libraryNavigationModeFlow } returns flowOf("TabRow")
        coEvery { mockUserPreferencesRepository.carouselStyleFlow } returns flowOf("NoPeek")
        coEvery { mockUserPreferencesRepository.fullPlayerLoadingTweaksFlow } returns flowOf(com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks())
        coEvery { mockUserPreferencesRepository.tapBackgroundClosesPlayerFlow } returns flowOf(true)
        coEvery { mockUserPreferencesRepository.hapticsEnabledFlow } returns flowOf(true)
        coEvery { mockUserPreferencesRepository.foldersSortOptionFlow } returns flowOf("FolderNameAZ") // Added missing mock
        coEvery { mockUserPreferencesRepository.persistentShuffleEnabledFlow } returns flowOf(false) // Added missing mock
        coEvery { mockUserPreferencesRepository.isShuffleOnFlow } returns flowOf(false) // Added missing mock
        every { mockUserPreferencesRepository.repeatModeFlow } returns _repeatModeFlow
        coEvery { mockThemePreferencesRepository.playerThemePreferenceFlow } returns flowOf("Global")
        coEvery { mockAiPreferencesRepository.aiProvider } returns flowOf("GEMINI")
        coEvery { mockAiPreferencesRepository.geminiApiKey } returns flowOf("")
        coEvery { mockAiPreferencesRepository.deepseekApiKey } returns flowOf("")

        // Mock StateHolders Flows
        every { mockLibraryStateHolder.allSongs } returns _allSongsFlow
        every { mockLibraryStateHolder.isLoadingLibrary } returns MutableStateFlow(false)
        every { mockLibraryStateHolder.isLoadingCategories } returns MutableStateFlow(false)
        every { mockLibraryStateHolder.genres } returns MutableStateFlow(persistentListOf())
        every { mockLibraryStateHolder.albums } returns MutableStateFlow(persistentListOf())
        every { mockLibraryStateHolder.artists } returns MutableStateFlow(persistentListOf())
        every { mockLibraryStateHolder.musicFolders } returns MutableStateFlow(persistentListOf())
        every { mockLibraryStateHolder.currentSongSortOption } returns MutableStateFlow<SortOption>(SortOption.SongTitleAZ)
        every { mockLibraryStateHolder.currentAlbumSortOption } returns MutableStateFlow<SortOption>(SortOption.AlbumTitleAZ)
        every { mockLibraryStateHolder.currentArtistSortOption } returns MutableStateFlow<SortOption>(SortOption.ArtistNameAZ)
        every { mockLibraryStateHolder.currentFolderSortOption } returns MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
        every { mockLibraryStateHolder.currentFavoriteSortOption } returns MutableStateFlow<SortOption>(SortOption.LikedSongTitleAZ)
        every { mockLibraryStateHolder.currentStorageFilter } returns MutableStateFlow(StorageFilter.ALL)

        every { mockSearchStateHolder.searchHistory } returns _searchHistoryFlow
        every { mockSearchStateHolder.searchResults } returns _searchResultsFlow
        every { mockSearchStateHolder.selectedSearchFilter } returns _selectedSearchFilterFlow
        every { mockSearchStateHolder.loadSearchHistory(any()) } just runs
        every { mockSearchStateHolder.clearSearchHistory() } just runs
        every { mockSearchStateHolder.deleteSearchHistoryItem(any()) } just runs
        every { mockSearchStateHolder.updateSearchFilter(any()) } just runs
        every { mockSearchStateHolder.initialize(any()) } just runs // Added missing initialize mock

        every { mockAiStateHolder.showAiPlaylistSheet } returns MutableStateFlow(false)
        every { mockAiStateHolder.isGeneratingAiPlaylist } returns MutableStateFlow(false)
        every { mockAiStateHolder.aiError } returns MutableStateFlow<String?>(null)
        every { mockAiStateHolder.isGeneratingMetadata } returns MutableStateFlow(false)
        every { mockAiStateHolder.initialize(any(), any(), any(), any(), any(), any()) } just runs
 
        every { mockCastStateHolder.castSession } returns _castSessionFlow
        every { mockCastStateHolder.startDiscovery() } just runs // Added missing mock
        every { mockCastStateHolder.selectedRoute } returns MutableStateFlow<androidx.mediarouter.media.MediaRouter.RouteInfo?>(null) // Added missing mock

        // Connectivity mocks removed as properties differ from expectations
        every { mockConnectivityStateHolder.initialize() } just runs
        every { mockConnectivityStateHolder.offlinePlaybackBlocked } returns MutableSharedFlow()

        val stablePlayerState = MutableStateFlow(StablePlayerState(currentSong = null))
        every { mockPlaybackStateHolder.stablePlayerState } returns stablePlayerState
        every { mockPlaybackStateHolder.setMediaController(any()) } just runs // Added missing mock

        every { mockSleepTimerStateHolder.initialize(any(), any(), any(), any(), any()) } just runs // Added missing mock
        every { mockLibraryStateHolder.initialize(any()) } just runs // Added missing mock
        every { mockCastTransferStateHolder.initialize(any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
        
        // Mock MusicRepository Basic Returns
        every { mockMusicRepository.getPaginatedSongs(any(), any()) } returns flowOf(androidx.paging.PagingData.empty())
        every { mockMusicRepository.getDistinctAlbumArtSongs() } returns flowOf(emptyList())
        every { mockMusicRepository.getHomeMixPreviewSongs(any()) } returns flowOf(emptyList())
        every { mockMusicRepository.getSongCountFlow() } returns flowOf(0)
        every { mockMusicRepository.getAllUniqueAlbumArtUris() } returns flowOf(emptyList())
        every { mockMusicRepository.getFavoriteSongIdsFlow() } returns flowOf(emptySet())
        every { mockMusicRepository.getGenres() } returns flowOf(emptyList())
        every { mockMusicRepository.getAudioFiles() } returns flowOf(emptyList())
        coEvery { mockMusicRepository.getFavoriteSongIdsOnce() } returns emptySet()
        every { mockMusicRepository.getFavoriteSongIdsFlow() } returns flowOf(emptySet())
        every { mockMusicRepository.telegramRepository } returns mockTelegramRepository
        every { mockTelegramRepository.downloadCompleted } returns MutableSharedFlow<Int>()
        every { mockLyricsStateHolder.songUpdates } returns MutableSharedFlow()

        // Initialize PlayerViewModel
        val sessionToken = mockk<SessionToken>(relaxed = true)
        mockMediaControllerFactory = mockk(relaxed = true)
        
        // Mock ListenableFuture for MediaController creation
        mockController = mockk(relaxed = true)
        every { mockController.repeatMode } answers { controllerRepeatMode }
        every { mockController.repeatMode = any() } answers {
            controllerRepeatMode = firstArg()
            controllerRepeatModeWrites += controllerRepeatMode
        }
        val mockFuture = mockk<ListenableFuture<MediaController>>(relaxed = true)
        every { mockFuture.get() } returns mockController
        every { mockFuture.addListener(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
        }
        every { mockMediaControllerFactory.create(any(), any(), any()) } returns mockFuture
        
        // Ensure manual executor for main thread to prevent RejectedExecutionException
        // We already mocked ContextCompat.getMainExecutor above.
        
        playerViewModel = PlayerViewModel(
            mockContext,
            mockMusicRepository,
            mockUserPreferencesRepository,
            mockAiPreferencesRepository,
            mockThemePreferencesRepository,
            mockAlbumArtThemeDao,
            mockSyncManager,
            mockDualPlayerEngine,
            mockk<com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRadio>(relaxed = true),
            mockAppShortcutManager,
            mockTelegramCacheManagerProvider,
            mockListeningStatsTracker,
            mockDailyMixStateHolder,
            mockLyricsStateHolder,
            mockCastStateHolder,
            mockCastRouteStateHolder,
            mockQueueStateHolder,
            mockQueueUndoStateHolder,
            mockPlaylistDismissUndoStateHolder,
            mockPlaybackStateHolder,
            mockConnectivityStateHolder,
            mockSleepTimerStateHolder,
            mockSearchStateHolder,
            mockAiStateHolder,
            mockLibraryStateHolder,
            mockFolderNavigationStateHolder,
            mockLibraryTabsStateHolder,
            mockCastTransferStateHolder,
            mockMetadataEditStateHolder,
            mockSongRemovalStateHolder,
            mockExternalMediaStateHolder,
            mockThemeStateHolder,
            mockMultiSelectionStateHolder,
            mockPlaylistSelectionStateHolder,
            sessionToken,
            mockMediaControllerFactory
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    protected fun PlayerViewModel.updateAllSongs(songs: List<Song>) {
        // Correctly update the flow that PlayerViewModel collects
        _allSongsFlow.value = songs.toImmutableList()
    }
}
