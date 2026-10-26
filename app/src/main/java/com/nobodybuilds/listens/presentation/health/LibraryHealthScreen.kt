package com.nobodybuilds.listens.presentation.health

import android.app.Activity
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.MetadataAutofillDialog
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbMeter
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbSongRow
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbTopBar
import com.nobodybuilds.listens.presentation.nb.NbType

private const val SECTION_PAGE = 60

@Composable
fun LibraryHealthScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    bottomInset: Dp = 0.dp,
    viewModel: LibraryHealthViewModel = hiltViewModel(),
    onMakeCovers: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val report by viewModel.report.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()

    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionSection by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var shownPerSection by remember { mutableStateOf(mapOf<HealthSection, Int>()) }
    var reviewSong by remember { mutableStateOf<Song?>(null) }

    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onWritePermissionResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel) {
        viewModel.writeRequests.collect { sender ->
            writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    val savedText = stringResource(R.string.health_msg_saved)
    val failedText = stringResource(R.string.health_msg_save_failed)
    val deniedText = stringResource(R.string.health_msg_permission_denied)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                HealthMessage.Saved -> savedText
                HealthMessage.SaveFailed -> failedText
                HealthMessage.PermissionDenied -> deniedText
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    val running = progress != null
    val songsById = remember(report) {
        report?.sections?.values?.flatten()?.associateBy { it.id }.orEmpty()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Nb.canvas)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomInset + 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "top") {
                NbTopBar(
                    title = stringResource(R.string.health_title),
                    kicker = report?.let {
                        pluralStringResource(R.plurals.health_songs_checked, it.totalSongs, it.totalSongs)
                    },
                    navigationIcon = {
                        NbIconButton(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.health_back),
                            onClick = onBack
                        )
                    }
                )
            }

            item(key = "intro") {
                Text(
                    text = stringResource(R.string.health_intro, LibraryHealthViewModel.HIGH_CONFIDENCE),
                    style = NbType.meta,
                    color = Nb.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            progress?.let { p ->
                item(key = "progress") {
                    BatchProgressCard(progress = p, onCancel = viewModel::cancelBatch, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            summary?.let { s ->
                item(key = "summary") {
                    BatchSummaryCard(summary = s, onDismiss = viewModel::dismissSummary, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            if (review.isNotEmpty()) {
                item(key = "review") {
                    ReviewCard(
                        items = review,
                        enabled = !running,
                        onReview = { reviewSong = it },
                        onDismissItem = { viewModel.removeFromReview(it.id) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            val currentReport = report
            if (currentReport == null) {
                item(key = "loading") {
                    Row(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Nb.ink)
                    }
                }
            } else {
                HealthSection.entries.forEach { section ->
                    val isExpanded = expanded == section.name
                    item(key = "section_${section.name}") {
                        SectionHeaderCard(
                            section = section,
                            count = currentReport.count(section),
                            expanded = isExpanded,
                            onToggle = {
                                expanded = if (isExpanded) null else section.name
                                if (selectionSection != section.name) {
                                    selectionSection = null
                                    selectedIds = emptySet()
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (isExpanded) {
                        if (section == HealthSection.DUPLICATES) {
                            duplicateItems(
                                groups = currentReport.duplicateGroups,
                                onDelete = { song ->
                                    val activity = context as? Activity ?: return@duplicateItems
                                    playerViewModel.deleteFromDevice(activity, song)
                                }
                            )
                        } else {
                            val songs = currentReport.sections[section].orEmpty()
                            val selecting = selectionSection == section.name
                            item(key = "actions_${section.name}") {
                                SectionActions(
                                    count = songs.size,
                                    selecting = selecting,
                                    selectedCount = selectedIds.size,
                                    enabled = !running && songs.isNotEmpty(),
                                    onFixAll = { viewModel.startBatch(songs) },
                                    onToggleSelect = {
                                        selectionSection = if (selecting) null else section.name
                                        selectedIds = emptySet()
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            if (section == HealthSection.MISSING_ARTWORK && songs.isNotEmpty() && onMakeCovers != null) {
                                item(key = "make_covers") {
                                    NbButton(
                                        text = "Make covers…",
                                        icon = rememberVectorPainter(Icons.Rounded.Palette),
                                        onClick = onMakeCovers,
                                        enabled = !running,
                                        style = NbButtonStyle.Secondary,
                                        size = NbButtonSize.Small,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                            val limit = shownPerSection[section] ?: SECTION_PAGE
                            items(songs.take(limit), key = { "${section.name}_${it.id}" }) { song ->
                                val checked = song.id in selectedIds
                                NbSongRow(
                                    title = song.title.ifBlank { song.path.substringAfterLast('/') },
                                    subtitle = listOf(song.displayArtist, song.album)
                                        .filter { it.isNotBlank() && it != "<unknown>" }
                                        .joinToString(" · ")
                                        .ifBlank { song.path.substringAfterLast('/') },
                                    artModel = song.albumArtUriString,
                                    artKey = song.album.ifBlank { song.title },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    trailing = if (selecting) {
                                        {
                                            Icon(
                                                painter = rememberVectorPainter(if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked),
                                                contentDescription = null,
                                                tint = if (checked) Nb.ink else Nb.textMuted,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = if (selecting) {
                                        { selectedIds = if (checked) selectedIds - song.id else selectedIds + song.id }
                                    } else null
                                )
                            }
                            if (songs.size > limit) {
                                item(key = "more_${section.name}") {
                                    NbButton(
                                        text = stringResource(R.string.health_show_more, songs.size - limit),
                                        onClick = { shownPerSection = shownPerSection + (section to limit + SECTION_PAGE) },
                                        style = NbButtonStyle.Quiet,
                                        size = NbButtonSize.Small,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selection bar
        if (selectionSection != null && selectedIds.isNotEmpty() && !running) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + com.nobodybuilds.listens.presentation.components.MiniPlayerHeight)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(NbShapes.xl)
                    .background(Nb.canvas)
                    .border(1.dp, Nb.border, NbShapes.xl)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    pluralStringResource(R.plurals.health_selected, selectedIds.size, selectedIds.size),
                    style = NbType.label,
                    color = Nb.text,
                    modifier = Modifier.weight(1f)
                )
                NbButton(
                    text = stringResource(R.string.health_autofill_selected),
                    icon = rememberVectorPainter(Icons.Rounded.AutoAwesome),
                    style = NbButtonStyle.Accent,
                    size = NbButtonSize.Small,
                    onClick = {
                        val chosen = selectedIds.mapNotNull { songsById[it] }
                        viewModel.startBatch(chosen)
                        selectionSection = null
                        selectedIds = emptySet()
                    }
                )
            }
        }
    }

    reviewSong?.let { song ->
        MetadataAutofillDialog(
            song = song,
            current = viewModel.currentFields(song),
            onDismiss = { reviewSong = null },
            onApply = { result -> viewModel.applyReviewResult(song, result) }
        )
    }
}

@Composable
private fun sectionTitle(section: HealthSection): String = stringResource(
    when (section) {
        HealthSection.MISSING_ARTWORK -> R.string.health_section_artwork
        HealthSection.MISSING_YEAR -> R.string.health_section_year
        HealthSection.MISSING_ALBUM_ARTIST -> R.string.health_section_album_artist
        HealthSection.MISSING_GENRE -> R.string.health_section_genre
        HealthSection.UNKNOWN_ARTIST_ALBUM -> R.string.health_section_unknown
        HealthSection.DUPLICATES -> R.string.health_section_duplicates
    }
)

@Composable
private fun sectionSubtitle(section: HealthSection): String = stringResource(
    when (section) {
        HealthSection.MISSING_ARTWORK -> R.string.health_section_artwork_desc
        HealthSection.MISSING_YEAR -> R.string.health_section_year_desc
        HealthSection.MISSING_ALBUM_ARTIST -> R.string.health_section_album_artist_desc
        HealthSection.MISSING_GENRE -> R.string.health_section_genre_desc
        HealthSection.UNKNOWN_ARTIST_ALBUM -> R.string.health_section_unknown_desc
        HealthSection.DUPLICATES -> R.string.health_section_duplicates_desc
    }
)

@Composable
private fun SectionHeaderCard(
    section: HealthSection,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NbCard(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        onClick = onToggle,
        background = if (expanded) Nb.subtle else Nb.canvas,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(sectionTitle(section), style = NbType.title, color = Nb.text)
                Text(sectionSubtitle(section), style = NbType.caption, color = Nb.textMuted)
            }
            NbStatusChip(
                text = count.toString(),
                status = if (count == 0) NbStatus.Success else if (section == HealthSection.DUPLICATES) NbStatus.Warning else NbStatus.Neutral
            )
            Icon(
                painter = rememberVectorPainter(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore),
                contentDescription = null,
                tint = Nb.textMuted
            )
        }
    }
}

@Composable
private fun SectionActions(
    count: Int,
    selecting: Boolean,
    selectedCount: Int,
    enabled: Boolean,
    onFixAll: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count == 0) {
        Text(
            stringResource(R.string.health_section_clean),
            style = NbType.meta,
            color = Nb.successText,
            modifier = modifier.padding(vertical = 4.dp)
        )
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NbButton(
            text = stringResource(R.string.health_fix_all, count),
            icon = rememberVectorPainter(Icons.Rounded.AutoAwesome),
            onClick = onFixAll,
            enabled = enabled,
            style = NbButtonStyle.Primary,
            size = NbButtonSize.Small
        )
        NbButton(
            text = stringResource(if (selecting) R.string.health_select_done else R.string.health_select),
            onClick = onToggleSelect,
            enabled = enabled,
            style = NbButtonStyle.Secondary,
            size = NbButtonSize.Small
        )
        if (selecting && selectedCount == 0) {
            Text(stringResource(R.string.health_select_hint), style = NbType.caption, color = Nb.textMuted)
        }
    }
}

@Composable
private fun BatchProgressCard(progress: BatchProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    NbCard(modifier = modifier.fillMaxWidth(), background = Nb.subtle) {
        NbOverline(stringResource(R.string.health_progress_overline))
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(R.string.health_progress, (progress.done + 1).coerceAtMost(progress.total), progress.total),
            style = NbType.h3,
            color = Nb.text
        )
        progress.current?.let {
            Text(
                "${it.title} · ${it.displayArtist}",
                style = NbType.meta,
                color = Nb.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(12.dp))
        NbMeter(progress = if (progress.total == 0) 0f else progress.done.toFloat() / progress.total)
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.health_progress_hint),
                style = NbType.caption,
                color = Nb.textMuted,
                modifier = Modifier.weight(1f)
            )
            NbButton(text = stringResource(R.string.health_cancel), onClick = onCancel, style = NbButtonStyle.Secondary, size = NbButtonSize.Small)
        }
    }
}

@Composable
private fun skipReasonText(reason: SkipReason): String = stringResource(
    when (reason) {
        SkipReason.FILE_MISSING -> R.string.health_skip_file_missing
        SkipReason.NO_TITLE -> R.string.health_skip_no_title
        SkipReason.NO_MATCH -> R.string.health_skip_no_match
        SkipReason.LOW_CONFIDENCE -> R.string.health_skip_low_confidence
        SkipReason.NOTHING_NEW -> R.string.health_skip_nothing_new
        SkipReason.NETWORK -> R.string.health_skip_network
        SkipReason.SAVE_FAILED -> R.string.health_skip_save_failed
        SkipReason.PERMISSION_DENIED -> R.string.health_skip_permission
        SkipReason.UNREADABLE -> R.string.health_skip_unreadable
    }
)

@Composable
private fun BatchSummaryCard(summary: BatchSummary, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var showDetails by remember(summary) { mutableStateOf(false) }
    NbCard(modifier = modifier.fillMaxWidth().animateContentSize()) {
        NbOverline(stringResource(if (summary.cancelled) R.string.health_summary_cancelled else R.string.health_summary_overline))
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(R.string.health_summary, summary.fixed.size, summary.skipped.size),
            style = NbType.h3,
            color = Nb.text
        )
        val reasons = summary.skipped.groupingBy { it.reason }.eachCount()
        if (reasons.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
            reasons.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                Text("$count · ${skipReasonText(reason)}", style = NbType.meta, color = Nb.textMuted)
            }
        }
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (summary.fixed.isNotEmpty() || summary.skipped.isNotEmpty()) {
                NbButton(
                    text = stringResource(if (showDetails) R.string.health_hide_details else R.string.health_show_details),
                    onClick = { showDetails = !showDetails },
                    style = NbButtonStyle.Secondary,
                    size = NbButtonSize.Small
                )
            }
            NbButton(text = stringResource(R.string.health_done), onClick = onDismiss, style = NbButtonStyle.Quiet, size = NbButtonSize.Small)
        }
        if (showDetails) {
            Spacer(Modifier.size(8.dp))
            summary.fixed.take(100).forEach { fixed ->
                DetailLine(
                    title = fixed.song.title,
                    detail = stringResource(R.string.health_fixed_detail, fixed.fields.size + if (fixed.addedArtwork) 1 else 0),
                    good = true
                )
            }
            summary.skipped.take(100).forEach { skipped ->
                DetailLine(title = skipped.song.title, detail = skipReasonText(skipped.reason), good = false)
            }
        }
    }
}

@Composable
private fun DetailLine(title: String, detail: String, good: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(NbShapes.pill)
                .background(if (good) Nb.successText else Nb.borderStrong)
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = NbType.meta, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(detail, style = NbType.caption, color = Nb.textMuted, maxLines = 1)
    }
}

@Composable
private fun ReviewCard(
    items: List<ReviewSong>,
    enabled: Boolean,
    onReview: (Song) -> Unit,
    onDismissItem: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(true) }
    NbCard(modifier = modifier.fillMaxWidth().animateContentSize(), background = Nb.tintPeach, borderColor = Nb.border) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.health_review_title), style = NbType.title, color = Nb.text)
                Text(stringResource(R.string.health_review_desc), style = NbType.caption, color = Nb.textMuted)
            }
            NbStatusChip(text = items.size.toString(), status = NbStatus.Accent)
        }
        if (open) {
            Spacer(Modifier.size(4.dp))
            items.forEach { item ->
                NbSongRow(
                    title = item.song.title,
                    subtitle = item.confidence?.let { stringResource(R.string.health_review_match, it) } ?: item.song.displayArtist,
                    artModel = item.song.albumArtUriString,
                    artKey = item.song.album.ifBlank { item.song.title },
                    trailing = {
                        NbButton(
                            text = stringResource(R.string.health_review_action),
                            onClick = { onReview(item.song) },
                            enabled = enabled,
                            style = NbButtonStyle.Secondary,
                            size = NbButtonSize.Small
                        )
                        NbButton(
                            text = stringResource(R.string.health_review_skip),
                            onClick = { onDismissItem(item.song) },
                            style = NbButtonStyle.Quiet,
                            size = NbButtonSize.Small
                        )
                    }
                )
            }
        }
    }
}

private fun LazyListScope.duplicateItems(groups: List<DuplicateGroup>, onDelete: (Song) -> Unit) {
    if (groups.isEmpty()) {
        item(key = "dup_empty") {
            Text(
                stringResource(R.string.health_duplicates_none),
                style = NbType.meta,
                color = Nb.successText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        return
    }
    item(key = "dup_hint") {
        Text(
            stringResource(R.string.health_duplicates_hint),
            style = NbType.caption,
            color = Nb.textMuted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    items(groups, key = { "dup_${it.key}" }) { group ->
        DuplicateGroupCard(group = group, onDelete = onDelete, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun DuplicateGroupCard(group: DuplicateGroup, onDelete: (Song) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    NbCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text(group.title, style = NbType.title, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(R.plurals.health_copies, group.copies.size, group.artist, group.copies.size),
            style = NbType.caption,
            color = Nb.textMuted
        )
        group.copies.forEach { copy ->
            Spacer(Modifier.size(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(NbShapes.md)
                    .background(Nb.subtle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        copy.song.path,
                        style = NbType.caption,
                        color = Nb.text,
                        maxLines = 2,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                    val format = copy.song.mimeType?.substringAfter('/')?.uppercase()
                        ?: copy.song.path.substringAfterLast('.', "").uppercase()
                    val bitrate = copy.song.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
                    val size = Formatter.formatShortFileSize(context, copy.sizeBytes)
                    val duration = copy.song.duration / 1000
                    Text(
                        listOfNotNull(format.ifBlank { null }, bitrate, size, "%d:%02d".format(duration / 60, duration % 60)).joinToString(" · "),
                        style = NbType.caption,
                        color = Nb.textMuted
                    )
                }
                NbButton(
                    text = stringResource(R.string.health_delete),
                    onClick = { onDelete(copy.song) },
                    style = NbButtonStyle.DangerQuiet,
                    size = NbButtonSize.Small
                )
            }
        }
    }
}
