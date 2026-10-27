package com.nobodybuilds.listens.presentation.components

import com.nobodybuilds.listens.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.presentation.components.subcomps.PlayerSeekBar
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.AnimationVector1D

@Composable
private fun LyricsPlaybackSeekBar(
    playbackPositionFlow: StateFlow<Long>,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    totalDuration: Long,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long?) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()

    PlayerSeekBar(
        backgroundColor = backgroundColor,
        onBackgroundColor = onBackgroundColor,
        primaryColor = accentColor,
        currentPosition = playbackPosition,
        totalDuration = totalDuration,
        onSeek = onSeekTo,
        onSeekPreview = onSeekPreviewChange,
        isPlaying = isPlaying,
        modifier = modifier
    )
}

/** Sync offset row, play/pause, seek bar and the floating toolbar under the lyrics. */
@Composable
internal fun LyricsSheetControls(
    bottomPadding: Dp,
    onInteraction: () -> Unit,
    syncControlsVisible: Boolean,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    sheetColors: LyricsSheetColors,
    containerColor: Color,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    playPauseColor: Color,
    onPlayPauseColor: Color,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    playbackPositionFlow: StateFlow<Long>,
    totalDuration: Long,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long?) -> Unit,
    showSyncedLyrics: Boolean?,
    hasSyncedLyrics: Boolean,
    onShowSyncedLyricsChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onMoreClick: () -> Unit,
    backProgressProvider: () -> Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(bottom = bottomPadding + 10.dp, end = 16.dp, start = 16.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Reset timer on any touch down or move in this area
                        if (event.changes.any { it.pressed }) {
                             onInteraction()
                        }
                    }
                }
            }
    ) {
                    AnimatedVisibility(
        visible = syncControlsVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        LyricsSyncControls(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            offsetMillis = lyricsSyncOffset,
            onOffsetChange = onLyricsSyncOffsetChange,
            backgroundColor = backgroundColor,
            accentColor = sheetColors.syncButtonContainer,
            onAccentColor = sheetColors.syncButtonContent,
            onBackgroundColor = onBackgroundColor
        )
    }

    // Playback Controls Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Play/Pause Button (Smaller)
        val playPauseCornerRadius by animateDpAsState(
            targetValue = if (isPlaying) 18.dp else 50.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "playPauseShape"
        )

        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(playPauseCornerRadius))
                .background(playPauseColor)
                .clickable {
                    com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().playPause()
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                label = "playPauseIconAnimation"
            ) { playing ->
                if (playing) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause",
                        tint = onPlayPauseColor
                    )
                } else {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play),
                        tint = onPlayPauseColor
                    )
                }
            }
        }

        // Progress Bar
        LyricsPlaybackSeekBar(
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            playbackPositionFlow = playbackPositionFlow,
            backgroundColor = backgroundColor,
            onBackgroundColor = onBackgroundColor,
            accentColor = accentColor,
            totalDuration = totalDuration,
            onSeekTo = onSeekTo,
            onSeekPreviewChange = onSeekPreviewChange,
            isPlaying = isPlaying
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Floating Toolbar
    LyricsFloatingToolbar(
        modifier = Modifier.padding(horizontal = 0.dp),
        showSyncedLyrics = showSyncedLyrics,
        hasSyncedLyrics = hasSyncedLyrics,
        onShowSyncedLyricsChange = onShowSyncedLyricsChange,
        onNavigateBack = {
            onNavigateBack()
        },
        onMoreClick = onMoreClick,
        backgroundColor = backgroundColor,
        onBackgroundColor = onBackgroundColor,
        accentColor = accentColor,
        onAccentColor = onAccentColor,
        // Pass progress so the back button animates with the gesture (draw-phase).
        backProgressProvider = backProgressProvider,
    )
 }
}

/** Small button that brings the controls back in immersive mode. */
@Composable
internal fun LyricsShowControlsButton(
    visible: Boolean,
    accentColor: Color,
    onAccentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        FilledIconButton(
            onClick = { onClick() },
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accentColor,
                contentColor = onAccentColor
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Show Controls"
            )
        }
    }
}

/** Arrow that slides in from the edge while swiping the lyrics to skip. */
@Composable
internal fun LyricsSwipeFeedbackOverlay(
    isNext: Boolean,
    swipeProgress: Animatable<Float, AnimationVector1D>,
    accentColor: Color,
    onAccentColor: Color,
    modifier: Modifier = Modifier
) {
    val icon = if (isNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious

    Box(
        modifier = modifier
            .size(100.dp) // Base size
            .padding(
                start = if(isNext) 0.dp else 6.dp,
                end = if(isNext) 6.dp else 0.dp
            )
            .graphicsLayer {
                 val widthPx = size.width
                 val initialOffset = if(isNext) widthPx else -widthPx
                 translationX = initialOffset * (1f - swipeProgress.value)

                 scaleX = 0.8f + (swipeProgress.value * 0.2f)
                 scaleY = 0.8f + (swipeProgress.value * 0.2f)
            }
            .background(
                 color = accentColor, // No alpha modulation
                 shape = RoundedCornerShape(
                     topStart = if(isNext) 360.dp else 8.dp,
                     bottomStart = if(isNext) 360.dp else 8.dp,
                     topEnd = if(isNext) 8.dp else 360.dp,
                     bottomEnd = if(isNext) 8.dp else 360.dp
                 )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = onAccentColor
        )
    }
}
