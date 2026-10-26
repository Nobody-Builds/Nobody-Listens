package com.twilighty.lmusic.presentation.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbArt
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCallout
import com.twilighty.lmusic.presentation.nb.NbDialog
import com.twilighty.lmusic.presentation.nb.NbDivider
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbStatus
import com.twilighty.lmusic.presentation.nb.NbStatusChip
import com.twilighty.lmusic.presentation.nb.NbTextField
import com.twilighty.lmusic.presentation.nb.NbToast
import com.twilighty.lmusic.presentation.nb.NbType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twilighty.lmusic.R
import java.net.URLEncoder
import timber.log.Timber
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import dev.shreyaspatil.capturable.capturable
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import com.twilighty.lmusic.data.media.AudioMetadataReader
import com.twilighty.lmusic.data.media.CoverArtUpdate
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import java.io.ByteArrayOutputStream
import java.util.Locale
import androidx.compose.ui.res.stringResource

private fun formatReplayGainForInput(gainDb: Float?): String {
    return gainDb?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditSongSheet(
    visible: Boolean,
    song: Song,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200))
            ) {
                EditSongContent(
                    song = song,
                    onDismiss = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditSongContent(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (
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
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.displayArtist) }
    var album by remember { mutableStateOf(song.album) }
    var genre by remember { mutableStateOf(song.genre ?: "") }
    var lyrics by remember { mutableStateOf(song.lyrics ?: "") }
    var trackNumber by remember { mutableStateOf(song.trackNumber.toString()) }
    var discNumber by remember { mutableStateOf(song.discNumber?.toString() ?: "") }
    var replayGainTrackGainDb by remember { mutableStateOf("") }
    var replayGainAlbumGainDb by remember { mutableStateOf("") }
    var coverArtPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var editedCoverArt by remember { mutableStateOf<CoverArtUpdate?>(null) }
    var isCoverArtDeleted by remember { mutableStateOf(false) }
    var showCoverArtCropper by remember { mutableStateOf(false) }
    var pendingCoverArtUri by remember { mutableStateOf<Uri?>(null) }

    var showInfoDialog by remember { mutableStateOf(false) }

    // LMusic: extra tag fields + autofill
    var albumArtist by remember { mutableStateOf(song.albumArtist ?: "") }
    var year by remember { mutableStateOf(song.year.takeIf { it > 0 }?.toString() ?: "") }
    var composer by remember { mutableStateOf("") }
    var initialExtended by remember { mutableStateOf(com.twilighty.lmusic.data.media.ExtendedTagSnapshot(albumArtist, year, "")) }
    var showAutofill by remember { mutableStateOf(false) }
    var autofillIds by remember { mutableStateOf<com.twilighty.lmusic.data.autofill.AutofillCandidate?>(null) }
    val autofilledFields = remember { androidx.compose.runtime.mutableStateListOf<com.twilighty.lmusic.data.autofill.AutofillField>() }
    var coverFromAutofill by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val autofillAppliedMessage = stringResource(R.string.lmusic_autofill_applied)
    val sheetScope = rememberCoroutineScope()
    val context = LocalContext.current
    val pickCoverArtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingCoverArtUri = uri
            showCoverArtCropper = true
        }
    }

    LaunchedEffect(song) {
        title = song.title
        artist = song.displayArtist
        album = song.album
        genre = song.genre ?: ""
        lyrics = song.lyrics ?: ""
        trackNumber = song.trackNumber.toString()
        discNumber = song.discNumber?.toString() ?: ""
        replayGainTrackGainDb = ""
        replayGainAlbumGainDb = ""
        albumArtist = song.albumArtist ?: ""
        year = song.year.takeIf { it > 0 }?.toString() ?: ""
        composer = ""
        initialExtended = com.twilighty.lmusic.data.media.ExtendedTagSnapshot(albumArtist, year, "")
        autofillIds = null
        autofilledFields.clear()
        coverFromAutofill = false
        coverArtPreview = null
        editedCoverArt = null
        isCoverArtDeleted = false

        if (song.path.isNotBlank()) {
            val embeddedMetadata = withContext(Dispatchers.IO) {
                try {
                    val file = java.io.File(song.path)
                    if (file.exists()) {
                        AudioMetadataReader.read(file, readArtwork = false)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to read embedded metadata for EditSongSheet")
                    null
                }
            }

            if (lyrics.isBlank()) {
                embeddedMetadata?.lyrics?.takeIf { it.isNotBlank() }?.let { embeddedLyrics ->
                    lyrics = embeddedLyrics
                }
            }

            replayGainTrackGainDb = formatReplayGainForInput(embeddedMetadata?.replayGainTrackGainDb)
            replayGainAlbumGainDb = formatReplayGainForInput(embeddedMetadata?.replayGainAlbumGainDb)

            val extended = withContext(Dispatchers.IO) {
                com.twilighty.lmusic.data.media.ExtendedTagReader.read(java.io.File(song.path))
            }
            if (extended != null) {
                albumArtist = extended.albumArtist.ifBlank { song.albumArtist.orEmpty() }
                year = extended.year.ifBlank { song.year.takeIf { it > 0 }?.toString().orEmpty() }
                composer = extended.composer
                initialExtended = com.twilighty.lmusic.data.media.ExtendedTagSnapshot(albumArtist, year, composer)
            }
        }
    }

    if (showAutofill) {
        MetadataAutofillDialog(
            song = song,
            current = com.twilighty.lmusic.data.autofill.MetadataFields(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                year = year,
                genre = genre,
                trackNumber = trackNumber.takeIf { it != "0" }.orEmpty(),
                discNumber = discNumber,
                composer = composer
            ),
            onDismiss = { showAutofill = false },
            onApply = { result ->
                val f = result.fields
                autofilledFields.clear()
                autofilledFields.addAll(result.selected)
                result.selected.forEach { field ->
                    when (field) {
                        com.twilighty.lmusic.data.autofill.AutofillField.TITLE -> title = f.title
                        com.twilighty.lmusic.data.autofill.AutofillField.ARTIST -> artist = f.artist
                        com.twilighty.lmusic.data.autofill.AutofillField.ALBUM -> album = f.album
                        com.twilighty.lmusic.data.autofill.AutofillField.ALBUM_ARTIST -> albumArtist = f.albumArtist
                        com.twilighty.lmusic.data.autofill.AutofillField.YEAR -> year = f.year
                        com.twilighty.lmusic.data.autofill.AutofillField.GENRE -> genre = f.genre
                        com.twilighty.lmusic.data.autofill.AutofillField.TRACK -> trackNumber = f.trackNumber
                        com.twilighty.lmusic.data.autofill.AutofillField.DISC -> discNumber = f.discNumber
                        com.twilighty.lmusic.data.autofill.AutofillField.COMPOSER -> composer = f.composer
                    }
                }
                result.coverArt?.let { art ->
                    editedCoverArt = CoverArtUpdate(art.bytes, art.mimeType)
                    isCoverArtDeleted = false
                    coverFromAutofill = true
                    coverArtPreview = runCatching {
                        android.graphics.BitmapFactory.decodeByteArray(art.bytes, 0, art.bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
                // Store MusicBrainz IDs only when the match was taken as this song's identity.
                val identityApplied = result.selected.containsAll(
                    listOf(
                        com.twilighty.lmusic.data.autofill.AutofillField.TITLE,
                        com.twilighty.lmusic.data.autofill.AutofillField.ARTIST
                    )
                ) || result.candidate.confidence >= 80
                autofillIds = if (
                    result.candidate.source == com.twilighty.lmusic.data.autofill.AutofillSource.MUSICBRAINZ &&
                    identityApplied
                ) result.candidate else null
                sheetScope.launch { snackbarHostState.showSnackbar(autofillAppliedMessage) }
            }
        )
    }

    if (showCoverArtCropper && pendingCoverArtUri != null) {
        CoverArtCropperDialog(
            sourceUri = pendingCoverArtUri!!,
            onDismiss = {
                showCoverArtCropper = false
                pendingCoverArtUri = null
            },
            onConfirm = { result ->
                coverArtPreview = result.preview
                editedCoverArt = result.update
                isCoverArtDeleted = false
                coverFromAutofill = false
                showCoverArtCropper = false
                pendingCoverArtUri = null
            }
        )
    }

    // Definición de colores para los TextFields
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    // Definición de la forma para los TextFields
    val textFieldShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 10.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusTR = 10.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusBL = 10.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusBR = 10.dp,
        smoothnessAsPercentTR = 60
    )

    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    // --- Info dialog ---
    if (showInfoDialog) {
        NbDialog(
            onDismissRequest = { showInfoDialog = false },
            title = stringResource(R.string.edit_song_info_dialog_title),
            body = stringResource(R.string.edit_song_info_dialog_body),
            actions = {
                NbButton(
                    text = stringResource(R.string.edit_song_got_it),
                    onClick = { showInfoDialog = false }
                )
            }
        )
    }

    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val isKeyboardVisible by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val tagsMostlyMissing = remember(song.id) {
        song.artist.isBlank() || song.artist.equals("<unknown>", ignoreCase = true) ||
            song.album.isBlank() || song.album.equals("<unknown>", ignoreCase = true)
    }
    val fileName = remember(song.path) { song.path.substringAfterLast('/').takeIf { it.isNotBlank() } }
    val formatLine = remember(song.id) {
        listOfNotNull(
            song.mimeType?.substringAfter('/')?.uppercase(Locale.getDefault())?.takeIf { it.isNotBlank() },
            song.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
            song.duration.takeIf { it > 0 }?.let { ms -> "%d:%02d".format(ms / 60000, (ms / 1000) % 60) }
        ).joinToString(" · ")
    }
    fun hl(field: com.twilighty.lmusic.data.autofill.AutofillField) = field in autofilledFields

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Nb.canvas)
                    .statusBarsPadding()
                    .heightIn(min = 60.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NbIconButton(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                Text(
                    text = stringResource(R.string.edit_song_toolbar_title),
                    style = NbType.h2,
                    color = Nb.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                NbButton(
                    text = stringResource(R.string.lmusic_autofill),
                    onClick = { showAutofill = true },
                    style = NbButtonStyle.Secondary,
                    size = NbButtonSize.Small,
                    icon = rememberVectorPainter(Icons.Rounded.AutoAwesome)
                )
                NbIconButton(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = stringResource(R.string.cd_show_metadata_info),
                    onClick = { showInfoDialog = true },
                    iconSize = 20.dp
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                NbToast(
                    text = data.visuals.message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    icon = { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp)) }
                )
            }
        },
        containerColor = Nb.canvas,
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = if (isKeyboardVisible) 8.dp else (navBarBottom + 112.dp),
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    CoverArtEditorCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        albumArtUri = song.albumArtUriString,
                        artKey = song.album.ifBlank { song.title },
                        preview = coverArtPreview,
                        isDeleted = isCoverArtDeleted,
                        isAutofilled = coverFromAutofill && coverArtPreview != null,
                        fileName = fileName,
                        formatLine = formatLine,
                        onPickNewArt = {
                            pickCoverArtLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onDelete = {
                            coverArtPreview = null
                            isCoverArtDeleted = true
                            coverFromAutofill = false
                            editedCoverArt = CoverArtUpdate(isDeletion = true)
                        },
                        onReset = {
                            coverArtPreview = null
                            editedCoverArt = null
                            isCoverArtDeleted = false
                            coverFromAutofill = false
                        }
                    )
                }

                if (tagsMostlyMissing && autofilledFields.isEmpty()) {
                    item {
                        NbCallout(
                            boldText = "Most tags are missing.",
                            text = "Autofill can suggest them.",
                            icon = rememberVectorPainter(Icons.Rounded.Info),
                            actionLabel = stringResource(R.string.lmusic_autofill),
                            onAction = { showAutofill = true }
                        )
                    }
                }

                item {
                    NbTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(R.string.song_field_title),
                        highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.TITLE),
                        trailing = autofillBadge(hl(com.twilighty.lmusic.data.autofill.AutofillField.TITLE))
                    )
                }
                item {
                    NbTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = stringResource(R.string.song_field_artist),
                        placeholder = "Unknown artist",
                        highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.ARTIST),
                        trailing = autofillBadge(hl(com.twilighty.lmusic.data.autofill.AutofillField.ARTIST))
                    )
                }
                item {
                    NbTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = stringResource(R.string.song_field_album),
                        placeholder = "Unknown album",
                        highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.ALBUM),
                        trailing = autofillBadge(hl(com.twilighty.lmusic.data.autofill.AutofillField.ALBUM))
                    )
                }
                item {
                    NbTextField(
                        value = albumArtist,
                        onValueChange = { albumArtist = it },
                        label = stringResource(R.string.lmusic_field_album_artist),
                        optional = true,
                        highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.ALBUM_ARTIST),
                        helper = "Leave empty to use the song's artist."
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NbTextField(
                            value = year,
                            onValueChange = { input -> year = input.filter { it.isDigit() || it == '-' }.take(10) },
                            label = stringResource(R.string.lmusic_field_year),
                            placeholder = "2024",
                            highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.YEAR),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        NbTextField(
                            value = trackNumber,
                            onValueChange = { trackNumber = it },
                            label = stringResource(R.string.song_field_track_number),
                            highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.TRACK),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NbTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = stringResource(R.string.song_field_genre),
                            highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.GENRE),
                            modifier = Modifier.weight(1f)
                        )
                        NbTextField(
                            value = discNumber,
                            onValueChange = { discNumber = it },
                            label = stringResource(R.string.song_field_disc_number),
                            highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.DISC),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    NbTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        label = stringResource(R.string.lmusic_field_composer),
                        optional = true,
                        highlighted = hl(com.twilighty.lmusic.data.autofill.AutofillField.COMPOSER),
                        helper = if (autofilledFields.isNotEmpty()) "Autofilled fields are outlined. Edit any of them before saving." else null
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NbTextField(
                            value = replayGainTrackGainDb,
                            onValueChange = { replayGainTrackGainDb = it },
                            label = stringResource(R.string.song_field_replaygain_track_db),
                            placeholder = stringResource(R.string.placeholder_replaygain_track_example),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        NbTextField(
                            value = replayGainAlbumGainDb,
                            onValueChange = { replayGainAlbumGainDb = it },
                            label = stringResource(R.string.song_field_replaygain_album_db),
                            placeholder = stringResource(R.string.placeholder_replaygain_album_example),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    NbTextField(
                        value = lyrics,
                        onValueChange = { lyrics = it },
                        label = stringResource(R.string.lyrics),
                        optional = true,
                        singleLine = false,
                        minLines = 5,
                        maxLines = 10,
                        trailing = {
                            NbIconButton(
                                painter = painterResource(id = R.drawable.rounded_search_24),
                                contentDescription = stringResource(R.string.cd_search_lyrics_lrclib),
                                onClick = {
                                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                                    val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                                    val url = "https://lrclib.net/search/$encodedTitle%20$encodedArtist"
                                    val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                size = 40.dp,
                                iconSize = 20.dp
                            )
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = !isKeyboardVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Nb.canvas)
                        .navigationBarsPadding()
                ) {
                    NbDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NbButton(
                            text = stringResource(R.string.cancel),
                            onClick = onDismiss,
                            style = NbButtonStyle.Secondary,
                            size = NbButtonSize.Large,
                            modifier = Modifier.weight(1f)
                        )
                        NbButton(
                            text = "Save to file",
                            onClick = {
                                val resolvedTrackNumber = trackNumber.toIntOrNull() ?: song.trackNumber
                                val resolvedDiscNumber = discNumber.toIntOrNull()
                                // Only send fields the user changed, so an untouched sheet keeps
                                // LMusic's original behaviour for the file.
                                val ids = autofillIds
                                val extended = com.twilighty.lmusic.data.media.ExtendedTagUpdate(
                                    albumArtist = albumArtist.trim().takeIf { it != initialExtended.albumArtist.trim() },
                                    year = year.trim().takeIf { it != initialExtended.year.trim() },
                                    composer = composer.trim().takeIf { it != initialExtended.composer.trim() },
                                    musicBrainzRecordingId = ids?.recordingId,
                                    musicBrainzReleaseId = ids?.releaseId,
                                    musicBrainzArtistId = ids?.artistId,
                                    musicBrainzReleaseGroupId = ids?.releaseGroupId,
                                )
                                val albumArtistUnchanged = extended.albumArtist == null
                                com.twilighty.lmusic.data.media.PendingExtendedTags.put(
                                    song.id,
                                    // Keep an existing album artist instead of letting it be reset to the artist.
                                    if (albumArtistUnchanged && initialExtended.albumArtist.isNotBlank()) {
                                        extended.copy(albumArtist = initialExtended.albumArtist)
                                    } else {
                                        extended
                                    }
                                )
                                onSave(
                                    title.trim(),
                                    artist.trim(),
                                    album.trim(),
                                    genre.trim(),
                                    lyrics,
                                    resolvedTrackNumber,
                                    resolvedDiscNumber,
                                    replayGainTrackGainDb.trim(),
                                    replayGainAlbumGainDb.trim(),
                                    editedCoverArt
                                )
                            },
                            style = NbButtonStyle.Primary,
                            size = NbButtonSize.Large,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Nb.textMuted, modifier = Modifier.size(14.dp))
                        Text(
                            "Android asks once to let LMusic change this file.",
                            style = NbType.caption,
                            color = Nb.textMuted
                        )
                    }
                }
            }
        }
    }
}

