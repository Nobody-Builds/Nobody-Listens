package com.nobodybuilds.listens.presentation.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.HorizontalPageIndicator as M3HorizontalPageIndicator
import com.nobodybuilds.listens.presentation.components.WearTopTimeText
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.radialBackgroundBrush
import com.nobodybuilds.listens.presentation.viewmodel.WearPlayerViewModel
import com.nobodybuilds.listens.shared.WearPlayerState
import com.nobodybuilds.listens.shared.WearVolumeState
import kotlin.math.abs

@Composable
fun PlayerScreen(
    onBrowseCategoryClick: (browseType: String, title: String) -> Unit = { _, _ -> },
    onVolumeClick: () -> Unit = {},
    onOutputClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    viewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsState()
    val isPhoneConnected by viewModel.isPhoneConnected.collectAsState()
    val isWatchOutputSelected by viewModel.isWatchOutputSelected.collectAsState()
    val activeOutputRouteType by viewModel.activeOutputRouteType.collectAsState()
    val activeVolumeState by viewModel.activeVolumeState.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()

    PlayerContent(
        state = state,
        albumArt = albumArt,
        isPhoneConnected = isPhoneConnected,
        isWatchOutputSelected = isWatchOutputSelected,
        activeVolumeState = activeVolumeState,
        onTogglePlayPause = viewModel::togglePlayPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onSetActiveVolume = viewModel::setActiveVolume,
        activeOutputRouteType = activeOutputRouteType,
        onBrowseCategoryClick = onBrowseCategoryClick,
        onVolumeClick = onVolumeClick,
        onOutputClick = onOutputClick,
        onMoreClick = onMoreClick,
        onQueueClick = onQueueClick,
    )
}

