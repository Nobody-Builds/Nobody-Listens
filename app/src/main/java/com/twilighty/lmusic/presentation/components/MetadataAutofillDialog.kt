package com.twilighty.lmusic.presentation.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.autofill.AutofillCandidate
import com.twilighty.lmusic.data.autofill.AutofillField
import com.twilighty.lmusic.data.autofill.AutofillSource
import com.twilighty.lmusic.data.autofill.CoverArt
import com.twilighty.lmusic.data.autofill.MetadataFields
import com.twilighty.lmusic.data.autofill.MusicBrainzAutofill
import com.twilighty.lmusic.data.autofill.OfflineMetadataGuesser
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.data.repository.MusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbArt
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCallout
import com.twilighty.lmusic.presentation.nb.NbCard
import com.twilighty.lmusic.presentation.nb.NbCheckbox
import com.twilighty.lmusic.presentation.nb.NbCompareRow
import com.twilighty.lmusic.presentation.nb.NbDivider
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbStatus
import com.twilighty.lmusic.presentation.nb.NbStatusChip
import com.twilighty.lmusic.presentation.nb.NbTextField
import com.twilighty.lmusic.presentation.nb.NbType

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MetadataAutofillEntryPoint {
    fun musicRepository(): MusicRepository
}

/** What the edit sheet receives when the user taps "Fill fields". */
data class AutofillResult(
    val fields: MetadataFields,
    val selected: Set<AutofillField>,
    val coverArt: CoverArt?,
    val candidate: AutofillCandidate,
)

private sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Loaded(val candidates: List<AutofillCandidate>) : OnlineState
    data class Failed(val offline: Boolean) : OnlineState
}

