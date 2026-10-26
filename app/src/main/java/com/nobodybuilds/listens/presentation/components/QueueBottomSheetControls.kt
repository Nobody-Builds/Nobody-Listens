package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.viewmodel.PlayerUiState
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbChip
import com.nobodybuilds.listens.presentation.nb.NbDialog
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbSheetHandle
import com.nobodybuilds.listens.presentation.nb.NbToast
import com.nobodybuilds.listens.presentation.nb.NbType

private data class QueueUndoBarProjection(
    val isVisible: Boolean = false,
    val removedSongTitle: String = ""
)

private fun PlayerUiState.toQueueUndoBarProjection(): QueueUndoBarProjection =
    QueueUndoBarProjection(
        isVisible = showQueueItemUndoBar,
        removedSongTitle = lastRemovedQueueSong?.title.orEmpty()
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueToolbarMenuButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current

    Surface(
        modifier = modifier
            .widthIn(min = 184.dp, max = 260.dp)
            .heightIn(min = 48.dp)
            .wrapContentWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                haptic.tap()
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 8.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}

@Composable
internal fun QueueHeaderSection(
    isPlaying: Boolean,
    queueSourceName: String,
    queueCount: Int,
    topPadding: Dp,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClearClick: () -> Unit,
    onSaveClick: () -> Unit,
    colorScheme: ColorScheme? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding - 6.dp, bottom = 8.dp)
    ) {
        NbSheetHandle(Modifier.align(Alignment.CenterHorizontally))
        QueueHeader(
            queueSourceName = queueSourceName,
            queueCount = queueCount,
            onClearClick = onClearClick,
            onSaveClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp)
        )
    }
}

@Composable
private fun QueueHeader(
    queueSourceName: String,
    queueCount: Int,
    onClearClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Up next",
                style = NbType.h2,
                color = MaterialTheme.colorScheme.onSurface
            )
            val countText = when {
                queueCount <= 0 -> stringResource(R.string.presentation_batch_e_queue_subtitle_empty)
                else -> pluralStringResource(
                    R.plurals.presentation_batch_e_queue_tracks_lined_up,
                    queueCount,
                    queueCount
                )
            }
            val source = queueSourceName.ifBlank { stringResource(R.string.presentation_batch_e_queue_source_fallback) }
            Text(
                text = "$countText · $source",
                style = NbType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (queueCount > 0) {
            NbButton(
                text = "Save",
                onClick = onSaveClick,
                style = NbButtonStyle.Quiet,
                size = NbButtonSize.Small
            )
            NbButton(
                text = stringResource(R.string.presentation_batch_b_clear),
                onClick = onClearClick,
                style = NbButtonStyle.Quiet,
                size = NbButtonSize.Small
            )
        }
    }
}

@Composable
internal fun QueueControlsToolbar(
    isShuffleOn: Boolean,
    repeatMode: Int,
    isTimerActive: androidx.compose.runtime.State<Boolean>,
    timerLabel: String?,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        NbDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NbChip(
                text = stringResource(R.string.shortcut_shuffle_short),
                selected = isShuffleOn,
                onClick = onToggleShuffle,
                icon = rememberVectorPainter(Icons.Rounded.Shuffle)
            )
            NbChip(
                text = if (repeatMode == Player.REPEAT_MODE_ONE) "Repeat one" else "Repeat",
                selected = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = onToggleRepeat,
                icon = rememberVectorPainter(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                )
            )
            Spacer(Modifier.weight(1f))
            NbChip(
                text = if (isTimerActive.value) timerLabel ?: "Timer" else "Timer",
                selected = isTimerActive.value,
                onClick = onTimerClick,
                icon = rememberVectorPainter(Icons.Rounded.Timer)
            )
        }
    }
}

/** Drags the whole queue sheet from its header and toolbar. */
internal fun Modifier.queueSheetDirectDrag(
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit
): Modifier = pointerInput(onQueueDragStart, onQueueDrag, onQueueRelease) {
    var dragTotal = 0f
    val dragVelocityTracker = VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = {
            dragTotal = 0f
            dragVelocityTracker.resetTracking()
            onQueueDragStart()
        },
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            dragTotal += dragAmount
            dragVelocityTracker.addPosition(change.uptimeMillis, change.position)
            onQueueDrag(dragAmount)
        },
        onDragEnd = {
            val velocity = dragVelocityTracker.calculateVelocity().y
            onQueueRelease(dragTotal, velocity)
        },
        onDragCancel = {
            val velocity = dragVelocityTracker.calculateVelocity().y
            onQueueRelease(dragTotal, velocity)
        }
    )
}

/** "Removed" toast with Undo, shown after a queue item is removed. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun QueueSheetUndoBar(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val queueUndoBarState by remember(viewModel) {
        viewModel.playerUiState
            .map { it.toQueueUndoBarProjection() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = QueueUndoBarProjection())
    AnimatedVisibility(
        visible = queueUndoBarState.isVisible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        NbToast(
            text = "${queueUndoBarState.removedSongTitle} ${stringResource(R.string.presentation_batch_e_removed)}",
            actionLabel = stringResource(R.string.action_undo),
            onAction = { viewModel.undoRemoveSongFromQueue() },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
internal fun QueueClearQueueDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    NbDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.presentation_batch_e_clear_queue_dialog_title),
        body = stringResource(R.string.presentation_batch_e_clear_queue_dialog_message),
        actions = {
            NbButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = NbButtonStyle.Secondary
            )
            NbButton(
                text = stringResource(R.string.presentation_batch_b_clear),
                onClick = onConfirm,
                style = NbButtonStyle.Danger
            )
        }
    )
}
