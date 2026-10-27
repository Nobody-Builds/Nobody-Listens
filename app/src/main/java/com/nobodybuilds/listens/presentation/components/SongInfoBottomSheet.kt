package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbActionRow
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbSheetHandle
import com.nobodybuilds.listens.presentation.nb.NbSheetShape
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbType
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.subcomps.AutoSizingTextToFill
import com.nobodybuilds.listens.utils.formatDuration
import com.nobodybuilds.listens.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.nobodybuilds.listens.data.ai.SongMetadata
import com.nobodybuilds.listens.data.media.CoverArtUpdate
import com.nobodybuilds.listens.ui.theme.MontserratFamily
import com.nobodybuilds.listens.presentation.viewmodel.SongInfoBottomSheetViewModel
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.TransformOrigin
import com.nobodybuilds.listens.presentation.screens.TabAnimation
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import com.nobodybuilds.listens.utils.AudioMetaUtils
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SongInfoBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onPlaySong: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddNextToQueue: () -> Unit,
    onAddToPlayList: () -> Unit,
    onDeleteFromDevice: (activity: Activity, song: Song, onResult: (Boolean) -> Unit) -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToGenre: () -> Unit,
    onEditSong: (
        title: String,
        artist: String,
        album: String,
        genre: String,
        lyrics: String,
        trackNumber: Int,
        discNumber: Int?,
        replayGainTrackGainDb: String,
        replayGainAlbumGainDb: String,
        coverArtUpdate: CoverArtUpdate?
    ) -> Unit,
    generateAiMetadata: suspend (List<String>) -> Result<SongMetadata>,
    removeFromListTrigger: () -> Unit,
    isGeneratingMetadata: Boolean = false,
    aiMetadataSuccess: Boolean = false,
    aiError: String? = null,
    onRetryMetadata: () -> Unit = {},
    songInfoViewModel: SongInfoBottomSheetViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showEditSheet by remember { mutableStateOf(false) }
    val audioMeta by songInfoViewModel.audioMeta.collectAsStateWithLifecycle()
    val isNobodyListensWatchAvailable by songInfoViewModel.isNobodyListensWatchAvailable.collectAsStateWithLifecycle()
    val isWatchAvailabilityResolved by songInfoViewModel.isWatchAvailabilityResolved.collectAsStateWithLifecycle()
    val isSendingToWatch by songInfoViewModel.isSendingToWatch.collectAsStateWithLifecycle()
    val watchTransfers by songInfoViewModel.watchTransfers.collectAsStateWithLifecycle()
    val watchSongIds by songInfoViewModel.watchSongIds.collectAsStateWithLifecycle()
    val reachableWatchNodeIds by songInfoViewModel.reachableWatchNodeIds.collectAsStateWithLifecycle()
    val latestSongWatchTransfer = remember(song.id, watchTransfers) {
        watchTransfers.values
            .asSequence()
            .filter { it.songId == song.id }
            .maxByOrNull { it.updatedAtMillis }
    }
    val currentSongTransfer = latestSongWatchTransfer?.takeIf {
        it.status == com.nobodybuilds.listens.shared.WearTransferProgress.STATUS_TRANSFERRING
    }
    val currentSongTransferPercent = ((currentSongTransfer?.progress ?: 0f) * 100f).toInt().coerceIn(0, 100)
    val isSongSavedOnWatch = remember(
        song.id,
        watchSongIds,
        reachableWatchNodeIds,
        currentSongTransfer,
    ) {
        currentSongTransfer == null && songInfoViewModel.isSongSavedOnAllReachableWatches(song.id)
    }
    val canSendToWatch = remember(song.path, song.contentUriString) {
        songInfoViewModel.isLocalSongForWatchTransfer(song)
    }

    LaunchedEffect(songInfoViewModel) {
        songInfoViewModel.refreshWatchAvailability()
    }

    var lastShownWatchTransferError by remember(song.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(
        latestSongWatchTransfer?.requestId,
        latestSongWatchTransfer?.status,
        latestSongWatchTransfer?.error,
    ) {
        val failedTransfer = latestSongWatchTransfer?.takeIf {
            it.status == com.nobodybuilds.listens.shared.WearTransferProgress.STATUS_FAILED &&
                    !it.error.isNullOrBlank()
        } ?: return@LaunchedEffect
        val errorKey = "${failedTransfer.requestId}:${failedTransfer.error}"
        if (lastShownWatchTransferError == errorKey) return@LaunchedEffect
        lastShownWatchTransferError = errorKey
        Toast.makeText(context, failedTransfer.error, Toast.LENGTH_SHORT).show()
    }

    val shouldOfferWatchTransfer = remember(
        canSendToWatch,
        currentSongTransfer,
        isNobodyListensWatchAvailable,
        isSongSavedOnWatch,
        isWatchAvailabilityResolved,
    ) {
        currentSongTransfer == null &&
                canSendToWatch &&
                isWatchAvailabilityResolved &&
                isNobodyListensWatchAvailable &&
                !isSongSavedOnWatch
    }
    val shouldShowWatchTransferLoading = remember(
        canSendToWatch,
        isWatchAvailabilityResolved,
    ) {
        canSendToWatch &&
                !isWatchAvailabilityResolved
    }

    val evenCornerRadiusElems = 26.dp

    val listItemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = 20.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 20.dp,
            smoothnessAsPercentTL = 60, cornerRadiusTL = 20.dp, smoothnessAsPercentBL = 60,
            cornerRadiusBL = 20.dp, smoothnessAsPercentTR = 60
        )
    }
    val albumArtShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }
    val playButtonShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    val favoriteButtonCornerRadius by animateDpAsState(
        targetValue = if (isFavorite) evenCornerRadiusElems else 60.dp,
        animationSpec = tween(durationMillis = 300), label = "FavoriteCornerAnimation"
    )
    val favoriteButtonContainerColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContainerColorAnimation"
    )
    val favoriteButtonContentColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContentColorAnimation"
    )
    val sendToWatchContainerColor by animateColorAsState(
        targetValue = if (isSendingToWatch) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 250),
        label = "SendToWatchContainerColorAnimation"
    )
    val sendToWatchContentColor by animateColorAsState(
        targetValue = if (isSendingToWatch) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 250),
        label = "SendToWatchContentColorAnimation"
    )

    val favoriteButtonShape = remember(favoriteButtonCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = favoriteButtonCornerRadius, smoothnessAsPercentBR = 60, cornerRadiusBR = favoriteButtonCornerRadius,
            smoothnessAsPercentTL = 60, cornerRadiusTL = favoriteButtonCornerRadius, smoothnessAsPercentBL = 60,
            cornerRadiusBL = favoriteButtonCornerRadius, smoothnessAsPercentTR = 60
        )
    }
    val infoSegmentContainerShape = remember {
        RoundedCornerShape(20.dp)
    }
    val infoSegmentItemShape = remember {
        RoundedCornerShape(8.dp)
    }

    val audioMetaLabel = remember(audioMeta) {
        val meta = audioMeta ?: return@remember null
        val formatLabel = AudioMetaUtils.mimeTypeToFormat(meta.mimeType)
            .takeIf { it != "-" }
            ?.uppercase(java.util.Locale.getDefault())
        val parts = buildList {
            meta.sampleRate?.takeIf { it > 0 }
                ?.let { add(String.format(java.util.Locale.US, "%.1f kHz", it / 1000.0)) }
            meta.bitrate?.takeIf { it > 0 }
                ?.let { add("${it / 1000} kbps") }
            formatLabel?.let { add(it) }
        }
        parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    // Streamed YouTube and JioSaavn songs have no file: they offer no tag editing, share a
    // link or their name instead, and cannot be deleted from the device. YouTube adds the video.
    val youTubeVideoId = remember(song.contentUriString) {
        com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris.videoIdFrom(song.contentUriString)
    }
    val isStreamedSong = remember(song.contentUriString) {
        youTubeVideoId != null || com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris.songIdFrom(song.contentUriString) != null
    }
    val songLocationInfo = remember(song.path, song.contentUriString) {
        songInfoViewModel.getSongLocationInfo(song)
    }

    LaunchedEffect(song.id) {
        songInfoViewModel.loadAudioMeta(song)
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    com.nobodybuilds.listens.presentation.haptics.HapticBottomSheetEffect(sheetState)
    ModalBottomSheet(
        onDismissRequest = {
            if (!showEditSheet) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = Nb.canvas,
        tonalElevation = 0.dp,
        shape = NbSheetShape,
        dragHandle = { NbSheetHandle() }
    ) {
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            val shareSong: () -> Unit = {
                try {
                    val shareIntent = if (isStreamedSong) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            // JioSaavn links need a page token the library does not keep, so its songs share their name.
                            val text = youTubeVideoId?.let { "https://music.youtube.com/watch?v=$it" }
                                ?: "${song.title} · ${song.displayArtist} (JioSaavn)"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                    } else Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, song.contentUriString.toUri())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            context.getString(R.string.song_info_share_chooser_title)
                        )
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.error_share_song_format, e.localizedMessage ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                // Header: art, title, artist · album, details, favourite
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NbArt(
                        model = song.albumArtUriString,
                        title = song.album.ifBlank { song.title },
                        size = 64.dp,
                        shape = NbShapes.md,
                        contentDescription = stringResource(R.string.widget_album_art)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = NbType.h3,
                            color = Nb.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOf(song.displayArtist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                            style = NbType.body,
                            color = Nb.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(formatDuration(song.duration), audioMetaLabel).joinToString(" · "),
                            style = NbType.caption,
                            color = Nb.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    NbIconButton(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites
                        ),
                        onClick = {
                            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().like(!isFavorite)
                            onToggleFavorite()
                        },
                        tint = if (isFavorite) Nb.accentText else Nb.text
                    )
                }
                NbDivider()
                Spacer(Modifier.height(6.dp))

                NbActionRow(
                    text = stringResource(R.string.play_playback),
                    icon = rememberVectorPainter(Icons.Rounded.PlayArrow),
                    onClick = onPlaySong
                )
                NbActionRow(
                    text = stringResource(R.string.action_queue_next),
                    icon = rememberVectorPainter(Icons.Rounded.SkipNext),
                    onClick = { com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().successLight(); onAddNextToQueue() }
                )
                NbActionRow(
                    text = stringResource(R.string.action_add_to_queue),
                    icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.QueueMusic),
                    onClick = { com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().successLight(); onAddToQueue() }
                )
                // YouTube and JioSaavn songs: the service's mix of similar songs.
                com.nobodybuilds.listens.presentation.ytmusic.YouTubeMusicStartRadioAction(song = song, onDismiss = onDismiss)
                com.nobodybuilds.listens.presentation.jiosaavn.JioSaavnStartRadioAction(song = song, onDismiss = onDismiss)
                if (youTubeVideoId != null) {
                    NbActionRow(
                        text = "Watch video",
                        icon = rememberVectorPainter(Icons.Rounded.SmartDisplay),
                        onClick = {
                            // Picks up at the song's position when it is the one playing.
                            context.startActivity(
                                com.nobodybuilds.listens.presentation.ytmusic.video.YouTubeMusicVideoActivity
                                    .intent(context, youTubeVideoId, song.title)
                            )
                            onDismiss()
                        }
                    )
                }
                // Smart features: "Play similar" (song radio); renders only when on and analysed.
                com.nobodybuilds.listens.presentation.smart.SmartSongActions(song = song, onDismiss = onDismiss)
                // Ambient rain / café / fire / waves over music (opens its own small sheet).
                com.nobodybuilds.listens.presentation.lofi.LofiSongActions()
                NbActionRow(
                    text = "Add to playlist…",
                    icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.PlaylistAdd),
                    onClick = onAddToPlayList
                )
                if (!isStreamedSong) NbActionRow(
                    text = "Edit tags",
                    icon = rememberVectorPainter(Icons.Rounded.Edit),
                    onClick = { showEditSheet = true },
                    trailing = {
                        NbStatusChip(
                            text = "Autofill",
                            status = NbStatus.Neutral,
                            icon = rememberVectorPainter(Icons.Rounded.AutoAwesome)
                        )
                    }
                )
                // Cover studio: "Make a cover" (only for local songs without artwork).
                com.nobodybuilds.listens.presentation.covers.CoverSongActions(song = song)
                NbActionRow(
                    text = "Go to album",
                    icon = rememberVectorPainter(Icons.Rounded.Album),
                    onClick = onNavigateToAlbum
                )
                NbActionRow(
                    text = "Go to artist",
                    icon = rememberVectorPainter(Icons.Rounded.Person),
                    onClick = onNavigateToArtist
                )
                if (!song.genre.isNullOrEmpty()) {
                    NbActionRow(
                        text = "Go to genre",
                        subtitle = song.genre,
                        icon = rememberVectorPainter(Icons.Rounded.MusicNote),
                        onClick = onNavigateToGenre
                    )
                }
                NbActionRow(
                    text = when {
                        youTubeVideoId != null -> "Share link"
                        isStreamedSong -> "Share"
                        else -> "Share file"
                    },
                    icon = rememberVectorPainter(Icons.Rounded.Share),
                    onClick = shareSong
                )

                val shouldRenderWatchTransferRow =
                    currentSongTransfer != null ||
                            shouldOfferWatchTransfer ||
                            shouldShowWatchTransferLoading
                if (shouldRenderWatchTransferRow) {
                    val watchText = when {
                        shouldShowWatchTransferLoading -> stringResource(R.string.song_info_checking_watch)
                        isSendingToWatch && currentSongTransfer != null && currentSongTransfer.totalBytes > 0L ->
                            stringResource(R.string.song_info_transferring_percent, currentSongTransferPercent)
                        isSendingToWatch && currentSongTransfer != null ->
                            stringResource(R.string.song_info_transferring_to_watch)
                        isSendingToWatch -> stringResource(R.string.song_info_transfer_in_progress)
                        isNobodyListensWatchAvailable -> stringResource(R.string.song_info_send_to_watch)
                        else -> stringResource(R.string.song_info_watch_unavailable)
                    }
                    NbActionRow(
                        text = watchText,
                        icon = painterResource(R.drawable.rounded_watch_arrow_down_24),
                        enabled = shouldOfferWatchTransfer && !isSendingToWatch,
                        onClick = {
                            songInfoViewModel.sendSongToWatch(song) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        trailing = if (shouldShowWatchTransferLoading || isSendingToWatch) {
                            { LoadingIndicator(modifier = Modifier.size(20.dp)) }
                        } else null
                    )
                }

                if (!isStreamedSong) NbDivider(Modifier.padding(vertical = 4.dp))
                if (!isStreamedSong) NbActionRow(
                    text = "Delete from device…",
                    icon = rememberVectorPainter(Icons.Rounded.Delete),
                    danger = true,
                    onClick = {
                        (context as? Activity)?.let { activity ->
                            onDeleteFromDevice(activity, song) { result ->
                                if (result) {
                                    removeFromListTrigger()
                                    onDismiss()
                                }
                            }
                        }
                    }
                )

                // Details
                Spacer(Modifier.height(12.dp))
                NbOverline(stringResource(R.string.song_info_tab_details), modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
                SongInfoSegmentedListItem(
                    headline = stringResource(R.string.song_info_label_duration),
                    supporting = formatDuration(song.duration),
                    icon = Icons.Rounded.Schedule,
                    iconDescription = stringResource(R.string.cd_duration_icon),
                    shape = NbShapes.md,
                )
                if (!audioMetaLabel.isNullOrEmpty()) {
                    SongInfoSegmentedListItem(
                        headline = stringResource(R.string.song_info_label_song_metadata),
                        supporting = audioMetaLabel,
                        icon = Icons.Rounded.Info,
                        iconDescription = stringResource(R.string.cd_audio_format_icon),
                        shape = NbShapes.md,
                    )
                }
                SongInfoSegmentedListItem(
                    headline = songLocationInfo.label,
                    supporting = songLocationInfo.value,
                    icon = if (songLocationInfo.isCloud) Icons.Rounded.Cloud else Icons.Rounded.AudioFile,
                    iconDescription = stringResource(
                        if (songLocationInfo.isCloud) {
                            R.string.cd_provider_icon
                        } else {
                            R.string.cd_file_icon
                        }
                    ),
                    shape = NbShapes.md,
                )
            }
        }
    }

    EditSongSheet(
        visible = showEditSheet,
        song = song,
        onDismiss = { showEditSheet = false },
        onSave = { title, artist, album, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArt ->
            onEditSong(
                title,
                artist,
                album,
                genre,
                lyrics,
                trackNumber,
                discNumber,
                replayGainTrackGainDb,
                replayGainAlbumGainDb,
                coverArt
            )
            showEditSheet = false
        },
    )
}

@Composable
private fun SongInfoSegmentedListItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    iconDescription: String,
    shape: Shape,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .let { baseModifier ->
            if (onClick != null) {
                baseModifier.clickable(onClick = onClick)
            } else {
                baseModifier
            }
        }

    Row(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = Nb.textMuted,
            modifier = Modifier.size(22.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(headline, style = NbType.caption, color = Nb.textMuted)
            Text(supporting, style = NbType.body, color = Nb.text)
        }
    }
}
