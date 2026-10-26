package com.twilighty.lmusic.presentation.covers

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.lmusic.data.covers.CoverPaletteChoice
import com.twilighty.lmusic.data.covers.CoverPalettes
import com.twilighty.lmusic.data.covers.CoverStyle
import com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCard
import com.twilighty.lmusic.presentation.nb.NbChip
import com.twilighty.lmusic.presentation.nb.NbEmptyState
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbMeter
import com.twilighty.lmusic.presentation.nb.NbOverline
import com.twilighty.lmusic.presentation.nb.NbSegmented
import com.twilighty.lmusic.presentation.nb.NbSettingRow
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbSongRow
import com.twilighty.lmusic.presentation.nb.NbSwitch
import com.twilighty.lmusic.presentation.nb.NbTopBar
import com.twilighty.lmusic.presentation.nb.NbType

/**
 * Cover studio: makes pattern covers in Daylight colours for songs without artwork.
 *
 * @param mode "batch" for every album missing artwork, anything else for the picker.
 * @param songId opens the studio for one song (and its album).
 */
@Composable
fun CoverStudioScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mode: String? = null,
    songId: String? = null,
    bottomInset: Dp = 0.dp,
    viewModel: CoverStudioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.start(mode, songId) }

    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onWritePermissionResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel) {
        viewModel.writeRequests.collect { sender ->
            writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    val goBack = { if (!viewModel.back()) onBack() }
    BackHandler(enabled = true) { goBack() }

    val saving = state.progress != null
    val title = when (state.mode) {
        CoverStudioMode.PICK -> "Cover studio"
        CoverStudioMode.SINGLE -> state.target?.label ?: "Cover studio"
        CoverStudioMode.BATCH -> "Make covers"
    }
    val kicker = when (state.mode) {
        CoverStudioMode.PICK -> "Pattern covers for songs without artwork"
        CoverStudioMode.SINGLE -> "Cover studio"
        CoverStudioMode.BATCH -> "Cover studio"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Nb.canvas)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomInset + 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top") {
            NbTopBar(
                title = title,
                kicker = kicker,
                titleStyle = NbType.h2,
                navigationIcon = {
                    NbIconButton(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        onClick = goBack
                    )
                }
            )
        }

        state.progress?.let { p ->
            item(key = "progress") {
                SaveProgressCard(p, onCancel = viewModel::cancelSave, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        state.summary?.let { s ->
            item(key = "summary") {
                SaveSummaryCard(s, onDismiss = viewModel::dismissSummary, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        if (state.loading) {
            item(key = "loading") {
                Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Nb.ink)
                }
            }
            return@LazyColumn
        }

        when (state.mode) {
            CoverStudioMode.PICK -> pickerItems(state, viewModel)
            CoverStudioMode.SINGLE -> singleItems(state, viewModel, saving)
            CoverStudioMode.BATCH -> batchItems(state, viewModel, saving)
        }
    }
}

// ---------------------------------------------------------------- picker

private fun androidx.compose.foundation.lazy.LazyListScope.pickerItems(state: CoverStudioState, viewModel: CoverStudioViewModel) {
    val missing = state.missingAlbums
    val shown = if (state.showAllInPicker) state.albums else missing
    item(key = "pick_filter") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NbSegmented(
                options = listOf("Missing artwork", "All"),
                selectedIndex = if (state.showAllInPicker) 1 else 0,
                onSelect = { viewModel.setShowAllInPicker(it == 1) }
            )
            if (missing.isNotEmpty()) {
                NbButton(
                    text = "Make covers for all ${missing.size}…",
                    icon = rememberVectorPainter(Icons.Rounded.AutoAwesome),
                    onClick = viewModel::openBatch,
                    style = NbButtonStyle.Primary,
                    size = NbButtonSize.Small
                )
            }
        }
    }
    if (shown.isEmpty()) {
        item(key = "pick_empty") {
            NbEmptyState(
                title = if (state.showAllInPicker) "No songs on this device" else "Every song has artwork",
                body = if (state.showAllInPicker) "Covers can only be made for files on this phone." else "Switch to All to make a new cover anyway.",
                icon = rememberVectorPainter(Icons.Rounded.Palette),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        return
    }
    items(shown, key = { "pick_${it.id}" }) { album ->
        NbSongRow(
            title = album.label,
            subtitle = listOf(
                album.artist,
                if (album.songs.size == 1) "1 song" else "${album.songs.size} songs",
            ).filter { it.isNotBlank() }.joinToString(" · "),
            artModel = album.artModel,
            artKey = album.key,
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = { viewModel.openAlbum(album) }
        )
    }
}

// ---------------------------------------------------------------- single

private fun androidx.compose.foundation.lazy.LazyListScope.singleItems(
    state: CoverStudioState,
    viewModel: CoverStudioViewModel,
    saving: Boolean,
) {
    val album = state.target ?: return
    item(key = "single_preview") {
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(NbShapes.xl)
                .background(Nb.muted)
                .border(1.dp, Nb.border, NbShapes.xl),
            contentAlignment = Alignment.Center
        ) {
            val preview = state.preview
            if (preview != null) {
                Image(bitmap = preview, contentDescription = "Cover preview", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Nb.ink)
            }
        }
    }
    item(key = "single_meta") {
        Text(
            text = listOf(album.artist, if (album.songs.size == 1) "1 song" else "${album.songs.size} songs")
                .filter { it.isNotBlank() }.joinToString(" · ") + " · nothing is saved until you tap Save",
            style = NbType.caption,
            color = Nb.textMuted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item(key = "single_style") { StylePicker(selected = state.options.style, allowMixed = false, onSelect = viewModel::setStyle) }
    item(key = "single_palette") { PalettePicker(selected = state.options.palette, previewKey = album.key, onSelect = viewModel::setPalette) }
    item(key = "single_toggles") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            NbSettingRow(
                title = "Show title",
                subtitle = "Album name in the bottom-left corner",
                trailing = { NbSwitch(checked = state.options.showText, onCheckedChange = viewModel::setShowText) },
                onClick = { viewModel.setShowText(!state.options.showText) }
            )
            if (state.song != null && album.songs.size > 1) {
                NbSettingRow(
                    title = "Use for the whole album",
                    subtitle = "Every song of ${album.label} gets this cover (${album.songs.size} songs)",
                    trailing = { NbSwitch(checked = state.applyToAlbum, onCheckedChange = viewModel::setApplyToAlbum) },
                    onClick = { viewModel.setApplyToAlbum(!state.applyToAlbum) }
                )
            }
        }
    }
    item(key = "single_actions") {
        val count = state.singleTargets.size
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NbButton(
                text = "Shuffle",
                icon = rememberVectorPainter(Icons.Rounded.Shuffle),
                onClick = viewModel::shuffle,
                enabled = !saving,
                style = NbButtonStyle.Secondary,
            )
            NbButton(
                text = if (count > 1) "Save to $count files" else "Save to file",
                icon = rememberVectorPainter(Icons.Rounded.Save),
                onClick = viewModel::saveSingle,
                enabled = !saving && state.preview != null,
                style = NbButtonStyle.Accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------------------------------------------------------------- batch

private fun androidx.compose.foundation.lazy.LazyListScope.batchItems(
    state: CoverStudioState,
    viewModel: CoverStudioViewModel,
    saving: Boolean,
) {
    if (state.batchAlbums.isEmpty()) {
        item(key = "batch_empty") {
            NbEmptyState(
                title = "Every song has artwork",
                body = "There's nothing to make covers for.",
                icon = rememberVectorPainter(Icons.Rounded.Palette),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        return
    }
    item(key = "batch_intro") {
        Text(
            "${state.batchAlbums.size} albums · ${state.batchSongCount} songs without artwork. Files that already have artwork are left alone.",
            style = NbType.meta,
            color = Nb.textMuted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item(key = "batch_style") { StylePicker(selected = state.options.style, allowMixed = true, onSelect = viewModel::setStyle) }
    item(key = "batch_palette") {
        PalettePicker(selected = state.options.palette, previewKey = state.batchAlbums.first().key, onSelect = viewModel::setPalette)
    }
    item(key = "batch_text") {
        NbSettingRow(
            title = "Show title",
            subtitle = "Album name in the bottom-left corner",
            trailing = { NbSwitch(checked = state.options.showText, onCheckedChange = viewModel::setShowText) },
            onClick = { viewModel.setShowText(!state.options.showText) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item(key = "batch_grid") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NbOverline("Preview")
            val previews = state.batchPreviews
            val slots = state.batchAlbums.take(CoverStudioViewModel.BATCH_PREVIEW_COUNT)
            slots.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { col, album ->
                        val preview = previews.getOrNull(rowIndex * 3 + col)?.takeIf { it.album.id == album.id }
                        Column(Modifier.weight(1f)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(NbShapes.md)
                                    .background(Nb.muted)
                                    .border(1.dp, Nb.border, NbShapes.md),
                                contentAlignment = Alignment.Center
                            ) {
                                if (preview != null) {
                                    Image(bitmap = preview.image, contentDescription = album.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Text(album.label, style = NbType.caption, color = Nb.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.batchAlbums.size > slots.size) {
                Text("and ${state.batchAlbums.size - slots.size} more", style = NbType.caption, color = Nb.textMuted)
            }
        }
    }
    item(key = "batch_actions") {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NbButton(
                text = "Shuffle",
                icon = rememberVectorPainter(Icons.Rounded.Shuffle),
                onClick = viewModel::shuffle,
                enabled = !saving,
                style = NbButtonStyle.Secondary,
            )
            NbButton(
                text = "Save all",
                icon = rememberVectorPainter(Icons.Rounded.Save),
                onClick = viewModel::saveAll,
                enabled = !saving,
                style = NbButtonStyle.Accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------------------------------------------------------------- pickers

@Composable
private fun StylePicker(selected: CoverStyle?, allowMixed: Boolean, onSelect: (CoverStyle?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NbOverline("Style", modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (allowMixed) NbChip(text = "Mixed", selected = selected == null, onClick = { onSelect(null) })
            CoverStyle.entries.forEach { style ->
                NbChip(text = style.label, selected = selected == style, onClick = { onSelect(style) })
            }
        }
    }
}

@Composable
private fun PalettePicker(selected: CoverPaletteChoice, previewKey: String, onSelect: (CoverPaletteChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NbOverline("Colours", modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoverPaletteChoice.entries.forEach { choice ->
                PaletteChip(choice = choice, previewKey = previewKey, selected = choice == selected, onClick = { onSelect(choice) })
            }
        }
    }
}

@Composable
private fun PaletteChip(choice: CoverPaletteChoice, previewKey: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalLMusicHaptics.current
    val swatches = remember(choice, previewKey) {
        if (choice == CoverPaletteChoice.AUTO) null else CoverPalettes.resolve(choice, previewKey).colors.map { Color(it) }
    }
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(NbShapes.pill)
            .background(if (selected) Nb.ink else Nb.canvas)
            .then(if (!selected) Modifier.border(1.dp, Nb.borderStrong, NbShapes.pill) else Modifier)
            .clickable(role = Role.Tab) { haptics.selection(); onClick() }
            .padding(start = 8.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            if (swatches == null) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Nb.accent).border(1.dp, Nb.border, CircleShape))
            } else {
                swatches.forEach { c ->
                    Box(Modifier.size(20.dp).clip(CircleShape).background(c).border(1.dp, Nb.border, CircleShape))
                }
            }
        }
        Text(choice.label, style = NbType.label, color = if (selected) Nb.onInk else Nb.text, maxLines = 1)
    }
}

// ---------------------------------------------------------------- progress / summary

@Composable
private fun SaveProgressCard(progress: CoverProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    NbCard(modifier = modifier.fillMaxWidth(), background = Nb.subtle) {
        NbOverline("Saving covers")
        Spacer(Modifier.size(6.dp))
        Text("${(progress.done + 1).coerceAtMost(progress.total)} of ${progress.total}", style = NbType.h3, color = Nb.text)
        progress.current?.let {
            Text(it.title, style = NbType.meta, color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(10.dp))
        NbMeter(progress = if (progress.total == 0) 0f else progress.done.toFloat() / progress.total)
        Spacer(Modifier.size(10.dp))
        NbButton(text = "Cancel", onClick = onCancel, style = NbButtonStyle.Secondary, size = NbButtonSize.Small)
    }
}

@Composable
private fun SaveSummaryCard(summary: CoverSummary, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    NbCard(modifier = modifier.fillMaxWidth()) {
        NbOverline(if (summary.cancelled) "Stopped" else "Done")
        Spacer(Modifier.size(6.dp))
        Text(
            when (summary.saved) {
                0 -> "No covers saved"
                1 -> "1 cover saved"
                else -> "${summary.saved} covers saved"
            },
            style = NbType.h3,
            color = Nb.text
        )
        if (summary.skipped.isNotEmpty()) {
            Text("${summary.skipped.size} skipped", style = NbType.meta, color = Nb.textMuted)
            Spacer(Modifier.size(6.dp))
            summary.skipped.groupBy { it.second }.forEach { (reason, list) ->
                Text("${reason.label}: ${list.size}", style = NbType.caption, color = Nb.textSecondary)
            }
            summary.skipped.take(6).forEach { (song, reason) ->
                Text(
                    "${song.title} — ${reason.label.lowercase()}",
                    style = NbType.caption,
                    color = Nb.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 480.dp)
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        NbButton(text = "OK", onClick = onDismiss, style = NbButtonStyle.Secondary, size = NbButtonSize.Small)
    }
}
