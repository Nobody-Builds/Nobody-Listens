package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.navigation.navigateSafelyReplacing
import com.nobodybuilds.listens.presentation.navigation.navigateToTopLevelSafely
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.CollagePattern
import com.nobodybuilds.listens.presentation.components.AlbumArtCollage
import com.nobodybuilds.listens.presentation.components.BetaInfoBottomSheet
import com.nobodybuilds.listens.presentation.components.Beta05CleanInstallDisclaimerDialog
import com.nobodybuilds.listens.presentation.components.ChangelogBottomSheet
import com.nobodybuilds.listens.presentation.netease.dashboard.NeteaseDashboardViewModel
import com.nobodybuilds.listens.presentation.ytmusic.dashboard.YouTubeMusicDashboardViewModel
import com.nobodybuilds.listens.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.nobodybuilds.listens.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.nobodybuilds.listens.presentation.qqmusic.dashboard.QqMusicDashboardViewModel
import com.nobodybuilds.listens.presentation.components.DailyMixSection
import com.nobodybuilds.listens.presentation.nb.NbHomeHeader
import com.nobodybuilds.listens.presentation.nb.NbMixTile
import com.nobodybuilds.listens.presentation.nb.NbRecentlyPlayedRow
import com.nobodybuilds.listens.presentation.nb.NbWeekStats
import com.nobodybuilds.listens.presentation.nb.MadeForYouMix
import com.nobodybuilds.listens.presentation.nb.NbHomeViewModel
import com.nobodybuilds.listens.presentation.nb.NbMadeForYouRow
import com.nobodybuilds.listens.presentation.nb.NbSongCoverRow
import com.nobodybuilds.listens.presentation.nb.NbTopArtistsRow
import com.nobodybuilds.listens.presentation.components.HomeOptionsBottomSheet
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.RecentlyPlayedSectionMinSongsToShow
import com.nobodybuilds.listens.presentation.model.collectRecentlyPlayedSongIds
import com.nobodybuilds.listens.presentation.model.mapRecentlyPlayedSongs
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.components.StreamingProviderSheet
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel
import com.nobodybuilds.listens.presentation.viewmodel.StatsViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val HomeLoadingPlaceholderMinDurationMillis = 1200L