private val fieldLabels = mapOf(
    AutofillField.TITLE to R.string.song_field_title,
    AutofillField.ARTIST to R.string.song_field_artist,
    AutofillField.ALBUM to R.string.song_field_album,
    AutofillField.ALBUM_ARTIST to R.string.lmusic_field_album_artist,
    AutofillField.YEAR to R.string.lmusic_field_year,
    AutofillField.GENRE to R.string.song_field_genre,
    AutofillField.TRACK to R.string.song_field_track_number,
    AutofillField.DISC to R.string.song_field_disc_number,
    AutofillField.COMPOSER to R.string.lmusic_field_composer,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataAutofillDialog(
    song: Song,
    current: MetadataFields,
    onDismiss: () -> Unit,
    onApply: (AutofillResult) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var queryTitle by remember { mutableStateOf(current.title) }
        var queryArtist by remember { mutableStateOf(current.artist) }
        var queryAlbum by remember { mutableStateOf(current.album) }

        var offlineCandidate by remember { mutableStateOf<AutofillCandidate?>(null) }
        var online by remember { mutableStateOf<OnlineState>(OnlineState.Idle) }
        var searchJob by remember { mutableStateOf<Job?>(null) }
        var selected by remember { mutableStateOf<AutofillCandidate?>(null) }

        fun runSearch() {
            searchJob?.cancel()
            searchJob = scope.launch {
                online = OnlineState.Loading
                online = try {
                    OnlineState.Loaded(
                        MusicBrainzAutofill.search(
                            title = queryTitle,
                            artist = queryArtist,
                            album = queryAlbum,
                            durationMs = song.duration.takeIf { it > 0 }
                        )
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Autofill search failed")
                    OnlineState.Failed(offline = e is java.io.IOException)
                }
            }
        }

        LaunchedEffect(song.id) {
            offlineCandidate = withContext(Dispatchers.IO) {
                runCatching { OfflineMetadataGuesser.guess(song, loadFolderMates(context, song)) }
                    .onFailure { Timber.w(it, "Offline autofill failed") }
                    .getOrNull()
            }
            // Untagged files: search with what the filename and folder suggest.
            offlineCandidate?.fields?.let { guess ->
                if (queryTitle.isBlank()) queryTitle = guess.title
                if (queryArtist.isBlank()) queryArtist = guess.artist
                if (queryAlbum.isBlank()) queryAlbum = guess.album
            }
            if (queryTitle.isNotBlank()) runSearch() else online = OnlineState.Loaded(emptyList())
        }

        val chosen = selected
        if (chosen != null) {
            BackHandler { selected = null }
            CandidateDetail(
                candidate = chosen,
                current = current,
                songHasArt = !song.albumArtUriString.isNullOrBlank(),
                onBack = { selected = null },
                onApply = { result ->
                    onApply(result)
                    onDismiss()
                }
            )
        } else {
            Scaffold(
                topBar = {
                    AutofillTopBar(
                        title = stringResource(R.string.lmusic_autofill_title),
                        navIcon = Icons.Rounded.Close,
                        navDescription = stringResource(R.string.lmusic_autofill_close),
                        onNav = onDismiss
                    )
                },
                containerColor = Nb.canvas,
                contentWindowInsets = WindowInsets.statusBars
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.lmusic_autofill_subtitle),
                            style = NbType.body,
                            color = Nb.textSecondary
                        )
                    }
                    item {
                        NbCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NbTextField(
                                        value = queryTitle,
                                        onValueChange = { queryTitle = it },
                                        label = stringResource(R.string.lmusic_autofill_search_title),
                                        modifier = Modifier.weight(1f)
                                    )
                                    NbTextField(
                                        value = queryArtist,
                                        onValueChange = { queryArtist = it },
                                        label = stringResource(R.string.lmusic_autofill_search_artist),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                NbTextField(
                                    value = queryAlbum,
                                    onValueChange = { queryAlbum = it },
                                    label = stringResource(R.string.lmusic_autofill_search_album),
                                    optional = true
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (offlineCandidate != null && current.title.isBlank()) "Filled in from the filename" else "",
                                        style = NbType.caption,
                                        color = Nb.textMuted,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NbButton(
                                        text = stringResource(R.string.lmusic_autofill_search),
                                        onClick = { runSearch() },
                                        enabled = queryTitle.isNotBlank() && online !is OnlineState.Loading,
                                        style = NbButtonStyle.Secondary,
                                        size = NbButtonSize.Small,
                                        icon = rememberVectorPainter(Icons.Rounded.Search)
                                    )
                                }
                            }
                        }
                    }

                    offlineCandidate?.let { guess ->
                        item {
                            SectionHeader(
                                title = stringResource(R.string.lmusic_autofill_from_files),
                                subtitle = stringResource(R.string.lmusic_autofill_from_files_desc),
                                badge = { NbStatusChip(text = "Offline", status = NbStatus.Neutral) }
                            )
                        }
                        item {
                            CandidateRow(candidate = guess, highlighted = false, onClick = { selected = guess })
                        }
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.lmusic_autofill_online),
                            subtitle = stringResource(R.string.lmusic_autofill_online_desc),
                            badge = { Text("MusicBrainz", style = NbType.caption, color = Nb.textMuted) }
                        )
                    }
                    when (val state = online) {
                        OnlineState.Idle, OnlineState.Loading -> item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Nb.ink)
                                Text(
                                    text = stringResource(R.string.lmusic_autofill_searching),
                                    style = NbType.body,
                                    color = Nb.textSecondary
                                )
                            }
                        }
                        is OnlineState.Failed -> item {
                            NbCallout(
                                boldText = "Offline.",
                                text = stringResource(R.string.lmusic_autofill_offline_error),
                                actionLabel = stringResource(R.string.lmusic_autofill_search),
                                onAction = { runSearch() }
                            )
                        }
                        is OnlineState.Loaded -> {
                            if (state.candidates.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.lmusic_autofill_no_results),
                                        style = NbType.body,
                                        color = Nb.textSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                itemsIndexed(state.candidates, key = { _, it -> (it.recordingId ?: "") + (it.releaseId ?: "") }) { index, candidate ->
                                    CandidateRow(
                                        candidate = candidate,
                                        highlighted = index == 0 && candidate.confidence >= 80,
                                        onClick = { selected = candidate }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutofillTopBar(
    title: String,
    navIcon: androidx.compose.ui.graphics.vector.ImageVector,
    navDescription: String,
    onNav: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Nb.canvas)
            .statusBarsPadding()
            .heightIn(min = 60.dp)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NbIconButton(imageVector = navIcon, contentDescription = navDescription, onClick = onNav)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (subtitle != null) NbType.h3 else NbType.h2,
                color = Nb.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, style = NbType.caption, color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, badge: (@Composable () -> Unit)? = null) {
    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = NbType.h3, color = Nb.text, modifier = Modifier.weight(1f))
            badge?.invoke()
        }
        Text(text = subtitle, style = NbType.caption, color = Nb.textMuted)
    }
}

