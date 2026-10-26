package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.SmartImage
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbDialog
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbTextField

@Composable
internal fun PlaylistMosaicArt(
    name: String,
    coverImageUri: String?,
    songs: List<Song>,
) {
    val size = 132.dp
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val covers = remember(songs) {
        songs.distinctBy { it.albumId }.take(4)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(NbShapes.lg)
            .background(Nb.canvas)
    ) {
        when {
            !coverImageUri.isNullOrBlank() -> {
                NbArt(model = null, title = name, size = size, shape = NbShapes.lg)
                SmartImage(
                    model = coverImageUri,
                    contentDescription = name,
                    shape = NbShapes.lg,
                    targetSize = Size(sizePx, sizePx),
                    placeHolderBackgroundColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize()
                )
            }
            covers.size >= 4 -> {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    covers.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            pair.forEach { song ->
                                NbArt(
                                    model = song.albumArtUriString,
                                    title = song.album.ifBlank { song.title },
                                    size = (size - 2.dp) / 2,
                                    shape = RectangleShape
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                NbArt(
                    model = covers.firstOrNull()?.albumArtUriString,
                    title = name,
                    size = size,
                    shape = NbShapes.lg
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// SongPickerBottomSheet moved to com.nobodybuilds.listens.presentation.components
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(TextFieldValue(currentName)) }
    val renameTitle = stringResource(R.string.presentation_batch_b_rename_playlist_dialog_title)
    val newNameLabel = stringResource(R.string.presentation_batch_b_new_name)
    val renameAction = stringResource(R.string.action_rename)
    NbDialog(
        onDismissRequest = onDismiss,
        title = renameTitle,
        actions = {
            NbButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = NbButtonStyle.Secondary
            )
            NbButton(
                text = renameAction,
                onClick = { if (newName.text.isNotBlank()) onRename(newName.text) },
                enabled = newName.text.isNotBlank() && newName.text != currentName
            )
        }
    ) {
        NbTextField(
            value = newName,
            onValueChange = { newName = it },
            label = newNameLabel,
            singleLine = true
        )
    }
}
