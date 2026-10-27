package com.nobodybuilds.listens.presentation.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nobodybuilds.listens.presentation.components.WatchScalingColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.TransferState
import com.nobodybuilds.listens.presentation.components.AlwaysOnScalingPositionIndicator
import com.nobodybuilds.listens.presentation.components.PlayingEqIcon
import com.nobodybuilds.listens.presentation.components.WearTopTimeText
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.WearTitleFont
import com.nobodybuilds.listens.presentation.theme.screenBackgroundColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerHighColor
import com.nobodybuilds.listens.data.local.LocalSongEntity
import com.nobodybuilds.listens.presentation.viewmodel.WearDownloadsUiEvent
import com.nobodybuilds.listens.presentation.viewmodel.WearDownloadsViewModel
import com.nobodybuilds.listens.presentation.viewmodel.WearPlayerViewModel
import com.nobodybuilds.listens.shared.WearTransferProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/**
 * Screen showing songs stored locally on the watch.
 * Tapping a song starts local ExoPlayer playback.
 */
@Composable
fun DownloadsScreen(
    viewModel: WearDownloadsViewModel = hiltViewModel(),
    playerViewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val localSongs by viewModel.localSongs.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val deviceSongs by viewModel.deviceSongs.collectAsState()
    val isDeviceLibraryLoading by viewModel.isDeviceLibraryLoading.collectAsState()
    val deviceLibraryError by viewModel.deviceLibraryError.collectAsState()
    val pendingPhonePlaybackSongId by viewModel.pendingPhonePlaybackSongId.collectAsState()
    val playerState by playerViewModel.playerState.collectAsState()
    val isPhoneConnected by playerViewModel.isPhoneConnected.collectAsState()
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()
    val context = LocalContext.current
    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasAudioPermission by remember {
        mutableStateOf(hasAudioLibraryPermission(context, audioPermission))
    }
    var selectedLocalSongForMenu by remember { mutableStateOf<LocalSongEntity?>(null) }
    var selectedLocalSongForDeleteConfirmation by remember { mutableStateOf<LocalSongEntity?>(null) }
    var selectedTransferForCancelConfirmation by remember { mutableStateOf<TransferState?>(null) }
    var inlineMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
    }

    LaunchedEffect(Unit) {
        hasAudioPermission = hasAudioLibraryPermission(context, audioPermission)
    }
    LaunchedEffect(hasAudioPermission) {
        viewModel.refreshDeviceLibrary(hasPermission = hasAudioPermission)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WearDownloadsUiEvent.Message -> inlineMessage = event.value
            }
        }
    }
    LaunchedEffect(inlineMessage) {
        val message = inlineMessage ?: return@LaunchedEffect
        delay(4_000L)
        if (inlineMessage == message) {
            inlineMessage = null
        }
    }
    LaunchedEffect(activeTransfers, selectedTransferForCancelConfirmation?.requestId) {
        val requestId = selectedTransferForCancelConfirmation?.requestId ?: return@LaunchedEffect
        val transfer = activeTransfers[requestId]
        if (transfer?.status != WearTransferProgress.STATUS_TRANSFERRING) {
            selectedTransferForCancelConfirmation = null
        }
    }
    val transferringStates = activeTransfers.values
        .filter { it.status == WearTransferProgress.STATUS_TRANSFERRING }
        .sortedByDescending { it.bytesTransferred }
    val failedTransfers = activeTransfers.values
        .filter {
            it.status == WearTransferProgress.STATUS_FAILED ||
                it.status == WearTransferProgress.STATUS_CANCELLED
        }
        .sortedBy { it.songTitle.lowercase() }
    val sectionAccentColor = MaterialTheme.colors.primary

    val background = palette.screenBackgroundColor()
    val surfaceContainer = palette.surfaceContainerColor()
    val elevatedSurfaceContainer = palette.surfaceContainerHighColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        WatchScalingColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
            // An open menu or confirmation takes the bezel.
            rotaryActive = selectedLocalSongForMenu == null &&
                selectedLocalSongForDeleteConfirmation == null &&
                selectedTransferForCancelConfirmation == null,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = stringResource(R.string.wear_watch_library_title),
                    style = MaterialTheme.typography.title2,
                    fontFamily = WearTitleFont,
                    fontWeight = FontWeight(780),
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            if (inlineMessage != null) {
                item {
                    Chip(
                        label = {
                            Text(
                                text = inlineMessage.orEmpty(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textPrimary,
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = palette.textError,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { inlineMessage = null },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = elevatedSurfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }

            if (transferringStates.isNotEmpty()) {
                item {
                    DownloadsSectionHeader(
                        title = stringResource(R.string.wear_transferring_from_phone),
                        color = sectionAccentColor,
                    )
                }

                items(transferringStates.size) { index ->
                    val transfer = transferringStates[index]
                    val progressText = if (transfer.totalBytes > 0L) {
                        "${(transfer.progress * 100f).toInt().coerceIn(0, 100)}%"
                    } else {
                        context.getString(R.string.wear_transfer_starting)
                    }
                    Chip(
                        label = {
                            Text(
                                text = transfer.songTitle.ifBlank { context.getString(R.string.wear_preparing_transfer) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = progressText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textSecondary.copy(alpha = 0.82f),
                            )
                        },
                        icon = {
                            CircularProgressIndicator(
                                indicatorColor = palette.shuffleActive,
                                trackColor = surfaceContainer,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        },
                        onClick = { selectedTransferForCancelConfirmation = transfer },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = elevatedSurfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (failedTransfers.isNotEmpty()) {
                item {
                    DownloadsSectionHeader(
                        title = stringResource(R.string.wear_transfer_issues),
                        color = palette.textError,
                    )
                }

                items(failedTransfers.size) { index ->
                    val transfer = failedTransfers[index]
                    val statusText = when (transfer.status) {
                        WearTransferProgress.STATUS_CANCELLED -> context.getString(R.string.wear_transfer_cancelled)
                        else -> transfer.error?.ifBlank { null } ?: context.getString(R.string.wear_transfer_failed)
                    }
                    Chip(
                        label = {
                            Text(
                                text = transfer.songTitle.ifBlank { context.getString(R.string.wear_transfer_failed) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = statusText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textError.copy(alpha = 0.90f),
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = palette.textError,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {},
                        colors = ChipDefaults.chipColors(
                            backgroundColor = elevatedSurfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (localSongs.isNotEmpty()) {
                item {
                    DownloadsSectionHeader(
                        title = stringResource(R.string.wear_saved_from_phone),
                        color = sectionAccentColor,
                    )
                }

                items(localSongs.size) { index ->
                    val song = localSongs[index]
                    val isCurrentSong = song.songId == playerState.songId && playerState.songId.isNotBlank()
                    val isPlayingSong = isCurrentSong && playerState.isPlaying
                    val secondaryText = if (
                        song.artist.isNotEmpty() ||
                        song.album.isNotEmpty() ||
                        song.duration > 0L
                    ) {
                        buildSongSubtitle(
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.duration,
                        )
                    } else {
                        ""
                    }
                    DownloadedSongChip(
                        title = song.title,
                        secondaryText = secondaryText,
                        isCurrentSong = isCurrentSong,
                        isPlayingSong = isPlayingSong,
                        onClick = {
                            viewModel.playLocalSong(song.songId)
                        },
                        onMenuClick = {
                            selectedLocalSongForMenu = song
                        },
                    )
                }
            }

            if (deviceSongs.isNotEmpty()) {
                item {
                    DownloadsSectionHeader(
                        title = stringResource(R.string.wear_songs_watch_storage),
                        color = sectionAccentColor,
                        topPadding = 10.dp,
                    )
                }
            }

            if (!hasAudioPermission) {
                item {
                    Chip(
                        label = {
                            Text(
                                text = stringResource(R.string.wear_allow_audio_access),
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = stringResource(R.string.wear_read_watch_library),
                                color = palette.textSecondary.copy(alpha = 0.8f),
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { permissionLauncher.launch(audioPermission) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = surfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (isDeviceLibraryLoading) {
                item {
                    Text(
                        text = stringResource(R.string.wear_scanning_watch_storage),
                        style = MaterialTheme.typography.body2,
                        color = palette.textSecondary.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            } else if (deviceLibraryError != null) {
                item {
                    Chip(
                        label = {
                            Text(
                                text = stringResource(R.string.wear_retry_scan),
                                color = palette.textPrimary,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = deviceLibraryError.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textSecondary.copy(alpha = 0.8f),
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { viewModel.refreshDeviceLibrary(hasPermission = true) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = surfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (deviceSongs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.wear_no_local_songs),
                        style = MaterialTheme.typography.body2,
                        color = palette.textSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            } else {
                items(deviceSongs.size) { index ->
                    val song = deviceSongs[index]
                    val isCurrentSong = song.songId == playerState.songId && playerState.songId.isNotBlank()
                    val isPlayingSong = isCurrentSong && playerState.isPlaying
                    val secondaryText = if (song.artist.isNotEmpty() || song.album.isNotEmpty()) {
                        buildSongSubtitle(
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.durationMs,
                        )
                    } else {
                        ""
                    }
                    Chip(
                        label = {
                            Text(
                                text = song.title,
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
                        } else null,
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
                        onClick = {
                            viewModel.playDeviceSong(song.songId)
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (isCurrentSong) elevatedSurfaceContainer else surfaceContainer,
                            contentColor = palette.chipContent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
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

        val menuSong = selectedLocalSongForMenu
        if (menuSong != null) {
            val isCurrentSong = menuSong.songId == playerState.songId && playerState.songId.isNotBlank()
            val isPlayingSong = isCurrentSong && playerState.isPlaying
            DownloadedSongActionScreen(
                song = menuSong,
                canPlayOnPhone = isPhoneConnected,
                isPhonePlaybackPending = pendingPhonePlaybackSongId == menuSong.songId,
                isCurrentWatchSong = isCurrentSong,
                isPlayingWatchSong = isPlayingSong,
                onDismiss = { selectedLocalSongForMenu = null },
                onPlayOnWatch = {
                    viewModel.playLocalSong(menuSong.songId)
                    selectedLocalSongForMenu = null
                },
                onPlayOnPhone = {
                    viewModel.playSongOnPhone(menuSong.songId)
                    selectedLocalSongForMenu = null
                },
                onDeleteFromWatch = {
                    selectedLocalSongForMenu = null
                    selectedLocalSongForDeleteConfirmation = menuSong
                },
            )
        }

        val confirmDeleteSong = selectedLocalSongForDeleteConfirmation
        if (confirmDeleteSong != null) {
            ConfirmDeleteDownloadedSongScreen(
                song = confirmDeleteSong,
                onDismiss = { selectedLocalSongForDeleteConfirmation = null },
                onConfirm = {
                    viewModel.deleteSong(confirmDeleteSong.songId)
                    selectedLocalSongForDeleteConfirmation = null
                },
            )
        }

        val confirmCancelTransfer = selectedTransferForCancelConfirmation
        if (confirmCancelTransfer != null) {
            ConfirmCancelTransferScreen(
                transfer = confirmCancelTransfer,
                onDismiss = { selectedTransferForCancelConfirmation = null },
                onConfirm = {
                    viewModel.cancelTransfer(confirmCancelTransfer.requestId)
                    selectedTransferForCancelConfirmation = null
                },
            )
        }
    }
}

private fun hasAudioLibraryPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
