package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nobodybuilds.listens.presentation.components.WatchScalingColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.AlwaysOnScalingPositionIndicator
import com.nobodybuilds.listens.presentation.components.PlayingEqIcon
import com.nobodybuilds.listens.presentation.components.WearTopTimeText
import com.nobodybuilds.listens.presentation.viewmodel.BrowseUiState
import com.nobodybuilds.listens.presentation.viewmodel.WearBrowseViewModel
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.rememberBrowseSubscreenTitleFont
import com.nobodybuilds.listens.presentation.theme.screenBackgroundColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerHighColor
import com.nobodybuilds.listens.shared.WearBrowseRequest
import com.nobodybuilds.listens.shared.WearLibraryItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.MoreVert
import com.nobodybuilds.listens.data.TransferState
import com.nobodybuilds.listens.presentation.viewmodel.WearDownloadsViewModel
import com.nobodybuilds.listens.presentation.viewmodel.WearPlayerViewModel
import com.nobodybuilds.listens.shared.WearTransferProgress

/**
 * Screen showing songs within a specific context (album, artist, playlist, favorites, all songs).
 * Tapping a song triggers playback on the phone with the full context queue.
 */
@Composable
fun SongListScreen(
    browseType: String,
    contextId: String?,
    title: String,
    viewModel: WearBrowseViewModel = hiltViewModel(),
    downloadsViewModel: WearDownloadsViewModel = hiltViewModel(),
    playerViewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadedIds by downloadsViewModel.downloadedSongIds.collectAsState()
    val activeTransfers by downloadsViewModel.activeTransfers.collectAsState()
    val playerState by playerViewModel.playerState.collectAsState()
    val palette = LocalWearPalette.current
    val subscreenTitleFont = rememberBrowseSubscreenTitleFont()
    var selectedSongForMenu by remember { mutableStateOf<WearLibraryItem?>(null) }
    var selectedSongForTransferConfirmation by remember { mutableStateOf<WearLibraryItem?>(null) }

    // Determine the context type for playback (maps browseType to context)
    val contextType = when (browseType) {
        WearBrowseRequest.ALBUM_SONGS -> "album"
        WearBrowseRequest.ARTIST_SONGS -> "artist"
        WearBrowseRequest.PLAYLIST_SONGS -> "playlist"
        WearBrowseRequest.FAVORITES -> "favorites"
        WearBrowseRequest.ALL_SONGS -> "all_songs"
        else -> browseType
    }

    // The actual context ID for playback (null for favorites/all_songs)
    val playbackContextId = when (browseType) {
        WearBrowseRequest.FAVORITES, WearBrowseRequest.ALL_SONGS -> null
        else -> contextId?.takeIf { it != "none" }
    }

    LaunchedEffect(browseType, contextId) {
        viewModel.loadItems(browseType, contextId?.takeIf { it != "none" })
    }

    val background = palette.screenBackgroundColor()

    when (val state = uiState) {
        is BrowseUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    indicatorColor = palette.textSecondary,
                )

                WearTopTimeText(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(5f),
                    color = palette.textPrimary,
                )
            }
        }

        is BrowseUiState.Error -> {
            val columnState = rememberResponsiveColumnState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
            ) {
                WatchScalingColumn(
                    modifier = Modifier.fillMaxSize(),
                    columnState = columnState,
                ) {
                    item { Spacer(modifier = Modifier.height(18.dp)) }

                    item {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.title2,
                            fontFamily = subscreenTitleFont,
                            fontWeight = FontWeight(760),
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.body2,
                            color = palette.textError,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    item {
                        Chip(
                            label = { Text(stringResource(R.string.wear_retry), color = palette.textPrimary) },
                            icon = {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.wear_cd_retry),
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = { viewModel.refresh() },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = palette.surfaceContainerColor(),
                                contentColor = palette.chipContent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
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

        is BrowseUiState.Success -> {
            val columnState = rememberResponsiveColumnState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
            ) {
                WatchScalingColumn(
                    modifier = Modifier.fillMaxSize(),
                    columnState = columnState,
                    // An open menu or confirmation takes the bezel.
                    rotaryActive = selectedSongForMenu == null && selectedSongForTransferConfirmation == null,
                ) {
                    item { Spacer(modifier = Modifier.height(18.dp)) }

                    item {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.title2,
                            fontFamily = subscreenTitleFont,
                            fontWeight = FontWeight(760),
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                        )
                    }

                    if (browseType == WearBrowseRequest.PLAYLIST_SONGS && playbackContextId != null && state.items.isNotEmpty()) {
                        item { SavePlaylistToWatchChip(playbackContextId, title, state.items) }
                    }

                    if (state.items.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.wear_no_songs),
                                style = MaterialTheme.typography.body2,
                                color = palette.textSecondary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    } else {
                        items(state.items.size) { index ->
                            val song = state.items[index]
                            val isDownloaded = downloadedIds.contains(song.id)
                            val transfer = pickTransferForSong(activeTransfers, song.id)
                            val isCurrentSong = song.id == playerState.songId && playerState.songId.isNotBlank()
                            SongChip(
                                song = song,
                                isDownloaded = isDownloaded,
                                transfer = transfer,
                                isCurrentSong = isCurrentSong,
                                isPlayingSong = isCurrentSong && playerState.isPlaying,
                                onClick = {
                                    viewModel.playFromContext(
                                        songId = song.id,
                                        contextType = contextType,
                                        contextId = playbackContextId,
                                    )
                                },
                                onMenuClick = {
                                    selectedSongForMenu = song
                                },
                            )
                        }
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

                val menuSong = selectedSongForMenu
                if (menuSong != null) {
                    val menuSongIsDownloaded = downloadedIds.contains(menuSong.id)
                    val menuSongTransfer = pickTransferForSong(activeTransfers, menuSong.id)
                    val menuSongIsTransferring = menuSongTransfer != null &&
                        menuSongTransfer.status == WearTransferProgress.STATUS_TRANSFERRING
                    val menuSongCanSaveToWatch = menuSong.canSaveToWatch

                    SongActionScreen(
                        song = menuSong,
                        isDownloaded = menuSongIsDownloaded,
                        isTransferring = menuSongIsTransferring,
                        canSaveToWatch = menuSongCanSaveToWatch,
                        onDismiss = { selectedSongForMenu = null },
                        onPlayNow = {
                            viewModel.playFromContext(
                                songId = menuSong.id,
                                contextType = contextType,
                                contextId = playbackContextId,
                            )
                            selectedSongForMenu = null
                        },
                        onPlayNext = {
                            viewModel.playNextFromContext(
                                songId = menuSong.id,
                                contextType = contextType,
                                contextId = playbackContextId,
                            )
                            selectedSongForMenu = null
                        },
                        onAddToQueue = {
                            viewModel.addToQueueFromContext(
                                songId = menuSong.id,
                                contextType = contextType,
                                contextId = playbackContextId,
                            )
                            selectedSongForMenu = null
                        },
                        onSaveToWatch = {
                            if (menuSongCanSaveToWatch) {
                                selectedSongForMenu = null
                                selectedSongForTransferConfirmation = menuSong
                            } else {
                                selectedSongForMenu = null
                            }
                        },
                    )
                }

                val confirmSong = selectedSongForTransferConfirmation
                if (confirmSong != null) {
                    ConfirmSaveToWatchScreen(
                        song = confirmSong,
                        onDismiss = { selectedSongForTransferConfirmation = null },
                        onConfirm = {
                            downloadsViewModel.requestDownload(confirmSong.id)
                            selectedSongForTransferConfirmation = null
                        },
                    )
                }
            }
        }
    }
}

private fun pickTransferForSong(
    transfers: kotlin.collections.Map<String, TransferState>,
    songId: String,
): TransferState? {
    return transfers.values
        .asSequence()
        .filter { it.songId == songId }
        .maxWithOrNull(
            compareBy<TransferState>(
                { transferStatusPriority(it.status) },
                { it.bytesTransferred }
            )
        )
}

private fun transferStatusPriority(status: String): Int = when (status) {
    WearTransferProgress.STATUS_TRANSFERRING -> 4
    WearTransferProgress.STATUS_FAILED -> 3
    WearTransferProgress.STATUS_CANCELLED -> 2
    WearTransferProgress.STATUS_COMPLETED -> 1
    else -> 0
}

@Composable
private fun SongChip(
    song: WearLibraryItem,
    isDownloaded: Boolean = false,
    transfer: TransferState? = null,
    isCurrentSong: Boolean = false,
    isPlayingSong: Boolean = false,
    onClick: () -> Unit,
    onMenuClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val palette = LocalWearPalette.current
    val isTransferring = transfer != null &&
        transfer.status == WearTransferProgress.STATUS_TRANSFERRING
    val secondaryText = when {
        isTransferring -> "${(transfer.progress * 100).toInt()}%"
        isCurrentSong -> {
            val stateLabel = context.getString(
                if (isPlayingSong) R.string.wear_status_playing else R.string.wear_status_current
            )
            if (song.subtitle.isNotEmpty()) "$stateLabel · ${song.subtitle}" else stateLabel
        }
        song.subtitle.isNotEmpty() -> song.subtitle
        else -> null
    }
    val iconTint = if (isCurrentSong) {
        if (isPlayingSong) palette.shuffleActive else palette.textSecondary
    } else {
        palette.textSecondary
    }
    val containerColor = if (isCurrentSong) palette.surfaceContainerHighColor() else palette.surfaceContainerColor()

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip(
            label = {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.textPrimary,
                )
            },
            secondaryLabel = if (secondaryText != null) {
                {
                    Text(
                        text = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = when {
                            isCurrentSong && isPlayingSong -> palette.shuffleActive.copy(alpha = 0.90f)
                            isCurrentSong -> palette.textSecondary.copy(alpha = 0.90f)
                            isTransferring -> palette.shuffleActive.copy(alpha = 0.90f)
                            else -> palette.textSecondary.copy(alpha = 0.78f)
                        },
                    )
                }
            } else null,
            icon = {
                when {
                    isCurrentSong -> PlayingEqIcon(
                        color = iconTint,
                        isPlaying = isPlayingSong,
                        modifier = Modifier.size(18.dp),
                    )
                    isDownloaded -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.wear_cd_downloaded),
                        tint = palette.shuffleActive,
                        modifier = Modifier.size(18.dp),
                    )
                    isTransferring -> CircularProgressIndicator(
                        indicatorColor = palette.shuffleActive,
                        trackColor = palette.surfaceContainerColor(),
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    else -> Icon(
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
