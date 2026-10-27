package com.nobodybuilds.listens.presentation.streaming

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.nb.NbActionRow
import kotlinx.coroutines.launch

/**
 * Now-playing download button for a streamed song, in the player's chip style. Tapping
 * it again on a downloaded song asks before deleting the copy in [folder].
 */
@Composable
fun StreamingDownloadButton(
    song: Song,
    downloading: Boolean,
    saved: Boolean,
    status: String?,
    folder: String,
    containerColor: Color,
    contentColor: Color,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    // The service's screen shows download progress; here a short toast says how it ended.
    // A status left over from before this screen opened is not news, so skip it.
    val statusOnOpen = remember { status }
    LaunchedEffect(status) {
        val message = status?.takeIf { it != statusOnOpen } ?: return@LaunchedEffect
        if (message.startsWith("Saved") || message.startsWith("Download failed") ||
            message.startsWith("Removed") || message.startsWith("Could not remove")
        ) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    FilledIconButton(
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        enabled = !downloading,
        onClick = { if (saved) confirmDelete = true else onDownload() },
    ) {
        when {
            downloading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            saved -> Icon(Icons.Rounded.DownloadDone, contentDescription = "Downloaded. Tap to remove")
            else -> Icon(Icons.Rounded.Download, contentDescription = "Download")
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove download?") },
            text = { Text("\"${song.title}\" will be deleted from $folder. You can still stream it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * "Start radio" in the song options sheet: plays the song followed by the service's
 * mix of similar songs, which keeps topping itself up.
 */
@Composable
fun StreamingStartRadioAction(
    song: Song,
    subtitle: String,
    onDismiss: () -> Unit,
    start: suspend () -> List<Song>,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val playerViewModel: PlayerViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val scope = rememberCoroutineScope()
    var loading by remember(song.id) { mutableStateOf(false) }

    NbActionRow(
        text = if (loading) "Starting radio…" else "Start radio",
        subtitle = subtitle,
        icon = rememberVectorPainter(Icons.Rounded.Radio),
        enabled = !loading,
        onClick = {
            scope.launch {
                loading = true
                val songs = runCatching { start() }.getOrDefault(emptyList())
                loading = false
                if (songs.size <= 1) {
                    Toast.makeText(context, "Couldn't start a radio for this song", Toast.LENGTH_SHORT).show()
                } else {
                    playerViewModel.playSongs(songs, songs.first(), "Radio · ${song.title}")
                    onDismiss()
                }
            }
        },
    )
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
