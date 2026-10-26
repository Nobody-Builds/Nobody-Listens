package com.nobodybuilds.listens.presentation.components.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics
import com.nobodybuilds.listens.presentation.components.TimerOptionsBottomSheet
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Bedtime
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.presentation.components.LocalMaterialTheme
import com.nobodybuilds.listens.presentation.viewmodel.PlayerSheetState
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.nobodybuilds.listens.presentation.components.ToggleSegmentButton

@Composable
internal fun FullPlayerControlsSection(
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    placeholderColor: Color,
    placeholderOnColor: Color,
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    transportPlayPauseColors: TransportButtonColors,
    transportSkipColors: TransportButtonColors,
    isShuffleEnabledProvider: () -> Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatModeProvider: () -> Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    sleepTimerSlot: @Composable (Modifier) -> Unit
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayControls

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().height(CONTROLS_SECTION_HEIGHT),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.42f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(CONTROLS_SECTION_HEIGHT))
            } else {
                ControlsPlaceholder(placeholderColor, placeholderOnColor)
            }
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact transport: 48dp prev / next, 64dp play-pause.
            CompactPlaybackControls(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                isPlayingProvider = isPlayingProvider,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                playSize = 64.dp,
                skipSize = 48.dp,
                colorPlayPause = transportPlayPauseColors.container,
                tintPlayPauseIcon = transportPlayPauseColors.content,
                colorSkip = transportSkipColors.container,
                tintSkipIcon = transportSkipColors.content
            )

            Spacer(modifier = Modifier.height(14.dp))

            BottomToggleRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOGGLE_ROW_HEIGHT)
                    .padding(horizontal = 26.dp, vertical = 0.dp),
                sleepTimerSlot = sleepTimerSlot,
                isShuffleEnabled = isShuffleEnabledProvider(),
                isShuffleTransitionInProgress = shuffleTransitionInProgress,
                repeatMode = repeatModeProvider(),
                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle
            )
        }
    }
}

internal data class TransportButtonColors(
    val container: Color,
    val content: Color
)

internal fun expressivePlayPauseButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.tertiaryFixedDim,
        content = colorScheme.onTertiaryFixed
    )
}

internal fun expressiveSkipButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.secondaryFixedDim,
        content = colorScheme.onSecondaryFixed
    )
}

private val CONTROLS_SECTION_HEIGHT = 156.dp

internal val TOGGLE_ROW_HEIGHT = 58.dp

@Composable
private fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    isShuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    sleepTimerSlot: @Composable (Modifier) -> Unit
) {
    val isFavorite = isFavoriteProvider()
    val haptics = LocalNobodyListensHaptics.current
    val rowCorners = 60.dp
    val inactiveBg = LocalMaterialTheme.current.onSurface.copy(alpha = 0.07f)
    val inactiveContentColor = LocalMaterialTheme.current.onSurface
    val rowShape = AbsoluteSmoothCornerShape(
        cornerRadiusBL = rowCorners,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = rowCorners,
        smoothnessAsPercentBL = 60,
        cornerRadiusTL = rowCorners,
        smoothnessAsPercentBR = 60,
        cornerRadiusTR = rowCorners,
        smoothnessAsPercentTL = 60
    )

    Box(
        modifier = modifier.background(
            color = LocalMaterialTheme.current.surfaceContainerLowest.copy(alpha = 0.7f),
            shape = rowShape
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(rowShape)
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = isShuffleEnabled,
                enabled = !isShuffleTransitionInProgress,
                activeColor = LocalMaterialTheme.current.primaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onPrimaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = {
                    haptics.toggle(!isShuffleEnabled)
                    onShuffleToggle()
                },
                iconId = R.drawable.rounded_shuffle_24,
                contentDesc = "Shuffle"
            )
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_24
                Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_24
                else -> R.drawable.rounded_repeat_24
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = LocalMaterialTheme.current.secondaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onSecondaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = {
                    // Off -> all -> one -> off
                    when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> haptics.toggleOn()
                        Player.REPEAT_MODE_ALL -> haptics.selection()
                        else -> haptics.toggleOff()
                    }
                    onRepeatToggle()
                },
                iconId = repeatIcon,
                contentDesc = "Repeat"
            )
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = LocalMaterialTheme.current.tertiaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onTertiaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = {
                    haptics.like(!isFavorite)
                    onFavoriteToggle()
                },
                iconId = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24,
                contentDesc = "Favourite"
            )
            sleepTimerSlot(commonModifier)
        }
    }
}