/** Sparkle badge shown inside a field that autofill changed. */
@Composable
private fun autofillBadge(show: Boolean): (@Composable () -> Unit)? = if (show) {
    {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = "Autofilled",
            tint = Nb.accentText,
            modifier = Modifier.padding(end = 6.dp).size(18.dp)
        )
    }
} else null

@Composable
private fun CoverArtEditorCard(
    modifier: Modifier = Modifier,
    albumArtUri: String?,
    artKey: String,
    preview: ImageBitmap?,
    isDeleted: Boolean,
    isAutofilled: Boolean,
    fileName: String?,
    formatLine: String,
    onPickNewArt: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(NbShapes.lg)
                .background(Nb.muted),
            contentAlignment = Alignment.Center
        ) {
            when {
                isDeleted -> {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_music_note_24),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Nb.textMuted
                    )
                }
                preview != null -> {
                    Image(
                        bitmap = preview,
                        contentDescription = stringResource(R.string.cd_cover_art_preview),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    NbArt(
                        model = albumArtUri,
                        title = artKey,
                        size = 92.dp,
                        shape = NbShapes.lg,
                        contentDescription = stringResource(R.string.cd_current_song_cover_art)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isAutofilled) {
                NbStatusChip(
                    text = "New cover",
                    status = NbStatus.Accent,
                    icon = rememberVectorPainter(Icons.Rounded.AutoAwesome)
                )
            } else if (fileName != null) {
                Text(
                    text = fileName,
                    style = NbType.caption,
                    color = Nb.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NbButton(
                    text = if (albumArtUri != null || preview != null) "Change" else "Add cover",
                    onClick = onPickNewArt,
                    style = NbButtonStyle.Secondary,
                    size = NbButtonSize.Small,
                    icon = rememberVectorPainter(Icons.Rounded.Image)
                )
                if (preview != null || isDeleted) {
                    NbButton(
                        text = stringResource(R.string.action_reset),
                        onClick = onReset,
                        style = NbButtonStyle.Quiet,
                        size = NbButtonSize.Small
                    )
                } else if (albumArtUri != null) {
                    NbButton(
                        text = "Remove",
                        onClick = onDelete,
                        style = NbButtonStyle.DangerQuiet,
                        size = NbButtonSize.Small
                    )
                }
            }
            if (formatLine.isNotBlank()) {
                Text(formatLine, style = NbType.caption, color = Nb.textMuted, maxLines = 1)
            }
        }
    }
}

