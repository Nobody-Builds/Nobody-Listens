package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.PlayingEqIcon
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.surfaceContainerColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerHighColor

@Composable
internal fun DownloadsSectionHeader(
    title: String,
    color: Color,
    topPadding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.caption2,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 2.dp),
    )
}

@Composable
internal fun DownloadedSongChip(
    title: String,
    secondaryText: String,
    isCurrentSong: Boolean,
    isPlayingSong: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val containerColor = if (isCurrentSong) {
        palette.surfaceContainerHighColor()
    } else {
        palette.surfaceContainerColor()
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip(
            label = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.textPrimary,
                )
            },
            secondaryLabel = if (secondaryText.isNotEmpty() || isCurrentSong) {
                {
                    val statusWord = stringResource(
                        if (isPlayingSong) R.string.wear_status_playing else R.string.wear_status_current
                    )
                    Text(
                        text = if (isCurrentSong) {
                            if (secondaryText.isNotEmpty()) {
                                "$statusWord · $secondaryText"
                            } else {
                                statusWord
                            }
                        } else {
                            secondaryText
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrentSong && isPlayingSong) {
                            palette.shuffleActive.copy(alpha = 0.90f)
                        } else {
                            palette.textSecondary.copy(alpha = 0.78f)
                        },
                    )
                }
            } else {
                null
            },
            icon = {
                if (isCurrentSong) {
                    PlayingEqIcon(
                        color = if (isPlayingSong) palette.shuffleActive else palette.textSecondary,
                        isPlaying = isPlayingSong,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            onClick = onClick,
            colors = ChipDefaults.chipColors(
                backgroundColor = containerColor,
                contentColor = palette.chipContent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 40.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(34.dp)
                .background(
                    color = palette.surfaceContainerHighColor().copy(alpha = 0.74f),
                    shape = CircleShape,
                )
                .clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.wear_cd_more_options),
                tint = palette.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun DownloadsActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentColor: Color = LocalWearPalette.current.textPrimary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val disabledContentColor = palette.textSecondary.copy(alpha = 0.72f)
    Chip(
        label = {
            Text(
                text = label,
                color = if (enabled) contentColor else disabledContentColor,
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) contentColor else disabledContentColor,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = { if (enabled) onClick() },
        colors = ChipDefaults.chipColors(
            backgroundColor = backgroundColor,
            contentColor = palette.chipContent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun buildSongSubtitle(artist: String, album: String, durationMs: Long): String {
    val parts = buildList {
        if (artist.isNotBlank()) add(artist)
        if (album.isNotBlank()) add(album)
        if (durationMs > 0L) add(formatDuration(durationMs))
    }
    return parts.joinToString(" · ")
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
