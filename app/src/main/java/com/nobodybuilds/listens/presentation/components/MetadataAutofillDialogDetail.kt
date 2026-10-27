package com.nobodybuilds.listens.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.autofill.AutofillCandidate
import com.nobodybuilds.listens.data.autofill.AutofillField
import com.nobodybuilds.listens.data.autofill.AutofillSource
import com.nobodybuilds.listens.data.autofill.CoverArt
import com.nobodybuilds.listens.data.autofill.MetadataFields
import com.nobodybuilds.listens.data.autofill.MusicBrainzAutofill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbCheckbox
import com.nobodybuilds.listens.presentation.nb.NbCompareRow
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbType

private val fieldLabels = mapOf(
    AutofillField.TITLE to R.string.song_field_title,
    AutofillField.ARTIST to R.string.song_field_artist,
    AutofillField.ALBUM to R.string.song_field_album,
    AutofillField.ALBUM_ARTIST to R.string.nobody_listens_field_album_artist,
    AutofillField.YEAR to R.string.nobody_listens_field_year,
    AutofillField.GENRE to R.string.song_field_genre,
    AutofillField.TRACK to R.string.song_field_track_number,
    AutofillField.DISC to R.string.song_field_disc_number,
    AutofillField.COMPOSER to R.string.nobody_listens_field_composer,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandidateDetail(
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
                navDescription = stringResource(R.string.nobody_listens_autofill_back),
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
                            else -> stringResource(R.string.nobody_listens_autofill_apply)
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
                                text = stringResource(R.string.nobody_listens_autofill_cover_art),
                                style = NbType.title,
                                color = Nb.text
                            )
                            Text(
                                text = when {
                                    coverLoading -> stringResource(R.string.nobody_listens_autofill_cover_loading)
                                    coverArt == null -> stringResource(R.string.nobody_listens_autofill_cover_none)
                                    else -> stringResource(if (songHasArt) R.string.nobody_listens_autofill_cover_replace else R.string.nobody_listens_autofill_cover_add)
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
                        text = stringResource(R.string.nobody_listens_autofill_choose_fields),
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
                            text = stringResource(R.string.nobody_listens_autofill_loading_details),
                            style = NbType.body,
                            color = Nb.textSecondary
                        )
                    }
                }
            } else if (changedFields.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.nobody_listens_autofill_nothing_new),
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