private data class CoverArtCropResult(
    val preview: ImageBitmap,
    val update: CoverArtUpdate,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun CoverArtCropperDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (CoverArtCropResult) -> Unit,
) {
    val context = LocalContext.current
    val dialogScope = rememberCoroutineScope()
    val captureController = rememberCaptureController()
    var loadedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(0f) }

    LaunchedEffect(sourceUri) {
        isLoading = true
        loadError = null
        val bitmap = withContext(Dispatchers.IO) { decodeBitmapFromUri(context, sourceUri) }
        if (bitmap != null) {
            loadedBitmap = bitmap.asImageBitmap()
        } else {
            loadError = context.getString(R.string.edit_song_unable_to_load_image)
        }
        isLoading = false
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(containerSize, scale, loadedBitmap) {
        loadedBitmap?.let { bitmap ->
            offset = clampOffset(offset, scale, containerSize, bitmap.width, bitmap.height)
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        loadedBitmap?.let { bitmap ->
            offset = clampOffset(offset + panChange, newScale, containerSize, bitmap.width, bitmap.height)
        }
    }

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    BasicAlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.edit_song_crop_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    val cropSide = minOf(maxWidth, 320.dp)
                    val density = LocalDensity.current
                    val cropSidePx = remember(cropSide) { with(density) { cropSide.toPx() } }

                    LaunchedEffect(cropSidePx) {
                        containerSize = cropSidePx
                    }

                    Box(
                        modifier = Modifier
                            .size(cropSide)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceDim)
                                .clipToBounds()
                        ) {
                            when {
                                isLoading -> {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }

                                loadError != null -> {
                                    Text(
                                        text = loadError!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                 loadedBitmap != null -> {
                                    val bitmap = loadedBitmap!!
                                    val baseScale = maxOf(containerSize / bitmap.width, containerSize / bitmap.height)
                                    val displayWidth = with(LocalDensity.current) { (bitmap.width * baseScale).toDp() }
                                    val displayHeight = with(LocalDensity.current) { (bitmap.height * baseScale).toDp() }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clipToBounds()
                                            .capturable(controller = captureController)
                                            .transformable(transformableState)
                                    ) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .requiredSize(displayWidth, displayHeight)
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    translationX = offset.x
                                                    translationY = offset.y
                                                }
                                        )
                                    }

                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val step = size.width / 3f
                                        for (index in 1 until 3) {
                                            val lineOffset = step * index
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(lineOffset, 0f),
                                                end = Offset(lineOffset, size.height),
                                                strokeWidth = 2f
                                            )
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(0f, lineOffset),
                                                end = Offset(size.width, lineOffset),
                                                strokeWidth = 2f
                                            )
                                        }
                                        drawRect(color = gridColor, style = Stroke(width = 3f))
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.edit_song_crop_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = onDismiss
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    val canConfirm = !isLoading && loadError == null && loadedBitmap != null
                    Button(
                        enabled = canConfirm && !isSaving,
                        onClick = {
                            if (!canConfirm) return@Button
                            dialogScope.launch {
                                isSaving = true
                                val captured = captureController.captureAsync().await()
                                if (captured != null) {
                                    val bytes = withContext(Dispatchers.IO) {
                                        imageBitmapToJpeg(captured)
                                    }
                                    if (bytes != null) {
                                        onConfirm(
                                            CoverArtCropResult(
                                                preview = captured,
                                                update = CoverArtUpdate(bytes, COVER_ART_MIME_TYPE)
                                            )
                                        )
                                    } else {
                                        Timber.w("Failed to convert captured cover art to JPEG")
                                    }
                                } else {
                                    Timber.w("CaptureController returned null bitmap")
                                }
                                isSaving = false
                            }
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.edit_song_apply_cover_art))
                    }
                }
            }
        }
    }
}

