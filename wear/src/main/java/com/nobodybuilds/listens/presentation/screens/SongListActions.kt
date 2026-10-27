package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nobodybuilds.listens.presentation.components.WatchScalingColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.AlwaysOnScalingPositionIndicator
import com.nobodybuilds.listens.presentation.components.WearTopTimeText
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.screenBackgroundColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerHighColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerHighestColor
import com.nobodybuilds.listens.shared.WearLibraryItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.QueueMusic

@Composable
internal fun SongActionScreen(
    song: WearLibraryItem,
    isDownloaded: Boolean,
    isTransferring: Boolean,
    canSaveToWatch: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSaveToWatch: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()

    val playNowColor = palette.shuffleActive.copy(alpha = 0.38f)
    val playNextColor = palette.repeatActive.copy(alpha = 0.38f)
    val addToQueueColor = palette.surfaceContainerHighColor()
    val saveToWatchColor = if (!canSaveToWatch || isDownloaded || isTransferring) {
        palette.surfaceContainerHighestColor()
    } else {
        palette.favoriteActive.copy(alpha = 0.40f)
    }
    val cancelColor = palette.surfaceContainerColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.screenBackgroundColor())
            .zIndex(12f),
    ) {
        WatchScalingColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = song.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.title3,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            if (song.subtitle.isNotEmpty()) {
                item {
                    Text(
                        text = song.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.caption2,
                        color = palette.textSecondary.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                    )
                }
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.wear_play_now),
                    backgroundColor = playNowColor,
                    onClick = onPlayNow,
                )
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.SkipNext,
                    label = stringResource(R.string.wear_play_next),
                    backgroundColor = playNextColor,
                    onClick = onPlayNext,
                )
            }

            item {
                SongActionChip(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = stringResource(R.string.wear_add_to_queue),
                    backgroundColor = addToQueueColor,
                    onClick = onAddToQueue,
                )
            }

            if (canSaveToWatch || isDownloaded || isTransferring) {
                item {
                    SongActionChip(
                        icon = Icons.Rounded.Download,
                        label = when {
                            isDownloaded -> stringResource(R.string.wear_saved_on_watch)
                            isTransferring -> stringResource(R.string.wear_saving)
                            else -> stringResource(R.string.wear_save_to_watch)
                        },
                        backgroundColor = saveToWatchColor,
                        enabled = canSaveToWatch && !isDownloaded && !isTransferring,
                        onClick = onSaveToWatch,
                    )
                }
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.Close,
                    label = stringResource(R.string.wear_back),
                    backgroundColor = cancelColor,
                    onClick = onDismiss,
                )
            }
        }

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

@Composable
internal fun ConfirmSaveToWatchScreen(
    song: WearLibraryItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()

    val confirmColor = palette.favoriteActive.copy(alpha = 0.42f)
    val cancelColor = palette.surfaceContainerColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.screenBackgroundColor())
            .zIndex(14f),
    ) {
        WatchScalingColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = song.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            item {
                Text(
                    text = stringResource(R.string.wear_save_song_on_watch_title),
                    color = palette.textSecondary.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.Download,
                    label = stringResource(R.string.wear_confirm_save),
                    backgroundColor = confirmColor,
                    onClick = onConfirm,
                )
            }

            item {
                SongActionChip(
                    icon = Icons.Rounded.Close,
                    label = stringResource(R.string.wear_cancel),
                    backgroundColor = cancelColor,
                    onClick = onDismiss,
                )
            }
        }

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun SongActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val contentColor = if (enabled) palette.textPrimary else palette.textSecondary.copy(alpha = 0.72f)

    Chip(
        label = {
            Text(
                text = label,
                color = contentColor,
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        colors = ChipDefaults.chipColors(
            backgroundColor = if (enabled) backgroundColor
            else palette.surfaceContainerHighestColor(),
            contentColor = contentColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
