package com.nobodybuilds.listens

import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import android.content.Context
import android.content.Intent
import android.os.Trace
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nobodybuilds.listens.data.github.GitHubAnnouncementPropertiesService
import com.nobodybuilds.listens.data.github.PlayStoreAnnouncementRemoteConfig
import com.nobodybuilds.listens.data.preferences.NavBarStyle
import com.nobodybuilds.listens.data.preferences.sanitizeNavBarCornerRadius
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.presentation.components.AppSidebarDrawer
import com.nobodybuilds.listens.presentation.components.DismissUndoBar
import com.nobodybuilds.listens.presentation.components.DrawerDestination
import com.nobodybuilds.listens.presentation.components.MiniPlayerBottomSpacer
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.PlayerInternalNavigationBar
import com.nobodybuilds.listens.presentation.components.PlayStoreAnnouncementDefaults
import com.nobodybuilds.listens.presentation.components.PlayStoreAnnouncementDialog
import com.nobodybuilds.listens.presentation.components.PlayStoreAnnouncementUiModel
import com.nobodybuilds.listens.presentation.components.UnifiedPlayerSheetV2
import com.nobodybuilds.listens.presentation.components.calculatePlayerSheetCollapsedTargetY
import com.nobodybuilds.listens.presentation.components.resolveNavBarOccupiedHeight
import com.nobodybuilds.listens.presentation.components.resolveNavBarSurfaceHeight
import com.nobodybuilds.listens.presentation.components.sanitizeNavigationBarBottomInset
import com.nobodybuilds.listens.presentation.navigation.AppNavigation
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.utils.LogUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.nobodybuilds.listens.presentation.utils.AppHapticsConfig
import com.nobodybuilds.listens.presentation.utils.LocalAppHapticsConfig
import com.nobodybuilds.listens.presentation.utils.NoOpHapticFeedback
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class DismissUndoBarSlice(
    val isVisible: Boolean = false,
    val durationMillis: Long = 4000L
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun MainUI(
    activity: ComponentActivity,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    userPreferencesRepository: UserPreferencesRepository,
    nobodyListensHapticsEngine: com.nobodybuilds.listens.presentation.haptics.NobodyListensHapticsEngine,
    openExternalUrl: (String) -> Unit
) {
    Trace.beginSection("MainActivity.MainUI")

    val commonNavItems = remember {
        persistentListOf(
            BottomNavItem("Home", R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, Screen.Home),
            BottomNavItem("Library", R.drawable.rounded_library_music_24, R.drawable.round_library_music_24, Screen.Library),
            BottomNavItem("Search", R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search),
            BottomNavItem("Stats", R.drawable.rounded_monitoring_24, R.drawable.rounded_monitoring_24, Screen.Stats)
        )
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isSearchBarActive by remember { mutableStateOf(false) }

    val routesWithHiddenNavigationBar = remember {
        setOf(
            Screen.Settings.route,
            Screen.Accounts.route,
            Screen.PlaylistDetail.route,
            Screen.DailyMixScreen.route,
            Screen.RecentlyPlayed.route,
            Screen.GenreDetail.route,
            Screen.AlbumDetail.route,
            Screen.ArtistDetail.route,
            Screen.DJSpace.route,
            Screen.NavBarCrRad.route,
            Screen.About.route,
            Screen.EditTransition.route,
            Screen.Experimental.route,
            Screen.ArtistSettings.route,
            Screen.Equalizer.route,
            Screen.SettingsCategory.route,
            Screen.DelimiterConfig.route,
            Screen.PaletteStyle.route,
            Screen.RecentlyPlayed.route,
            Screen.DeviceCapabilities.route,
            Screen.EasterEgg.route,
            Screen.WordDelimiterConfig.route,
            Screen.LibraryHealth.route,
            Screen.Lofi.route,
            Screen.Focus.route,
            Screen.Sleep.route,
            Screen.Recap.route,
            Screen.TasteMatch.route,
            Screen.Garden.route
        )
    }
    val shouldHideNavigationBar by remember(currentRoute, isSearchBarActive) {
        derivedStateOf {
            if (currentRoute == Screen.Search.route && isSearchBarActive) {
                true
            } else {
                // The bottom bar belongs to the four main tabs only; every other screen
                // (settings, detail pages, Lofi, Party, Cover studio, …) hides it.
                currentRoute?.let { route ->
                    !com.nobodybuilds.listens.presentation.navigation.isMainRootRoute(route) ||
                        routesWithHiddenNavigationBar.any { hiddenRoute -> route == hiddenRoute }
                } ?: false
            }
        }
    }

    val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val navBarCornerRadiusRaw by playerViewModel.navBarCornerRadius.collectAsStateWithLifecycle()
    val navBarCornerRadius = sanitizeNavBarCornerRadius(navBarCornerRadiusRaw)
    val hapticsEnabled by playerViewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val rootView = LocalView.current
    val platformHapticFeedback = LocalHapticFeedback.current
    val appHapticsConfig = remember(hapticsEnabled) {
        AppHapticsConfig(enabled = hapticsEnabled)
    }
    val scopedHapticFeedback = remember(platformHapticFeedback, appHapticsConfig.enabled) {
        // Route Compose/Material haptics through the Nobody Listens engine so they follow the haptics level.
        if (appHapticsConfig.enabled) {
            com.nobodybuilds.listens.presentation.haptics.EngineHapticFeedback(nobodyListensHapticsEngine)
        } else {
            NoOpHapticFeedback
        }
    }

    val systemNavBarInset = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )

    LaunchedEffect(hapticsEnabled, rootView) {
        rootView.isHapticFeedbackEnabled = hapticsEnabled
        rootView.rootView?.isHapticFeedbackEnabled = hapticsEnabled
    }

    val horizontalPadding = if (navBarStyle == NavBarStyle.DEFAULT) {
        if (systemNavBarInset > 30.dp) 14.dp else systemNavBarInset
    } else {
        0.dp
    }
    val animatedBottomBarPadding by animateDpAsState(
        targetValue = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else systemNavBarInset,
        animationSpec = tween(400),
        label = "BottomBarPadding"
    )
    val bottomBarPadding = animatedBottomBarPadding
    val navBarHeight = resolveNavBarSurfaceHeight(navBarStyle, systemNavBarInset, navBarCompactMode)
    val navBarOccupiedHeight by remember(systemNavBarInset, navBarCompactMode) {
        derivedStateOf { resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode) }
    }
    val navBarVisibilityProgress by animateFloatAsState(
        targetValue = if (shouldHideNavigationBar) 0f else 1f,
        animationSpec = tween(
            durationMillis = 220,
            easing = LinearOutSlowInEasing
        ),
        label = "NavBarVisibilityProgress"
    )
    val visibleNavBarOccupiedHeight by remember(navBarOccupiedHeight, navBarVisibilityProgress) {
        derivedStateOf { navBarOccupiedHeight * navBarVisibilityProgress }
    }
    val miniPlayerBottomMargin by remember(systemNavBarInset, visibleNavBarOccupiedHeight) {
        derivedStateOf {
            if (visibleNavBarOccupiedHeight > systemNavBarInset) {
                visibleNavBarOccupiedHeight
            } else {
                systemNavBarInset
            }
        }
    }
    val shouldRenderNavigationBar by remember(shouldHideNavigationBar, navBarVisibilityProgress) {
        derivedStateOf {
            !shouldHideNavigationBar || navBarVisibilityProgress > 0.01f
        }
    }
    val isNavBarEffectivelyHidden by remember(shouldHideNavigationBar, navBarVisibilityProgress) {
        derivedStateOf {
            shouldHideNavigationBar && navBarVisibilityProgress <= 0.01f
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val announcementService = remember { GitHubAnnouncementPropertiesService() }
    val context = LocalContext.current
    var playStoreAnnouncement by remember {
        mutableStateOf(PlayStoreAnnouncementDefaults.localizedTemplate(context))
    }
    var showPlayStoreAnnouncement by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (PlayStoreAnnouncementDefaults.LOCAL_PREVIEW_ENABLED) {
            playStoreAnnouncement = PlayStoreAnnouncementDefaults.hardcodedPreview(activity)
            showPlayStoreAnnouncement = true
            return@LaunchedEffect
        }

        announcementService.fetchPlayStoreAnnouncement()
            .onSuccess { remoteConfig ->
                val resolvedAnnouncement = remoteConfig.toUiModel(activity)
                playStoreAnnouncement = resolvedAnnouncement
                showPlayStoreAnnouncement = resolvedAnnouncement.enabled
            }
            .onFailure { throwable ->
                LogUtils.w(
                    activity,
                    "Remote announcement unavailable. Keeping popup disabled. ${throwable.message ?: ""}",
                )
            }
    }

    LaunchedEffect(userPreferencesRepository) {
        userPreferencesRepository.clearDeprecatedPlayerSheetPreference()
    }

    LaunchedEffect(rootView) { nobodyListensHapticsEngine.attachView(rootView) }

    CompositionLocalProvider(
        LocalAppHapticsConfig provides appHapticsConfig,
        LocalHapticFeedback provides scopedHapticFeedback,
        com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics provides nobodyListensHapticsEngine
    ) {
        AppSidebarDrawer(
            drawerState = drawerState,
            selectedRoute = currentRoute ?: Screen.Home.route,
            onDestinationSelected = { destination ->
                scope.launch { drawerState.close() }
                when (destination) {
                    DrawerDestination.Home -> navController.navigateSafely(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                    DrawerDestination.Equalizer -> navController.navigateSafely(Screen.Equalizer.route)
                    DrawerDestination.Settings -> navController.navigateSafely(Screen.Settings.route)
                    DrawerDestination.Telegram -> {
                        val intent = Intent(activity, com.nobodybuilds.listens.presentation.telegram.auth.TelegramLoginActivity::class.java)
                        activity.startActivity(intent)
                    }
                    else -> {}
                }
            }
    ) {

            Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (shouldRenderNavigationBar) {
                    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction.value
                    val currentSongId by remember {
                        playerViewModel.stablePlayerState
                            .map { it.currentSong?.id }
                            .distinctUntilChanged()
                    }.collectAsStateWithLifecycle(initialValue = null)
                    val showPlayerContentArea = currentSongId != null
                    val navBarElevation = 0.dp // Daylight: no shadows, the bar has a hairline border

                    val playerContentActualBottomRadiusTargetValue by remember(
                        navBarStyle,
                        showPlayerContentArea,
                        playerContentExpansionFraction,
                    ) {
                        derivedStateOf {
                            if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                                return@derivedStateOf lerp(navBarCornerRadius.dp, 26.dp, playerContentExpansionFraction)
                            }

                            if (showPlayerContentArea) {
                                if (playerContentExpansionFraction < 0.2f) {
                                    lerp(12.dp, 26.dp, (playerContentExpansionFraction / 0.2f).coerceIn(0f, 1f))
                                } else {
                                    26.dp
                                }
                            } else {
                                navBarCornerRadius.dp
                            }
                        }
                    }

                    val playerContentActualBottomRadius by animateDpAsState(
                        targetValue = playerContentActualBottomRadiusTargetValue,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        ),
                        label = "PlayerContentBottomRadius"
                    )

                    val navBarHideFraction = if (showPlayerContentArea) playerContentExpansionFraction else 0f
                    val navBarHideFractionClamped = navBarHideFraction.coerceIn(0f, 1f)

                    val animatedNavBarCornerRadius by animateDpAsState(
                        targetValue = navBarCornerRadius.dp,
                        animationSpec = tween(400),
                        label = "NavBarCornerRadius"
                    )

                    val actualShape = remember(playerContentActualBottomRadius, showPlayerContentArea, navBarStyle, animatedNavBarCornerRadius) {
                        val bottomRadius = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else animatedNavBarCornerRadius
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = playerContentActualBottomRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusTR = playerContentActualBottomRadius,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBL = bottomRadius,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBR = bottomRadius,
                            smoothnessAsPercentBL = 60
                        )
                    }

                    var componentHeightPx by remember { mutableStateOf(0) }
                    val density = LocalDensity.current
                    val shadowOverflowPx = remember(navBarElevation, density) {
                        with(density) { (navBarElevation * 8).toPx() }
                    }
                    val bottomBarPaddingPx = remember(bottomBarPadding, density) {
                        with(density) { bottomBarPadding.toPx() }
                    }
                    val animatedTranslationY by remember(
                        navBarHideFractionClamped,
                        componentHeightPx,
                        shadowOverflowPx,
                        bottomBarPaddingPx,
                    ) {
                        derivedStateOf {
                            (componentHeightPx + shadowOverflowPx + bottomBarPaddingPx) * navBarHideFractionClamped
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(visibleNavBarOccupiedHeight)
                            .clipToBounds()
                    ) {
                        val onSearchIconDoubleTap = remember(playerViewModel) {
                            { playerViewModel.onSearchNavIconDoubleTapped() }
                        }

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = bottomBarPadding)
                                .onSizeChanged { componentHeightPx = it.height }
                                .graphicsLayer {
                                    translationY = animatedTranslationY
                                    alpha = 1f
                                }
                                .height(navBarHeight)
                                .padding(horizontal = horizontalPadding),
                            color = NavigationBarDefaults.containerColor,
                            shape = actualShape,
                            shadowElevation = navBarElevation,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            PlayerInternalNavigationBar(
                                navController = navController,
                                navItems = commonNavItems,
                                currentRoute = currentRoute,
                                navBarStyle = navBarStyle,
                                compactMode = navBarCompactMode,
                                bottomBarPadding = bottomBarPadding,
                                onSearchIconDoubleTap = onSearchIconDoubleTap,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            ) { innerPadding ->
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val containerHeight = this.maxHeight
                    val screenHeightPx = remember(containerHeight, density) {
                        with(density) { containerHeight.toPx() }
                    }

                    val showPlayerContentInitially by remember {
                        playerViewModel.stablePlayerState
                            .map { it.currentSong?.id != null }
                            .distinctUntilChanged()
                    }.collectAsStateWithLifecycle(initialValue = false)
                    val routesWithHiddenMiniPlayer = remember { setOf(Screen.NavBarCrRad.route, Screen.Recap.route) }
                    val shouldHideMiniPlayer by remember(currentRoute) {
                        derivedStateOf { currentRoute in routesWithHiddenMiniPlayer }
                    }

                    val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
                    val totalSheetHeightWhenContentCollapsedPx = if (showPlayerContentInitially && !shouldHideMiniPlayer) miniPlayerH else 0f

                    val bottomMargin = miniPlayerBottomMargin

                    val spacerPx = with(density) { MiniPlayerBottomSpacer.toPx() }
                    val bottomMarginPx = with(density) { bottomMargin.toPx() }
                    val sheetCollapsedTargetY = calculatePlayerSheetCollapsedTargetY(
                        containerHeightPx = screenHeightPx,
                        collapsedContentHeightPx = totalSheetHeightWhenContentCollapsedPx,
                        bottomMarginPx = bottomMarginPx,
                        bottomSpacerPx = spacerPx
                    )

                    AppNavigation(
                        playerViewModel = playerViewModel,
                        navController = navController,
                        paddingValues = innerPadding,
                        userPreferencesRepository = userPreferencesRepository,
                        onSearchBarActiveChange = { isSearchBarActive = it },
                        onOpenSidebar = { scope.launch { drawerState.open() } }
                    )

                    UnifiedPlayerSheetV2(
                        playerViewModel = playerViewModel,
                        sheetCollapsedTargetY = sheetCollapsedTargetY,
                        collapsedStateHorizontalPadding = horizontalPadding,
                        hideMiniPlayer = shouldHideMiniPlayer,
                        containerHeight = containerHeight,
                        navController = navController,
                        isNavBarHidden = isNavBarEffectivelyHidden
                    )

                    val dismissUndoBarSlice by remember {
                        playerViewModel.playerUiState
                            .map { state ->
                                DismissUndoBarSlice(
                                    isVisible = state.showDismissUndoBar,
                                    durationMillis = state.undoBarVisibleDuration
                                )
                            }
                            .distinctUntilChanged()
                    }.collectAsStateWithLifecycle(initialValue = DismissUndoBarSlice())
                    val onUndoDismissPlaylist = remember(playerViewModel) {
                        { playerViewModel.undoDismissPlaylist() }
                    }
                    val onCloseDismissUndoBar = remember(playerViewModel) {
                        { playerViewModel.hideDismissUndoBar() }
                    }

                    AnimatedVisibility(
                        visible = dismissUndoBarSlice.isVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = innerPadding.calculateBottomPadding() + MiniPlayerBottomSpacer)
                            .padding(horizontal = horizontalPadding)
                    ) {
                        DismissUndoBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MiniPlayerHeight)
                                .padding(horizontal = 14.dp),
                            onUndo = onUndoDismissPlaylist,
                            onClose = onCloseDismissUndoBar,
                            durationMillis = dismissUndoBarSlice.durationMillis
                        )
                    }

                    // "Turn on screensaver?" when charging + playing + landscape (foreground only).
                    com.nobodybuilds.listens.presentation.screensaver.ScreensaverOfferHost(
                        playerViewModel = playerViewModel,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    if (showPlayStoreAnnouncement) {
                        PlayStoreAnnouncementDialog(
                            announcement = playStoreAnnouncement,
                            onDismiss = { showPlayStoreAnnouncement = false },
                            onOpenPlayStore = { url ->
                                showPlayStoreAnnouncement = false
                                openExternalUrl(url)
                            }
                        )
                    }
                }
            }
        }
    }

    Trace.endSection()
}

private fun PlayStoreAnnouncementRemoteConfig.toUiModel(context: Context): PlayStoreAnnouncementUiModel {
    val fallback = PlayStoreAnnouncementDefaults.localizedTemplate(context)
    return fallback.copy(
        enabled = enabled,
        playStoreUrl = playStoreUrl ?: fallback.playStoreUrl,
        title = title ?: fallback.title,
        body = body ?: fallback.body,
        primaryActionLabel = primaryActionLabel ?: fallback.primaryActionLabel,
        dismissActionLabel = dismissActionLabel ?: fallback.dismissActionLabel,
        linkPendingMessage = linkPendingMessage ?: fallback.linkPendingMessage,
    )
}
