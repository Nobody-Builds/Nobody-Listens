package com.nobodybuilds.listens.presentation.screens

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import com.nobodybuilds.listens.presentation.components.rotaryInput
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.volumeRotaryBehavior
import com.nobodybuilds.listens.presentation.components.WatchScalingColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.nobodybuilds.listens.presentation.components.AlwaysOnScalingPositionIndicator
import com.nobodybuilds.listens.presentation.components.CurvedVolumeIndicator
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.rememberWearScreen
import com.nobodybuilds.listens.presentation.theme.WearTitleFont
import com.nobodybuilds.listens.presentation.theme.surfaceContainerColor
import com.nobodybuilds.listens.shared.WearPlayerState
import com.nobodybuilds.listens.shared.WearVolumeState
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
@OptIn(ExperimentalWearFoundationApi::class)
internal fun MainPlayerPage(
    state: WearPlayerState,
    isPhoneConnected: Boolean,
    isWatchOutputSelected: Boolean = false,
    activeVolumeState: WearVolumeState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSetActiveVolume: (Int) -> Unit,
    rotaryEnabled: Boolean,
    activeOutputRouteType: String,
    onVolumeClick: () -> Unit,
    onOutputClick: () -> Unit,
    onMoreClick: () -> Unit,
    onQueueClick: () -> Unit,
    onQueueShortcutRevealChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWearPalette.current
    val volumeEnabled = isPhoneConnected || isWatchOutputSelected
    val columnState = rememberResponsiveColumnState(
        contentPadding = {
            PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 2.dp,
                bottom = 20.dp,
            )
        },
    )

    val livePositionMs by rememberLivePositionMs(state)
    val trackProgressTarget = if (state.totalDurationMs > 0L) {
        (livePositionMs.toFloat() / state.totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackProgress by animateFloatAsState(
        targetValue = trackProgressTarget,
        animationSpec = tween(durationMillis = 280),
        label = "trackProgress",
    )
    val queueShortcutRevealTarget by remember(columnState.state) {
        derivedStateOf {
            val layoutInfo = columnState.state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 1) return@derivedStateOf 0f
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0f

            val firstVisibleIndex = visibleItems.minOf { it.index }
            val lastVisibleIndex = visibleItems.maxOf { it.index }
            val visibleCount = (lastVisibleIndex - firstVisibleIndex + 1).coerceAtLeast(1)
            val maxFirstVisibleIndex = (totalItems - visibleCount).coerceAtLeast(0)
            if (maxFirstVisibleIndex == 0) return@derivedStateOf 0f

            if (lastVisibleIndex >= totalItems - 1) {
                1f
            } else {
                (firstVisibleIndex.toFloat() / maxFirstVisibleIndex.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val queueShortcutReveal by animateFloatAsState(
        targetValue = queueShortcutRevealTarget,
        animationSpec = tween(durationMillis = 220),
        label = "queueShortcutReveal",
    )
    val rotaryVolumeUiState = remember(activeVolumeState.level, activeVolumeState.max) {
        VolumeUiState(
            current = activeVolumeState.level.coerceAtLeast(0),
            max = activeVolumeState.max.coerceAtLeast(0),
            min = 0,
        )
    }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var volumeOverlayInteractionTick by remember { mutableIntStateOf(0) }
    val volumeProgress = if (activeVolumeState.max > 0) {
        (activeVolumeState.level.toFloat() / activeVolumeState.max.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(rotaryEnabled) {
        if (!rotaryEnabled) {
            showVolumeOverlay = false
        }
    }
    LaunchedEffect(volumeOverlayInteractionTick, rotaryEnabled) {
        if (!rotaryEnabled || volumeOverlayInteractionTick == 0) return@LaunchedEffect
        showVolumeOverlay = true
        delay(2_000L)
        showVolumeOverlay = false
    }
    LaunchedEffect(queueShortcutReveal) {
        onQueueShortcutRevealChanged(queueShortcutReveal)
    }

    val rotaryModifier = if (rotaryEnabled && volumeEnabled && activeVolumeState.max > 0) {
        // The bezel is volume here, but only while this page is on show (see rotaryInput).
        Modifier.rotaryInput(
            behavior = volumeRotaryBehavior(
                volumeUiStateProvider = { rotaryVolumeUiState },
                onRotaryVolumeInput = { newVolume ->
                    showVolumeOverlay = true
                    volumeOverlayInteractionTick++
                    onSetActiveVolume(newVolume)
                },
            ),
            active = true,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(rotaryModifier)
    ) {
        WatchScalingColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            columnState = columnState,
            rotaryActive = false, // the bezel sets the volume on this page
        ) {
            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                HeaderBlock(
                    state = state,
                    isPhoneConnected = isPhoneConnected,
                    isWatchOutputSelected = isWatchOutputSelected,
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                MainControlsRow(
                    isPlaying = state.isPlaying,
                    isEmpty = state.isEmpty,
                    enabled = if (isWatchOutputSelected) !state.isEmpty else isPhoneConnected,
                    trackProgress = trackProgress,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
            }

            item {
                SecondaryControlsRow(
                    volumeEnabled = volumeEnabled,
                    deviceEnabled = volumeEnabled,
                    deviceRouteType = if (isWatchOutputSelected) {
                        com.nobodybuilds.listens.shared.WearVolumeState.ROUTE_TYPE_WATCH
                    } else {
                        activeOutputRouteType
                    },
                    moreEnabled = !state.isEmpty,
                    onVolumeClick = onVolumeClick,
                    onOutputClick = onOutputClick,
                    onMoreClick = onMoreClick,
                    deviceActiveColor = palette.shuffleActive,
                )
            }

            item { Spacer(modifier = Modifier.height(50.dp)) }
        }

        PlayerCrownVolumeIndicator(
            progress = volumeProgress,
            visible = showVolumeOverlay,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
        )

        BottomQueueShortcut(
            revealProgress = queueShortcutReveal,
            enabled = isPhoneConnected,
            onClick = onQueueClick,
            modifier = Modifier
                .align(Alignment.BottomCenter),
        )

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun rememberLivePositionMs(state: WearPlayerState): androidx.compose.runtime.State<Long> {
    val safeDuration = state.totalDurationMs.coerceAtLeast(0L)
    val safeAnchorPosition = state.currentPositionMs.coerceIn(0L, safeDuration)
    val positionKey = remember(
        state.songId,
        safeAnchorPosition,
        safeDuration,
        state.isPlaying,
    ) {
        "${state.songId}|$safeAnchorPosition|$safeDuration|${state.isPlaying}"
    }
    return produceState(
        initialValue = safeAnchorPosition,
        key1 = positionKey,
    ) {
        value = safeAnchorPosition
        if (!state.isPlaying || safeDuration <= 0L) {
            return@produceState
        }

        val startElapsedRealtime = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startElapsedRealtime
            val next = (safeAnchorPosition + elapsed).coerceIn(0L, safeDuration)
            value = next
            if (next >= safeDuration) break
            delay(250L)
        }
    }
}

@Composable
private fun PlayerCrownVolumeIndicator(
    progress: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWearPalette.current
    val overlayAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 180 else 220),
        label = "playerVolumeOverlayAlpha",
    )
    val overlayScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = tween(durationMillis = if (visible) 180 else 220),
        label = "playerVolumeOverlayScale",
    )

    if (overlayAlpha <= 0.01f && !visible) return

    Box(
        modifier = modifier.graphicsLayer {
            alpha = overlayAlpha
            scaleX = overlayScale
            scaleY = overlayScale
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
    ) {
        CurvedVolumeIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            startAngle = 144f,
            sweepAngle = 72f,
            strokeWidth = 4.dp,
            inset = 10.dp,
            trackColor = palette.surfaceContainerColor().copy(alpha = 0.42f),
            progressColor = palette.controlContainer.copy(alpha = 0.94f),
        )
    }
}

@Composable
private fun HeaderBlock(
    state: WearPlayerState,
    isPhoneConnected: Boolean,
    isWatchOutputSelected: Boolean = false,
) {
    val palette = LocalWearPalette.current
    val screen = rememberWearScreen()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = screen.contentInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.songTitle.ifEmpty { "Nothing playing" },
            style = MaterialTheme.typography.body1,
            fontFamily = WearTitleFont,
            fontWeight = FontWeight.ExtraBold, // Gilroy's open weights: below 600 would be Light
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = when {
                isWatchOutputSelected -> {
                    val artistAlbum = buildList {
                        if (state.artistName.isNotBlank()) add(state.artistName)
                        if (state.albumName.isNotBlank()) add(state.albumName)
                    }.joinToString(" · ")
                    if (artistAlbum.isNotBlank()) artistAlbum else "On watch"
                }
                !isPhoneConnected -> "No phone"
                state.artistName.isNotEmpty() -> state.artistName
                state.isEmpty -> "Pick a song"
                else -> "Unknown artist"
            },
            style = MaterialTheme.typography.body1,
            color = when {
                isWatchOutputSelected -> palette.textSecondary
                !isPhoneConnected -> palette.textError
                else -> palette.textSecondary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

//        if (isWatchOutputSelected) {
//            Text(
//                text = "On watch",
//                style = MaterialTheme.typography.caption3,
//                color = palette.shuffleActive.copy(alpha = 0.85f),
//                textAlign = TextAlign.Center,
//                modifier = Modifier.fillMaxWidth(),
//            )
//        }
    }
}
