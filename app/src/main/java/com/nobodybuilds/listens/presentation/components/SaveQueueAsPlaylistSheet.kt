package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import kotlinx.coroutines.flow.map
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCheckbox
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbTextField
import com.nobodybuilds.listens.presentation.nb.NbType
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveQueueAsPlaylistSheet(
    songs: List<Song>,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Set<String>) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val animatedAlbumCornerRadius = 60.dp
    val albumShape = remember(animatedAlbumCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedAlbumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedAlbumCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedAlbumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedAlbumCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    var playlistName by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaultName, selection = TextRange(defaultName.length)))
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedSongIds = remember(songs) {
        mutableStateMapOf<String, Boolean>().apply {
            songs.forEach { put(it.id, true) }
        }
    }

    val filteredSongs = remember(searchQuery, songs) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true)
        }
    }

    val hasSelection by remember {
        derivedStateOf { selectedSongIds.any { it.value } }
    }
    val allSelected by remember {
        derivedStateOf { selectedSongIds.isNotEmpty() && selectedSongIds.all { it.value } }
    }

    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
    }

    BackHandler(onBack = { onDismiss() })

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { onDismiss() }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                text = stringResource(R.string.presentation_batch_e_save_as_playlist_sheet_title),
                                style = NbType.h2,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            NbButton(
                                text = if (allSelected) {
                                    stringResource(R.string.presentation_batch_e_deselect_all)
                                } else {
                                    stringResource(R.string.presentation_batch_b_select_all)
                                },
                                onClick = {
                                    val target = !allSelected
                                    selectedSongIds.keys.forEach { selectedSongIds[it] = target }
                                },
                                style = NbButtonStyle.Secondary,
                                size = NbButtonSize.Small,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NbTextField(
                                value = playlistName,
                                onValueChange = { playlistName = it },
                                label = stringResource(R.string.presentation_batch_e_playlist_name_label),
                                singleLine = true,
                                fieldModifier = Modifier.focusRequester(focusRequester)
                            )
                            NbTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = stringResource(R.string.presentation_batch_e_search_songs_to_include_placeholder),
                                singleLine = true,
                                leading = {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailing = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.cd_clear_search),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else null
                            )
                        }
                        NbDivider()
                    }
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.ime)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NbDivider()
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.presentation_batch_e_n_songs_selected,
                                        selectedSongIds.count { it.value },
                                        selectedSongIds.count { it.value }
                                    ),
                                    style = NbType.label,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (playlistName.text.isNotBlank()) {
                                        stringResource(
                                            R.string.presentation_batch_e_save_as_format,
                                            playlistName.text
                                        )
                                    } else {
                                        stringResource(R.string.presentation_batch_e_enter_playlist_name)
                                    },
                                    style = NbType.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            NbButton(
                                text = stringResource(R.string.action_save),
                                onClick = {
                                    if (hasSelection) {
                                        val finalName =
                                            playlistName.text.ifBlank { defaultName }
                                        val chosenIds = selectedSongIds
                                            .filterValues { it }
                                            .keys
                                        onConfirm(finalName, chosenIds)
                                        onDismiss()
                                    }
                                },
                                enabled = hasSelection,
                                size = NbButtonSize.Large,
                                icon = rememberVectorPainter(Icons.Rounded.Check)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding()
                        )
                        .consumeWindowInsets(innerPadding)
                        .imePadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (filteredSongs.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.presentation_batch_e_no_songs_match_query,
                                        searchQuery
                                    ),
                                    style = NbType.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredSongs, key = { it.id }) { song ->
                            val checked = selectedSongIds[song.id] ?: false
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedSongIds[song.id] = !checked
                                    }
                                    .heightIn(min = 64.dp)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NbCheckbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedSongIds[song.id] = isChecked
                                    }
                                )
                                NbArt(
                                    model = song.albumArtUriString,
                                    title = song.album.ifBlank { song.title },
                                    size = 44.dp,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, style = NbType.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        song.displayArtist,
                                        style = NbType.meta,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

internal fun requestQueueSaveAsPlaylist(
    context: Context,
    queue: List<Song>,
    currentQueueSourceName: String,
    playlistViewModel: PlaylistViewModel,
    onRequestSaveAsPlaylist: (
        songs: List<Song>,
        defaultName: String,
        onConfirm: (String, Set<String>) -> Unit
    ) -> Unit
) {
    val res = context.resources
    val defaultName = if (currentQueueSourceName.isNotBlank()) {
        res.getString(
            R.string.presentation_batch_e_queue_named_suffix,
            currentQueueSourceName
        )
    } else {
        res.getString(R.string.presentation_batch_e_queue_current)
    }
    onRequestSaveAsPlaylist(
        queue,
        defaultName
    ) { name, selectedIds ->
        val orderedSelection = queue
            .filter { selectedIds.contains(it.id) }
            .map { it.id }
        if (orderedSelection.isNotEmpty()) {
            playlistViewModel.createPlaylist(
                name = name,
                songIds = orderedSelection,
                isQueueGenerated = true
            )
        }
    }
}