@Composable
private fun PlayerContent(
    state: WearPlayerState,
    albumArt: Bitmap?,
    isPhoneConnected: Boolean,
    isWatchOutputSelected: Boolean = false,
    activeVolumeState: WearVolumeState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSetActiveVolume: (Int) -> Unit,
    activeOutputRouteType: String,
    onBrowseCategoryClick: (browseType: String, title: String) -> Unit,
    onVolumeClick: () -> Unit,
    onOutputClick: () -> Unit,
    onMoreClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    // Memoize: radialGradient allocates Shader inputs on every call. PlayerContent
    // recomposes whenever the play-button ring animation ticks, so without this
    // we'd churn the GC for nothing.
    val background = remember(palette) { palette.radialBackgroundBrush() }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var mainPageQueueReveal by remember { mutableFloatStateOf(0f) }
    var albumRevealProgress by remember { mutableFloatStateOf(0f) }
    val isMainPlayerPage = pagerState.currentPage == 0
    val isMainPlayerOverlayVisible =
        mainPageQueueReveal > 0.05f || albumRevealProgress > 0.05f
    val showPageIndicator =
        !isMainPlayerPage || (pagerState.isScrollInProgress && !isMainPlayerOverlayVisible)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        if (albumArt != null && !state.isEmpty) {
            PlayerArtworkBackdrop(albumArt = albumArt)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> {
                    PlayerMainPageHost(
                        state = state,
                        albumArt = albumArt,
                        isCurrentPage = pagerState.currentPage == 0,
                        isPhoneConnected = isPhoneConnected,
                        isWatchOutputSelected = isWatchOutputSelected,
                        activeVolumeState = activeVolumeState,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSetActiveVolume = onSetActiveVolume,
                        activeOutputRouteType = activeOutputRouteType,
                        onVolumeClick = onVolumeClick,
                        onOutputClick = onOutputClick,
                        onMoreClick = onMoreClick,
                        onQueueClick = onQueueClick,
                        onQueueShortcutRevealChanged = { mainPageQueueReveal = it },
                        onAlbumRevealProgressChanged = { albumRevealProgress = it },
                    )
                }

                else -> {
                    BrowseScreen(
                        onCategoryClick = onBrowseCategoryClick,
                        rotaryActive = pagerState.currentPage == 1,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showPageIndicator,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 240)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(6f),
        ) {
            M3HorizontalPageIndicator(
                pagerState = pagerState,
                modifier = Modifier
                    .padding(bottom = 10.dp),
                selectedColor = palette.textPrimary,
                unselectedColor = palette.textPrimary.copy(alpha = 0.52f),
                backgroundColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun PlayerMainPageHost(
    state: WearPlayerState,
    albumArt: Bitmap?,
    isCurrentPage: Boolean,
    isPhoneConnected: Boolean,
    isWatchOutputSelected: Boolean,
    activeVolumeState: WearVolumeState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSetActiveVolume: (Int) -> Unit,
    activeOutputRouteType: String,
    onVolumeClick: () -> Unit,
    onOutputClick: () -> Unit,
    onMoreClick: () -> Unit,
    onQueueClick: () -> Unit,
    onQueueShortcutRevealChanged: (Float) -> Unit,
    onAlbumRevealProgressChanged: (Float) -> Unit,
) {
    val palette = LocalWearPalette.current
    val density = LocalDensity.current
    val canShowAlbumArt = !state.isEmpty
    var isDraggingAlbumReveal by remember { mutableStateOf(false) }
    var rawAlbumRevealProgress by remember { mutableFloatStateOf(0f) }
    val albumRevealProgress by animateFloatAsState(
        targetValue = rawAlbumRevealProgress,
        animationSpec = if (isDraggingAlbumReveal) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            )
        },
        label = "albumRevealProgress",
    )

    LaunchedEffect(canShowAlbumArt, isCurrentPage) {
        if (!canShowAlbumArt || !isCurrentPage) {
            isDraggingAlbumReveal = false
            rawAlbumRevealProgress = 0f
        }
    }

    SideEffect {
        onAlbumRevealProgressChanged(albumRevealProgress)
    }

    BackHandler(enabled = albumRevealProgress > 0.01f) {
        isDraggingAlbumReveal = false
        rawAlbumRevealProgress = 0f
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
        val revealDistancePx = heightPx * 0.62f
        val overlayOffsetPx = -heightPx * (1f - albumRevealProgress)
        val mainTranslationY = -heightPx * 0.08f * albumRevealProgress
        val mainScale = 1f - (0.04f * albumRevealProgress)
        val mainAlpha = 1f - (0.26f * albumRevealProgress)
        val timeAlpha = (1f - (albumRevealProgress * 1.6f)).coerceIn(0f, 1f)

        fun settleAlbumReveal(progress: Float, velocityY: Float = 0f) {
            isDraggingAlbumReveal = false
            rawAlbumRevealProgress = if (shouldOpenAlbumArtReveal(progress, velocityY)) 1f else 0f
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainPlayerPage(
                state = state,
                isPhoneConnected = isPhoneConnected,
                isWatchOutputSelected = isWatchOutputSelected,
                activeVolumeState = activeVolumeState,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSetActiveVolume = onSetActiveVolume,
                rotaryEnabled = isCurrentPage && albumRevealProgress <= 0.01f,
                activeOutputRouteType = activeOutputRouteType,
                onVolumeClick = onVolumeClick,
                onOutputClick = onOutputClick,
                onMoreClick = onMoreClick,
                onQueueClick = onQueueClick,
                onQueueShortcutRevealChanged = onQueueShortcutRevealChanged,
                modifier = Modifier.graphicsLayer {
                    translationY = mainTranslationY
                    scaleX = mainScale
                    scaleY = mainScale
                    alpha = mainAlpha
                },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(42.dp)
                    .albumArtRevealDragGesture(
                        enabled = canShowAlbumArt && isCurrentPage,
                        currentProgress = albumRevealProgress,
                        revealDistancePx = revealDistancePx,
                        onDragStart = {
                            isDraggingAlbumReveal = true
                            rawAlbumRevealProgress = albumRevealProgress
                        },
                        onProgressChange = { rawAlbumRevealProgress = it },
                        onRelease = { progress, velocityY ->
                            settleAlbumReveal(progress, velocityY)
                        },
                    )
                    .clickable(enabled = canShowAlbumArt && isCurrentPage) {
                        isDraggingAlbumReveal = false
                        rawAlbumRevealProgress = 1f
                    }
                    .zIndex(5f),
            )

            WearTopTimeText(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = timeAlpha }
                    .zIndex(6f),
                color = palette.textPrimary,
            )

            if (canShowAlbumArt || albumRevealProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = overlayOffsetPx
                            alpha = albumRevealProgress
                        }
                        .albumArtRevealDragGesture(
                            enabled = isCurrentPage,
                            currentProgress = albumRevealProgress,
                            revealDistancePx = revealDistancePx,
                            onDragStart = {
                                isDraggingAlbumReveal = true
                                rawAlbumRevealProgress = albumRevealProgress
                            },
                            onProgressChange = { rawAlbumRevealProgress = it },
                            onRelease = { progress, velocityY ->
                                settleAlbumReveal(progress, velocityY)
                            },
                        )
                        .zIndex(8f),
                ) {
                    AlbumArtPage(
                        state = state,
                        albumArt = albumArt,
                        onTap = {
                            isDraggingAlbumReveal = false
                            rawAlbumRevealProgress = 0f
                        },
                    )
                }
            }
        }
    }
}

private fun Modifier.albumArtRevealDragGesture(
    enabled: Boolean,
    currentProgress: Float,
    revealDistancePx: Float,
    onDragStart: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onRelease: (Float, Float) -> Unit,
): Modifier {
    if (!enabled) return this

    return this.pointerInput(enabled, currentProgress, revealDistancePx) {
        val velocityTracker = VelocityTracker()
        var dragProgress = currentProgress
        var openingFromClosed = false
        detectVerticalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                dragProgress = currentProgress
                openingFromClosed = currentProgress <= 0.01f
                onDragStart()
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val progressDelta = if (openingFromClosed) {
                    abs(dragAmount) / revealDistancePx
                } else {
                    -dragAmount / revealDistancePx
                }
                dragProgress = (dragProgress + progressDelta).coerceIn(0f, 1f)
                onProgressChange(dragProgress)
            },
            onDragEnd = {
                onRelease(dragProgress, velocityTracker.calculateVelocity().y)
            },
            onDragCancel = {
                onRelease(dragProgress, 0f)
            },
        )
    }
}

private fun shouldOpenAlbumArtReveal(
    progress: Float,
    velocityY: Float,
): Boolean = when {
    velocityY <= -900f -> true
    velocityY >= 900f -> false
    progress >= 0.34f -> true
    else -> false
}