// Modern HomeScreen with collapsible top bar and staggered grid layout
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    neteaseViewModel: NeteaseDashboardViewModel = hiltViewModel(),
    qqMusicViewModel: QqMusicDashboardViewModel = hiltViewModel(),
    navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel(),
    jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel(),
    youTubeMusicViewModel: YouTubeMusicDashboardViewModel = hiltViewModel(),
    jioSaavnViewModel: com.nobodybuilds.listens.presentation.jiosaavn.dashboard.JioSaavnDashboardViewModel = hiltViewModel(),
    nbHomeViewModel: NbHomeViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit
) {
    val context = LocalContext.current
    // DETECTAR MODO BENCHMARK
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    val statsViewModel: StatsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsStateWithLifecycle()
    val curatedYourMixSongs by playerViewModel.yourMixSongs.collectAsStateWithLifecycle()
    val homeMixPreviewSongs by playerViewModel.homeMixPreviewSongs.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val usesFallbackHomeMix = remember(curatedYourMixSongs, dailyMixSongs) {
        curatedYourMixSongs.isEmpty() && dailyMixSongs.isEmpty()
    }
    val yourMixSongs = remember(curatedYourMixSongs, dailyMixSongs, homeMixPreviewSongs) {
        when {
            curatedYourMixSongs.isNotEmpty() -> curatedYourMixSongs
            dailyMixSongs.isNotEmpty() -> dailyMixSongs
            else -> homeMixPreviewSongs
        }
    }
    var homePlaceholderRefreshGeneration by rememberSaveable { mutableIntStateOf(0) }
    var hasHomeLoadingMinimumElapsed by rememberSaveable(homePlaceholderRefreshGeneration) {
        mutableStateOf(false)
    }

    LaunchedEffect(homePlaceholderRefreshGeneration, yourMixSongs.isEmpty()) {
        if (yourMixSongs.isEmpty()) {
            hasHomeLoadingMinimumElapsed = false
            delay(HomeLoadingPlaceholderMinDurationMillis)
            hasHomeLoadingMinimumElapsed = true
        } else {
            hasHomeLoadingMinimumElapsed = true
        }
    }

    val shouldShowYourMixLoadingPlaceholder = yourMixSongs.isEmpty() && !hasHomeLoadingMinimumElapsed
    val recentSongIds = remember(playbackHistory) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            maxItems = 64
        )
    }
    val recentlyPlayedSourceSongsInitialValue = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) persistentListOf<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentlyPlayedSourceSongsInitialValue)
    val latestRecentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            maxItems = 64
        )
    }
    // Keep the visible Home snapshot stable and only refresh it once the screen is off-screen.
    var recentlyPlayedSongs by remember { mutableStateOf(latestRecentlyPlayedSongs) }
    val latestRecentlyPlayedSongsState = rememberUpdatedState(latestRecentlyPlayedSongs)

    LaunchedEffect(latestRecentlyPlayedSongs, lifecycleOwner) {
        val isHomeVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (recentlyPlayedSongs.isEmpty() || !isHomeVisible) {
            recentlyPlayedSongs = latestRecentlyPlayedSongs
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recentlyPlayedSongs = latestRecentlyPlayedSongsState.value
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val recentlyPlayedQueue = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    ReportDrawnWhen {
        yourMixSongs.isNotEmpty() || hasHomeLoadingMinimumElapsed || isBenchmarkMode
    }

    val yourMixSong: String = "Today's Mix for you"

    // 2) Observar sólo el currentSong (o null) para saber si mostrar padding
    val currentSong by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong }
    }.collectAsStateWithLifecycle(initialValue = null)

    // 3) Observe shuffle state for sync
    val isShuffleEnabled by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState
            .map { it.isShuffleEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)


    // Daylight Home data: "Made for you" rule-based mixes, recently added, top artists.
    val showClassicSections by nbHomeViewModel.showClassicSections.collectAsStateWithLifecycle()
    val recentlyAddedSongs by nbHomeViewModel.recentlyAdded.collectAsStateWithLifecycle()
    val favouriteSongs by nbHomeViewModel.favourites.collectAsStateWithLifecycle()
    val smartMixes by nbHomeViewModel.smartMixes.collectAsStateWithLifecycle()
    val forgottenFavourites by nbHomeViewModel.forgottenFavourites.collectAsStateWithLifecycle()
    val topArtists by nbHomeViewModel.topArtists.collectAsStateWithLifecycle()
    DisposableEffect(lifecycleOwner, nbHomeViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) nbHomeViewModel.refreshIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val madeForYouMixes = remember(dailyMixSongs, favouriteSongs, recentlyAddedSongs, forgottenFavourites) {
        buildList {
            if (dailyMixSongs.isNotEmpty()) add(
                MadeForYouMix("daily", "Daily mix", "${dailyMixSongs.size} songs picked for today", dailyMixSongs)
            )
            if (favouriteSongs.isNotEmpty()) add(
                MadeForYouMix("favourites", "Favourites", "Your liked songs", favouriteSongs)
            )
            if (recentlyAddedSongs.isNotEmpty()) add(
                MadeForYouMix("recently_added", "Recently added", "New in your library", recentlyAddedSongs)
            )
            if (forgottenFavourites.isNotEmpty()) add(
                MadeForYouMix("forgotten", "Forgotten favourites", "Not played in a month", forgottenFavourites)
            )
        }
    }

    // Padding inferior si hay canción en reproducción
    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var showChangelogBottomSheet by remember { mutableStateOf(false) }
    var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
    var showStreamingProviderSheet by remember { mutableStateOf(false) }
    var cleanInstallDisclaimerDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val betaSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    LocalContext.current

    val homeStatsOverview by statsViewModel.homeOverview.collectAsStateWithLifecycle()
    LaunchedEffect(homeStatsOverview) {
        homeStatsOverview?.let { overview ->
            nbHomeViewModel.setTopArtistNames(overview.topArtists.map { it.artist })
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 180.dp.toPx() } }
    val isScrolledPastThreshold = remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    // Drawer state for sidebar
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // The old "Beta 0.5.0 clean install" notice was for the upstream app; Nobody Listens never shows it.
    @Suppress("UNUSED_VARIABLE") val unusedDisclaimerState = cleanInstallDisclaimerDismissedThisSession
    val shouldShowCleanInstallDisclaimer = false

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            val showUpdateBanner = com.nobodybuilds.listens.presentation.update.rememberUpdateBannerVisible()
            val showFoldersNotice = com.nobodybuilds.listens.presentation.folders.rememberFoldersMigratedNoticeVisible()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = paddingValuesParent.calculateBottomPadding()
                            + 38.dp + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                item(key = "nb_home_header", contentType = "nb_home_header") {
                    NbHomeHeader(
                        onSettingsClick = { navController.navigateSafely(Screen.Settings.route) },
                        onWhatsNewClick = { showChangelogBottomSheet = true },
                        onSourcesClick = { showStreamingProviderSheet = true }
                    )
                }
                if (showUpdateBanner) item(key = "update_banner") { com.nobodybuilds.listens.presentation.update.UpdateBanner() }
                if (showFoldersNotice) item(key = "folders_migrated_notice") {
                    com.nobodybuilds.listens.presentation.folders.FoldersMigratedNotice(
                        onReview = { navController.navigateSafely(Screen.SettingsCategory.createRoute("library")) }
                    )
                }

                if (yourMixSongs.isEmpty()) {
                    item(
                        key = "your_mix_placeholder",
                        contentType = "your_mix_placeholder"
                    ) {
                        if (shouldShowYourMixLoadingPlaceholder) {
                            YourMixLoadingPlaceholder()
                        } else {
                            YourMixEmptyPlaceholder(
                                onRefresh = {
                                    homePlaceholderRefreshGeneration++
                                    settingsViewModel.refreshLibrary()
                                    playerViewModel.forceUpdateDailyMix()
                                    nbHomeViewModel.refreshIfStale(force = true)
                                }
                            )
                        }
                    }
                } else {
                    item(
                        key = "your_mix_header",
                        contentType = "your_mix_header"
                    ) {
                        NbMixTile(
                            songs = yourMixSongs,
                            isShuffleEnabled = isShuffleEnabled,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onPlay = {
                                if (usesFallbackHomeMix) {
                                    playerViewModel.shuffleAllSongs(queueName = "Your Mix")
                                } else {
                                    playerViewModel.playSongsShuffled(
                                        songsToPlay = yourMixSongs,
                                        queueName = "Your Mix",
                                        startAtZero = true,
                                    )
                                }
                            }
                        )
                    }
                }

                // Classic sections (album collage + Daily Mix), behind
                // Settings › Appearance › "Show classic Home sections".
                if (showClassicSections && yourMixSongs.isNotEmpty()) {
                    item(
                        key = "album_art_collage",
                        contentType = "album_art_collage"
                    ) {
                        val basePattern = settingsUiState.collagePattern
                        val isAutoRotate = settingsUiState.collageAutoRotate
                        val patterns = remember { CollagePattern.entries }

                        val activePattern = if (isAutoRotate) {
                            var rotationIndex by rememberSaveable { mutableIntStateOf(-1) }
                            LaunchedEffect(Unit) { rotationIndex++ }
                            remember(rotationIndex) {
                                patterns[rotationIndex.coerceAtLeast(0) % patterns.size]
                            }
                        } else {
                            basePattern
                        }

                        AlbumArtCollage(
                            modifier = Modifier.fillMaxWidth(),
                            songs = yourMixSongs,
                            padding = 14.dp,
                            height = 400.dp,
                            pattern = activePattern,
                            onSongClick = { song ->
                                if (usesFallbackHomeMix) {
                                    playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                                } else {
                                    playerViewModel.showAndPlaySong(song, yourMixSongs, "Your Mix")
                                }
                            }
                        )
                    }
                }

                if (showClassicSections && dailyMixSongs.isNotEmpty()) {
                    item(
                        key = "daily_mix_section",
                        contentType = "daily_mix_section"
                    ) {
                        DailyMixSection(
                            songs = dailyMixSongs,
                            onClickOpen = {
                                navController.navigateSafely(Screen.DailyMixScreen.route)
                            },
                            onNavigateToAlbum = { song ->
                                navController.navigateSafelyReplacing(
                                    route = Screen.AlbumDetail.createRoute(song.albumId),
                                    patternToPop = Screen.AlbumDetail.route
                                )
                            },
                            onNavigateToArtist = { song ->
                                navController.navigateSafelyReplacing(
                                    route = Screen.ArtistDetail.createRoute(song.artistId),
                                    patternToPop = Screen.ArtistDetail.route
                                )
                            },
                            onNavigateToGenre = { song ->
                                song.genre?.let {
                                    navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                                }
                            },
                            playerViewModel = playerViewModel
                        )
                    }
                }

                item(key = "spaces", contentType = "spaces") {
                    com.nobodybuilds.listens.presentation.nb.NbSpacesRow(
                        onLofi = { navController.navigateSafely(Screen.Lofi.route) },
                        onFocus = { navController.navigateSafely(Screen.Focus.route) },
                        onSleep = { navController.navigateSafely(Screen.Sleep.route) },
                        onGarden = { navController.navigateSafely(Screen.Garden.route) },
                        onParty = { navController.navigateSafely(Screen.Party.route) },
                        onTasteMatch = { navController.navigateSafely(Screen.TasteMatch.route) },
                        onCovers = { navController.navigateSafely(Screen.CoverStudio.createRoute()) },
                    )
                }
                if (madeForYouMixes.isNotEmpty() || smartMixes.isNotEmpty()) {
                    item(key = "made_for_you", contentType = "made_for_you") {
                        NbMadeForYouRow(
                            // On-device smart mixes lead the row when the smart layer is on.
                            mixes = smartMixes + madeForYouMixes,
                            extraMixes = emptyList(),
                            onMixClick = { mix ->
                                mix.songs.firstOrNull()?.let { first ->
                                    playerViewModel.playSongs(
                                        songsToPlay = mix.songs,
                                        startSong = first,
                                        queueName = mix.title
                                    )
                                }
                            }
                        )
                    }
                }

                if (recentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
                    item(
                        key = "recently_played_section",
                        contentType = "recently_played_section"
                    ) {
                        NbRecentlyPlayedRow(
                            songs = recentlyPlayedQueue.take(20),
                            onSongClick = { song ->
                                if (recentlyPlayedQueue.isNotEmpty()) {
                                    playerViewModel.playSongs(
                                        songsToPlay = recentlyPlayedQueue,
                                        startSong = song,
                                        queueName = "Recently Played"
                                    )
                                }
                            },
                            onSeeAll = {
                                navController.navigateSafely(Screen.RecentlyPlayed.route)
                            },
                            currentSongId = currentSong?.id
                        )
                    }
                }

                if (recentlyAddedSongs.isNotEmpty()) {
                    item(key = "recently_added_section", contentType = "recently_added_section") {
                        NbSongCoverRow(
                            title = "Recently added",
                            songs = recentlyAddedSongs.take(20),
                            currentSongId = currentSong?.id,
                            keyPrefix = "added",
                            onSongClick = { song ->
                                playerViewModel.playSongs(
                                    songsToPlay = recentlyAddedSongs,
                                    startSong = song,
                                    queueName = "Recently added"
                                )
                            }
                        )
                    }
                }

                if (homeStatsOverview != null) {
                    item(
                        key = "listening_stats_preview",
                        contentType = "listening_stats_preview"
                    ) {
                        homeStatsOverview?.let { overview ->
                            NbWeekStats(
                                summary = overview,
                                onOpenStats = { navController.navigateToTopLevelSafely(Screen.Stats.route) }
                            )
                        }
                    }
                }

                if (topArtists.isNotEmpty()) {
                    item(key = "top_artists", contentType = "top_artists") {
                        NbTopArtistsRow(
                            artists = topArtists,
                            onArtistClick = { artist ->
                                navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id))
                            }
                        )
                    }
                }
            }
        }
    }
    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showOptionsBottomSheet = false
                            navController.navigateSafely(Screen.DJSpace.route)
                        }
                    }
                }
            )
        }
    }
    if (showChangelogBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChangelogBottomSheet = false },
            sheetState = sheetState
        ) {
            ChangelogBottomSheet()
        }
    }
    if (showBetaInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBetaInfoBottomSheet = false },
            sheetState = betaSheetState,
            //contentWindowInsets = { WindowInsets.statusBars.only(WindowInsets.statusBars) }
        ) {
            BetaInfoBottomSheet()
        }
    }
    if (showStreamingProviderSheet) {
        val isNeteaseLoggedIn by neteaseViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isQqMusicLoggedIn by qqMusicViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isNavidromeLoggedIn by navidromeViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isJellyfinLoggedIn by jellyfinViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isYouTubeMusicLoggedIn by youTubeMusicViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isJioSaavnLoggedIn by jioSaavnViewModel.isLoggedIn.collectAsStateWithLifecycle()
        StreamingProviderSheet(
            onDismissRequest = { showStreamingProviderSheet = false },
            isNeteaseLoggedIn = isNeteaseLoggedIn,
            onNavigateToNeteaseDashboard = {
                navController.navigateSafely(Screen.NeteaseDashboard.route)
            },
            isQqMusicLoggedIn = isQqMusicLoggedIn,
            onNavigateToQqMusicDashboard = {
                navController.navigateSafely(Screen.QqMusicDashboard.route)
            },
            isNavidromeLoggedIn = isNavidromeLoggedIn,
            onNavigateToNavidromeDashboard = {
                navController.navigateSafely(Screen.NavidromeDashboard.route)
            },
            isJellyfinLoggedIn = isJellyfinLoggedIn,
            onNavigateToJellyfinDashboard = {
                navController.navigateSafely(Screen.JellyfinDashboard.route)
            },
            isYouTubeMusicLoggedIn = isYouTubeMusicLoggedIn,
            onNavigateToYouTubeMusicDashboard = {
                navController.navigateSafely(Screen.YouTubeMusicDashboard.route)
            },
            isJioSaavnLoggedIn = isJioSaavnLoggedIn,
            onNavigateToJioSaavnDashboard = {
                navController.navigateSafely(Screen.JioSaavnDashboard.route)
            }
        )
    }
    if (shouldShowCleanInstallDisclaimer) {
        Beta05CleanInstallDisclaimerDialog(
            onDismiss = { dontShowAgain ->
                cleanInstallDisclaimerDismissedThisSession = true
                if (dontShowAgain) {
                    settingsViewModel.setBeta05CleanInstallDisclaimerDismissed(true)
                }
            }
        )
    }
}
