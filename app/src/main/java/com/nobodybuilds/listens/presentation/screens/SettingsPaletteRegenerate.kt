package com.nobodybuilds.listens.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Palette regeneration state for Settings > Developer, shared by its maintenance card,
 * the "choose a song" sheet and the "regenerate all" dialog.
 */
internal class SettingsPaletteRegenerateState {
    var showRegenerateAllPalettesDialog by mutableStateOf(false)
    var showPaletteRegenerateSheet by mutableStateOf(false)
    var isPaletteRegenerateRunning by mutableStateOf(false)
    var isPaletteBulkRegenerateRunning by mutableStateOf(false)
    var paletteBulkCompletedCount by mutableStateOf(0)
    var paletteBulkTotalCount by mutableStateOf(0)
    var paletteSongSearchQuery by mutableStateOf("")
    val isAnyPaletteRegenerateRunning: Boolean
        get() = isPaletteRegenerateRunning || isPaletteBulkRegenerateRunning
}

/** The "choose a song" sheet and the "regenerate all" dialog for album palettes. */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPaletteRegenerateDialogs(
    state: SettingsPaletteRegenerateState,
    paletteRegenerateTargets: List<Song>,
    playerViewModel: PlayerViewModel,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    with(state) {
        val paletteRegenerateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filteredPaletteSongs = remember(paletteRegenerateTargets, paletteSongSearchQuery) {
            val query = paletteSongSearchQuery.trim()
            if (query.isBlank()) {
                paletteRegenerateTargets
            } else {
                paletteRegenerateTargets.filter { song ->
                    song.title.contains(query, ignoreCase = true) ||
                        song.displayArtist.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true)
                }
            }
        }

        if (showPaletteRegenerateSheet) {
            com.nobodybuilds.listens.presentation.haptics.HapticBottomSheetEffect(paletteRegenerateSheetState)
            ModalBottomSheet(
                onDismissRequest = {
                    if (!isAnyPaletteRegenerateRunning) {
                        showPaletteRegenerateSheet = false
                        paletteSongSearchQuery = ""
                    }
                },
                sheetState = paletteRegenerateSheetState
            ) {
                PaletteRegenerateSongSheetContent(
                    songs = filteredPaletteSongs,
                    isRunning = isPaletteRegenerateRunning,
                    searchQuery = paletteSongSearchQuery,
                    onSearchQueryChange = { paletteSongSearchQuery = it },
                    onClearSearch = { paletteSongSearchQuery = "" },
                    onSongClick = { song ->
                        if (isAnyPaletteRegenerateRunning) return@PaletteRegenerateSongSheetContent
                        isPaletteRegenerateRunning = true
                        coroutineScope.launch {
                            val success = playerViewModel.forceRegenerateAlbumPaletteForSong(song)
                            isPaletteRegenerateRunning = false
                            if (success) {
                                showPaletteRegenerateSheet = false
                                paletteSongSearchQuery = ""
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.dialog_palette_regenerated, song.title),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.dialog_palette_regenerate_failed, song.title),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }

        if (showRegenerateAllPalettesDialog) {
            AlertDialog(
                icon = {
                    Icon(
                        Icons.Rounded.Style,
                        null,
                        tint = if (isPaletteBulkRegenerateRunning) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                },
                title = {
                    Text(
                        if (isPaletteBulkRegenerateRunning) {
                            stringResource(R.string.dialog_regenerating_palettes_title)
                        } else {
                            stringResource(R.string.dialog_regenerate_palettes_title)
                        }
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isPaletteBulkRegenerateRunning) {
                                stringResource(R.string.dialog_regenerate_palettes_progress_body, paletteBulkTotalCount)
                            } else {
                                stringResource(R.string.dialog_regenerate_palettes_confirm_body, paletteRegenerateTargets.size)
                            }
                        )

                        if (isPaletteBulkRegenerateRunning) {
                            val progress = if (paletteBulkTotalCount > 0) {
                                paletteBulkCompletedCount.toFloat() / paletteBulkTotalCount.toFloat()
                            } else {
                                0f
                            }

                            LinearWavyProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )

                            Text(
                                text = stringResource(R.string.dialog_palette_progress_format, paletteBulkCompletedCount, paletteBulkTotalCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                onDismissRequest = {
                    if (!isPaletteBulkRegenerateRunning) {
                        showRegenerateAllPalettesDialog = false
                    }
                },
                confirmButton = {
                    if (isPaletteBulkRegenerateRunning) {
                        TextButton(
                            onClick = {},
                            enabled = false
                        ) {
                            Text(stringResource(R.string.working_ellipsis))
                        }
                    } else {
                        TextButton(
                            onClick = {
                                isPaletteBulkRegenerateRunning = true
                                paletteBulkCompletedCount = 0
                                paletteBulkTotalCount = paletteRegenerateTargets.size

                                coroutineScope.launch {
                                    var successCount = 0
                                    paletteRegenerateTargets.forEachIndexed { index, song ->
                                        if (playerViewModel.forceRegenerateAlbumPaletteForSong(song)) {
                                            successCount++
                                        }
                                        paletteBulkCompletedCount = index + 1
                                    }

                                    isPaletteBulkRegenerateRunning = false
                                    showRegenerateAllPalettesDialog = false

                                    val totalCount = paletteRegenerateTargets.size
                                    Toast.makeText(
                                        context,
                                        if (successCount == totalCount) {
                                            context.getString(R.string.toast_regenerated_palettes_all, successCount)
                                        } else {
                                            context.getString(R.string.toast_regenerated_palettes_partial, successCount, totalCount)
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.dialog_regenerate))
                        }
                    }
                },
                dismissButton = {
                    if (!isPaletteBulkRegenerateRunning) {
                        TextButton(
                            onClick = { showRegenerateAllPalettesDialog = false }
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PaletteRegenerateSongSheetContent(
    songs: List<Song>,
    isRunning: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.palette_force_regenerate_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.palette_force_regenerate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isRunning,
            placeholder = { Text(stringResource(R.string.search_songs_by_title_artist_album)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = onClearSearch,
                        enabled = !isRunning
                    ) {
                        Icon(Icons.Rounded.ClearAll, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        if (isRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.palette_regenerating_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (songs.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.search_no_songs_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(songs, key = { it.id }) { song ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isRunning) { onSongClick(song) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (song.album.isNotBlank()) {
                                Text(
                                    text = song.album,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
