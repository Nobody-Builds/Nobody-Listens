package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.TransferState
import com.nobodybuilds.listens.presentation.components.AlwaysOnScalingPositionIndicator
import com.nobodybuilds.listens.presentation.components.WearTopTimeText
import com.nobodybuilds.listens.presentation.theme.surfaceContainerHighestColor
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.screenBackgroundColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerColor
import com.nobodybuilds.listens.data.local.LocalSongEntity

@Composable
internal fun DownloadedSongActionScreen(
    song: LocalSongEntity,
    canPlayOnPhone: Boolean,
    isPhonePlaybackPending: Boolean,
    isCurrentWatchSong: Boolean,
    isPlayingWatchSong: Boolean,
    onDismiss: () -> Unit,
    onPlayOnWatch: () -> Unit,
    onPlayOnPhone: () -> Unit,
    onDeleteFromWatch: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()
    val subtitle = buildSongSubtitle(song.artist, song.album, song.duration)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.screenBackgroundColor())
            .zIndex(12f),
    ) {
        ScalingLazyColumn(
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

            if (subtitle.isNotEmpty()) {
                item {
                    Text(
                        text = subtitle,
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
                DownloadsActionChip(
                    icon = Icons.Rounded.PlayArrow,
                    label = when {
                        isCurrentWatchSong && isPlayingWatchSong -> stringResource(R.string.wear_playing_on_watch)
                        isCurrentWatchSong -> stringResource(R.string.wear_current_on_watch)
                        else -> stringResource(R.string.wear_play_on_watch)
                    },
                    backgroundColor = palette.shuffleActive.copy(alpha = 0.38f),
                    onClick = onPlayOnWatch,
                )
            }

            item {
                DownloadsActionChip(
                    icon = Icons.Rounded.PhoneAndroid,
                    label = when {
                        isPhonePlaybackPending -> stringResource(R.string.wear_starting_on_phone)
                        !canPlayOnPhone -> stringResource(R.string.wear_phone_disconnected)
                        else -> stringResource(R.string.wear_play_on_phone)
                    },
                    backgroundColor = if (canPlayOnPhone && !isPhonePlaybackPending) {
                        palette.repeatActive.copy(alpha = 0.38f)
                    } else {
                        palette.surfaceContainerHighestColor()
                    },
                    enabled = canPlayOnPhone && !isPhonePlaybackPending,
                    onClick = onPlayOnPhone,
                )
            }

            item {
                DownloadsActionChip(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.wear_delete_from_watch),
                    backgroundColor = palette.favoriteActive.copy(alpha = 0.38f),
                    onClick = onDeleteFromWatch,
                )
            }

            item {
                DownloadsActionChip(
                    icon = Icons.Rounded.Close,
                    label = stringResource(R.string.wear_back),
                    backgroundColor = palette.surfaceContainerColor(),
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
internal fun ConfirmDeleteDownloadedSongScreen(
    song: LocalSongEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.screenBackgroundColor())
            .zIndex(12f),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = stringResource(R.string.wear_delete_from_watch_confirm_title),
                    style = MaterialTheme.typography.title3,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            item {
                Text(
                    text = song.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.body2,
                    color = palette.textSecondary.copy(alpha = 0.86f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            item {
                Text(
                    text = stringResource(R.string.wear_delete_watch_copy_message),
                    style = MaterialTheme.typography.caption2,
                    color = palette.textSecondary.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            item {
                DownloadsActionChip(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.wear_delete),
                    backgroundColor = palette.favoriteActive.copy(alpha = 0.38f),
                    onClick = onConfirm,
                )
            }

            item {
                DownloadsActionChip(
                    icon = Icons.Rounded.Close,
                    label = stringResource(R.string.wear_cancel),
                    backgroundColor = palette.surfaceContainerColor(),
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
internal fun ConfirmCancelTransferScreen(
    transfer: TransferState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.screenBackgroundColor())
            .zIndex(12f),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = stringResource(R.string.wear_cancel_transfer_title),
                    style = MaterialTheme.typography.title3,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            item {
                Text(
                    text = transfer.songTitle.ifBlank { context.getString(R.string.wear_current_transfer_fallback) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.body2,
                    color = palette.textSecondary.copy(alpha = 0.86f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            item {
                Text(
                    text = stringResource(R.string.wear_cancel_transfer_body),
                    style = MaterialTheme.typography.caption2,
                    color = palette.textSecondary.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            item {
                DownloadsActionChip(
                    icon = Icons.Rounded.Close,
                    label = stringResource(R.string.wear_yes_cancel_transfer),
                    backgroundColor = MaterialTheme.colors.error,
                    contentColor = MaterialTheme.colors.onError,
                    onClick = onConfirm,
                )
            }

            item {
                DownloadsActionChip(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.wear_keep_sending),
                    backgroundColor = palette.surfaceContainerColor(),
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