private const val COVER_ART_MIME_TYPE = "image/jpeg"

private fun clampOffset(
    offset: Offset,
    scale: Float,
    containerSize: Float,
    bitmapWidth: Int,
    bitmapHeight: Int
): Offset {
    if (containerSize <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) return Offset.Zero

    // When using ContentScale.Crop, the image is scaled to fill the container size.
    // The base scale factor is the maximum of the width and height ratios.
    val baseScale = maxOf(containerSize / bitmapWidth, containerSize / bitmapHeight)
    val totalScale = baseScale * scale

    val scaledWidth = bitmapWidth * totalScale
    val scaledHeight = bitmapHeight * totalScale

    // Calculate maximum translation bounds (half of the overflow).
    val maxX = maxOf(0f, (scaledWidth - containerSize) / 2f)
    val maxY = maxOf(0f, (scaledHeight - containerSize) / 2f)

    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private fun imageBitmapToJpeg(imageBitmap: ImageBitmap): ByteArray? {
    return try {
        val bitmap = imageBitmap.asAndroidBitmap()
        ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.toByteArray()
        }
    } catch (error: Exception) {
        Timber.e(error, "Unable to compress image bitmap to JPEG")
        null
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val maxDimension = 2048
            val width = info.size.width
            val height = info.size.height
            val largerSide = maxOf(width, height)
            if (largerSide > maxDimension) {
                val scale = largerSide.toFloat() / maxDimension
                decoder.setTargetSize(
                    (width / scale).roundToInt(),
                    (height / scale).roundToInt()
                )
            }
            decoder.isMutableRequired = false
        }
    } catch (error: Exception) {
        Timber.e(error, "Failed to decode bitmap from $uri")
        null
    }
}