/**
 * Sleep timer segment for the now playing toggle row. Shows the moon icon; when a timer is
 * running it takes the accent fill and a small "24m" / "End" / "×3" label.
 * Only this small composable observes the timer flows.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerSleepTimerSegment(
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalNobodyListensHaptics.current
    val timerDisplay by playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle()
    val endTimeMillis by playerViewModel.sleepTimerEndTimeMillis.collectAsStateWithLifecycle()
    val isEndOfTrack by playerViewModel.isEndOfTrackTimerActive.collectAsStateWithLifecycle()
    val playCount by playerViewModel.playCount.collectAsStateWithLifecycle()
    val isCounterActive = playCount > 1f
    val isActive = timerDisplay != null || isCounterActive

    // Remaining minutes: recomputed once per minute only while a duration timer is running
    // and this segment is on screen.
    var remainingMinutes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(endTimeMillis) {
        val end = endTimeMillis
        if (end == null) {
            remainingMinutes = null
            return@LaunchedEffect
        }
        while (true) {
            val remainingMs = end - System.currentTimeMillis()
            if (remainingMs <= 0L) {
                remainingMinutes = null
                break
            }
            remainingMinutes = (remainingMs + 59_999L) / 60_000L
            kotlinx.coroutines.delay((remainingMs % 60_000L).coerceAtLeast(1_000L))
        }
    }

    val label: String? = when {
        isEndOfTrack -> "End"
        remainingMinutes != null -> "${remainingMinutes}m"
        isCounterActive -> "×${playCount.toInt()}"
        else -> null
    }

    val activeColor = LocalMaterialTheme.current.tertiary
    val activeContentColor = LocalMaterialTheme.current.onTertiary
    val inactiveBg = LocalMaterialTheme.current.onSurface.copy(alpha = 0.07f)
    val inactiveContentColor = LocalMaterialTheme.current.onSurface
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveBg,
        animationSpec = tween(durationMillis = 250),
        label = "sleepSegmentBg"
    )
    val corner by animateDpAsState(
        targetValue = if (isActive) 60.dp else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "sleepSegmentCorner"
    )
    val contentColor = if (isActive) activeContentColor else inactiveContentColor

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(corner))
            .background(bgColor)
            .clickable {
                haptics.selection()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Rounded.Bedtime,
                contentDescription = if (isActive) "Sleep timer on" else "Sleep timer",
                tint = contentColor,
                modifier = Modifier.size(if (label != null) 18.dp else 22.dp)
            )
            if (label != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/** The existing sleep timer sheet, wired to the player view model. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerSleepTimerSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val timerDisplay by playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle()
    val durationMinutes by playerViewModel.activeTimerDurationMinutes.collectAsStateWithLifecycle()
    val playCount by playerViewModel.playCount.collectAsStateWithLifecycle()
    val isEndOfTrack by playerViewModel.isEndOfTrackTimerActive.collectAsStateWithLifecycle()

    TimerOptionsBottomSheet(
        onPlayCounter = playerViewModel::playCounted,
        activeTimerValueDisplay = timerDisplay,
        activeTimerDurationMinutes = durationMinutes,
        playCount = playCount,
        isEndOfTrackTimerActive = isEndOfTrack,
        onDismiss = onDismiss,
        onCancelCountedPlay = playerViewModel::cancelCountedPlay,
        onSetPredefinedTimer = { minutes -> playerViewModel.setSleepTimer(minutes) },
        onSetEndOfTrackTimer = { enable -> playerViewModel.setEndOfTrackTimer(enable) },
        onOpenCustomTimePicker = {},
        onCancelTimer = { playerViewModel.cancelSleepTimer() }
    )
}
