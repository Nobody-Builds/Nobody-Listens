package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCallout
import com.nobodybuilds.listens.presentation.nb.NbDialog
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbTextField
import com.nobodybuilds.listens.presentation.nb.NbToast
import com.nobodybuilds.listens.presentation.nb.NbType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import java.net.URLEncoder
import timber.log.Timber
import com.nobodybuilds.listens.data.model.Song
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.nobodybuilds.listens.data.media.AudioMetadataReader
import com.nobodybuilds.listens.data.media.CoverArtUpdate
import java.util.Locale
import androidx.compose.ui.res.stringResource

private fun formatReplayGainForInput(gainDb: Float?): String {
    return gainDb?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditSongContent(
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

    // Nobody Listens: extra tag fields + autofill
    var albumArtist by remember { mutableStateOf(song.albumArtist ?: "") }
    var year by remember { mutableStateOf(song.year.takeIf { it > 0 }?.toString() ?: "") }
    var composer by remember { mutableStateOf("") }
    var initialExtended by remember { mutableStateOf(com.nobodybuilds.listens.data.media.ExtendedTagSnapshot(albumArtist, year, "")) }
    var showAutofill by remember { mutableStateOf(false) }
    var autofillIds by remember { mutableStateOf<com.nobodybuilds.listens.data.autofill.AutofillCandidate?>(null) }
    val autofilledFields = remember { androidx.compose.runtime.mutableStateListOf<com.nobodybuilds.listens.data.autofill.AutofillField>() }
    var coverFromAutofill by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val autofillAppliedMessage = stringResource(R.string.nobody_listens_autofill_applied)
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
        initialExtended = com.nobodybuilds.listens.data.media.ExtendedTagSnapshot(albumArtist, year, "")
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
                com.nobodybuilds.listens.data.media.ExtendedTagReader.read(java.io.File(song.path))
            }
            if (extended != null) {
                albumArtist = extended.albumArtist.ifBlank { song.albumArtist.orEmpty() }
                year = extended.year.ifBlank { song.year.takeIf { it > 0 }?.toString().orEmpty() }
                composer = extended.composer
                initialExtended = com.nobodybuilds.listens.data.media.ExtendedTagSnapshot(albumArtist, year, composer)
            }
        }
    }

    if (showAutofill) {
        MetadataAutofillDialog(
            song = song,
            current = com.nobodybuilds.listens.data.autofill.MetadataFields(
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
                        com.nobodybuilds.listens.data.autofill.AutofillField.TITLE -> title = f.title
                        com.nobodybuilds.listens.data.autofill.AutofillField.ARTIST -> artist = f.artist
                        com.nobodybuilds.listens.data.autofill.AutofillField.ALBUM -> album = f.album
                        com.nobodybuilds.listens.data.autofill.AutofillField.ALBUM_ARTIST -> albumArtist = f.albumArtist
                        com.nobodybuilds.listens.data.autofill.AutofillField.YEAR -> year = f.year
                        com.nobodybuilds.listens.data.autofill.AutofillField.GENRE -> genre = f.genre
                        com.nobodybuilds.listens.data.autofill.AutofillField.TRACK -> trackNumber = f.trackNumber
                        com.nobodybuilds.listens.data.autofill.AutofillField.DISC -> discNumber = f.discNumber
                        com.nobodybuilds.listens.data.autofill.AutofillField.COMPOSER -> composer = f.composer
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
                        com.nobodybuilds.listens.data.autofill.AutofillField.TITLE,
                        com.nobodybuilds.listens.data.autofill.AutofillField.ARTIST
                    )
                ) || result.candidate.confidence >= 80
                autofillIds = if (
                    result.candidate.source == com.nobodybuilds.listens.data.autofill.AutofillSource.MUSICBRAINZ &&
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
    fun hl(field: com.nobodybuilds.listens.data.autofill.AutofillField) = field in autofilledFields

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
                    text = stringResource(R.string.nobody_listens_autofill),
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
                            actionLabel = stringResource(R.string.nobody_listens_autofill),
                            onAction = { showAutofill = true }
                        )
                    }
                }

                item {
                    NbTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(R.string.song_field_title),
                        highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.TITLE),
                        trailing = autofillBadge(hl(com.nobodybuilds.listens.data.autofill.AutofillField.TITLE))
                    )
                }
                item {
                    NbTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = stringResource(R.string.song_field_artist),
                        placeholder = "Unknown artist",
                        highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.ARTIST),
                        trailing = autofillBadge(hl(com.nobodybuilds.listens.data.autofill.AutofillField.ARTIST))
                    )
                }
                item {
                    NbTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = stringResource(R.string.song_field_album),
                        placeholder = "Unknown album",
                        highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.ALBUM),
                        trailing = autofillBadge(hl(com.nobodybuilds.listens.data.autofill.AutofillField.ALBUM))
                    )
                }
                item {
                    NbTextField(
                        value = albumArtist,
                        onValueChange = { albumArtist = it },
                        label = stringResource(R.string.nobody_listens_field_album_artist),
                        optional = true,
                        highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.ALBUM_ARTIST),
                        helper = "Leave empty to use the song's artist."
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NbTextField(
                            value = year,
                            onValueChange = { input -> year = input.filter { it.isDigit() || it == '-' }.take(10) },
                            label = stringResource(R.string.nobody_listens_field_year),
                            placeholder = "2024",
                            highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.YEAR),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        NbTextField(
                            value = trackNumber,
                            onValueChange = { trackNumber = it },
                            label = stringResource(R.string.song_field_track_number),
                            highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.TRACK),
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
                            highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.GENRE),
                            modifier = Modifier.weight(1f)
                        )
                        NbTextField(
                            value = discNumber,
                            onValueChange = { discNumber = it },
                            label = stringResource(R.string.song_field_disc_number),
                            highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.DISC),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    NbTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        label = stringResource(R.string.nobody_listens_field_composer),
                        optional = true,
                        highlighted = hl(com.nobodybuilds.listens.data.autofill.AutofillField.COMPOSER),
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
                                // Nobody Listens' original behaviour for the file.
                                val ids = autofillIds
                                val extended = com.nobodybuilds.listens.data.media.ExtendedTagUpdate(
                                    albumArtist = albumArtist.trim().takeIf { it != initialExtended.albumArtist.trim() },
                                    year = year.trim().takeIf { it != initialExtended.year.trim() },
                                    composer = composer.trim().takeIf { it != initialExtended.composer.trim() },
                                    musicBrainzRecordingId = ids?.recordingId,
                                    musicBrainzReleaseId = ids?.releaseId,
                                    musicBrainzArtistId = ids?.artistId,
                                    musicBrainzReleaseGroupId = ids?.releaseGroupId,
                                )
                                val albumArtistUnchanged = extended.albumArtist == null
                                com.nobodybuilds.listens.data.media.PendingExtendedTags.put(
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
                            "Android asks once to let Nobody Listens change this file.",
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