@Composable
private fun CandidateRow(candidate: AutofillCandidate, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NbShapes.lg)
            .background(Nb.canvas)
            .border(if (highlighted) 1.5.dp else 1.dp, if (highlighted) Nb.ink else Nb.border, NbShapes.lg)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (candidate.source == AutofillSource.FILES) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(NbShapes.md)
                    .background(Nb.muted),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Nb.text)
            }
        } else {
            NbArt(
                model = candidate.thumbnailUrl,
                title = candidate.fields.album.ifBlank { candidate.fields.title },
                size = 52.dp,
                shape = RoundedCornerShape(10.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = candidate.fields.title.ifBlank { "—" },
                style = NbType.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Nb.text
            )
            Text(
                text = candidate.fields.artist.ifBlank { "—" },
                style = NbType.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Nb.textMuted
            )
            val meta = listOfNotNull(
                candidate.fields.album.ifBlank { null },
                candidate.fields.year.ifBlank { null },
                candidate.releaseType,
                candidate.durationMs?.let { formatDuration(it) }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = NbType.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Nb.textMuted
                )
            }
        }
        if (candidate.source == AutofillSource.MUSICBRAINZ) {
            ConfidenceChip(candidate.confidence)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Nb.textMuted)
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: Int, short: Boolean = false) {
    NbStatusChip(
        text = if (short) "$confidence%" else stringResource(R.string.lmusic_autofill_match, confidence),
        status = when {
            confidence >= 80 -> NbStatus.Success
            confidence >= 65 -> NbStatus.Warning
            else -> NbStatus.Neutral
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateDetail(
    candidate: AutofillCandidate,
    current: MetadataFields,
    songHasArt: Boolean,
    onBack: () -> Unit,
    onApply: (AutofillResult) -> Unit,
) {
    var detailed by remember(candidate) { mutableStateOf(if (candidate.detailed) candidate else null) }
    var coverArt by remember(candidate) { mutableStateOf<CoverArt?>(null) }
    var coverBitmap by remember(candidate) { mutableStateOf<ImageBitmap?>(null) }
    var coverLoading by remember(candidate) { mutableStateOf(candidate.source == AutofillSource.MUSICBRAINZ) }
    var useCover by remember(candidate) { mutableStateOf(true) }
    var chosenFields by remember(candidate) { mutableStateOf<Set<AutofillField>?>(null) }

    LaunchedEffect(candidate) {
        if (candidate.source == AutofillSource.MUSICBRAINZ) {
            launch {
                val art = runCatching { MusicBrainzAutofill.loadCoverArt(candidate) }.getOrNull()
                coverArt = art
                coverBitmap = art?.let { cover ->
                    withContext(Dispatchers.Default) {
                        runCatching {
                            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeByteArray(cover.bytes, 0, cover.bytes.size, options)?.asImageBitmap()
                        }.getOrNull()
                    }
                }
                coverLoading = false
                useCover = art != null
            }
            detailed = runCatching { MusicBrainzAutofill.loadDetails(candidate) }.getOrElse { candidate }
        }
    }

    val details = detailed
    val proposals = details?.fields
    val changedFields = remember(proposals, current) {
        if (proposals == null) emptyList() else AutofillField.entries.filter { field ->
            val newValue = field.valueOf(proposals).trim()
            newValue.isNotEmpty() && !newValue.equals(field.valueOf(current).trim(), ignoreCase = false)
        }
    }
    LaunchedEffect(changedFields) {
        if (chosenFields == null && proposals != null) {
            // Pre-tick everything that's new, but don't overwrite an existing title/artist
            // from a weak match.
            chosenFields = changedFields.filter { field ->
                val hasCurrent = field.valueOf(current).isNotBlank()
                !(hasCurrent && candidate.confidence < 70 && field in setOf(AutofillField.TITLE, AutofillField.ARTIST))
            }.toSet()
        }
    }

    Scaffold(
        topBar = {
            val titleText = details?.fields?.title ?: candidate.fields.title
            val sub = listOfNotNull(
                (details?.fields ?: candidate.fields).artist.ifBlank { null },
                (details?.fields ?: candidate.fields).album.ifBlank { null }?.let { album ->
                    val y = (details?.fields ?: candidate.fields).year
                    if (y.isNotBlank()) "$album ($y)" else album
                }
            ).joinToString(" · ").ifBlank { null }
            AutofillTopBar(
                title = titleText,
                subtitle = sub,
                navIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navDescription = stringResource(R.string.lmusic_autofill_back),
                onNav = onBack,
                trailing = if (candidate.source == AutofillSource.MUSICBRAINZ) {
                    { ConfidenceChip(candidate.confidence, short = true) }
                } else null
            )
        },
        bottomBar = {
            val selectedSet = chosenFields.orEmpty()
            val applyCover = useCover && coverArt != null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Nb.canvas)
            ) {
                NbDivider()
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val count = selectedSet.size
                    NbButton(
                        text = when {
                            count > 0 && applyCover -> "Fill $count ${if (count == 1) "field" else "fields"} and cover"
                            count > 0 -> "Fill $count ${if (count == 1) "field" else "fields"}"
                            applyCover -> "Use this cover"
                            else -> stringResource(R.string.lmusic_autofill_apply)
                        },
                        onClick = {
                            val finalCandidate = details ?: candidate
                            onApply(
                                AutofillResult(
                                    fields = finalCandidate.fields,
                                    selected = selectedSet,
                                    coverArt = if (applyCover) coverArt else null,
                                    candidate = finalCandidate
                                )
                            )
                        },
                        enabled = details != null && (selectedSet.isNotEmpty() || applyCover),
                        size = NbButtonSize.Large,
                        icon = rememberVectorPainter(Icons.Rounded.AutoAwesome),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Nothing is written to the file until you tap Save.",
                        style = NbType.caption,
                        color = Nb.textMuted
                    )
                }
            }
        },
        containerColor = Nb.canvas,
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (candidate.source == AutofillSource.MUSICBRAINZ) {
                item {
                    val checked = useCover && coverArt != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NbShapes.lg)
                            .border(if (checked) 1.5.dp else 1.dp, if (checked) Nb.ink else Nb.border, NbShapes.lg)
                            .clickable(enabled = coverArt != null) { useCover = !useCover }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(NbShapes.md)
                                .background(Nb.muted),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = coverBitmap
                            when {
                                bitmap != null -> Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                coverLoading -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Nb.ink)
                                else -> Icon(Icons.Rounded.Album, contentDescription = null, tint = Nb.textMuted)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lmusic_autofill_cover_art),
                                style = NbType.title,
                                color = Nb.text
                            )
                            Text(
                                text = when {
                                    coverLoading -> stringResource(R.string.lmusic_autofill_cover_loading)
                                    coverArt == null -> stringResource(R.string.lmusic_autofill_cover_none)
                                    else -> stringResource(if (songHasArt) R.string.lmusic_autofill_cover_replace else R.string.lmusic_autofill_cover_add)
                                },
                                style = NbType.caption,
                                color = Nb.textMuted
                            )
                        }
                        NbCheckbox(
                            checked = checked,
                            onCheckedChange = if (coverArt != null) ({ v -> useCover = v }) else null
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.lmusic_autofill_choose_fields),
                        style = NbType.h3,
                        color = Nb.text,
                        modifier = Modifier.weight(1f)
                    )
                    if (changedFields.isNotEmpty()) {
                        Text(
                            "${chosenFields.orEmpty().size} of ${changedFields.size} picked",
                            style = NbType.caption,
                            color = Nb.textMuted
                        )
                    }
                }
            }

            if (proposals == null) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Nb.ink)
                        Text(
                            text = stringResource(R.string.lmusic_autofill_loading_details),
                            style = NbType.body,
                            color = Nb.textSecondary
                        )
                    }
                }
            } else if (changedFields.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.lmusic_autofill_nothing_new),
                        style = NbType.body,
                        color = Nb.textSecondary
                    )
                }
            } else {
                items(changedFields, key = { it.name }) { field ->
                    val isChecked = chosenFields.orEmpty().contains(field)
                    NbCompareRow(
                        label = stringResource(fieldLabels.getValue(field)),
                        current = field.valueOf(current),
                        proposed = field.valueOf(proposals),
                        checked = isChecked,
                        onCheckedChange = {
                            val set = chosenFields.orEmpty()
                            chosenFields = if (isChecked) set - field else set + field
                        }
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private suspend fun loadFolderMates(context: Context, song: Song): List<Song> {
    val folder = File(song.path).parent ?: return emptyList()
    return runCatching {
        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, MetadataAutofillEntryPoint::class.java)
            .musicRepository()
        repository.getAllSongsOnce().filter { File(it.path).parent == folder }
    }.getOrElse { emptyList() }
}
