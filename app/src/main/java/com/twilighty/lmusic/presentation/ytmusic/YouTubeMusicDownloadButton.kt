package com.twilighty.lmusic.presentation.ytmusic

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.data.ytmusic.YouTubeMusicDownloader
import com.twilighty.lmusic.data.ytmusic.YouTubeMusicTrack
import com.twilighty.lmusic.data.ytmusic.YouTubeMusicUris
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class YouTubeMusicSongActionsViewModel @Inject constructor(
    private val downloader: YouTubeMusicDownloader,
) : ViewModel() {
    val activeVideoId = downloader.activeVideoId
    val savedVideoIds = downloader.savedVideoIds
    val status = downloader.status

    fun delete(song: Song, videoId: String) = downloader.delete(videoId, song.title)

    fun download(song: Song, videoId: String) {
        downloader.enqueue(
            listOf(
                YouTubeMusicTrack(
                    videoId = videoId,
                    title = song.title,
                    artist = song.displayArtist.takeIf { it != "YouTube Music" }.orEmpty(),
                    album = song.album.takeIf { it != "YouTube Music" }.orEmpty(),
                    thumbnailUrl = song.albumArtUriString,
                    durationMs = song.duration,
                )
            )
        )
    }
}

/**
 * Now-playing download button for streamed YouTube songs, in the player's chip style.
 * Shows nothing for other songs. Saves a tagged copy to Music/Nobody Listens/YouTube Music;
 * tapping it again on a downloaded song asks before deleting that copy.
 */
@Composable
fun YouTubeMusicDownloadButton(
    song: Song,
    containerColor: Color,
    contentColor: Color,
    viewModel: YouTubeMusicSongActionsViewModel = hiltViewModel(),
) {
    val videoId = remember(song.contentUriString) { YouTubeMusicUris.videoIdFrom(song.contentUriString) } ?: return
    val context = LocalContext.current
    val activeVideoId by viewModel.activeVideoId.collectAsStateWithLifecycle()
    val savedVideoIds by viewModel.savedVideoIds.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val downloading = activeVideoId == videoId
    val saved = videoId in savedVideoIds
    var confirmDelete by remember { mutableStateOf(false) }

    // The YouTube Music screen shows download progress; here a short toast says how it ended.
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
        onClick = { if (saved) confirmDelete = true else viewModel.download(song, videoId) },
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
            text = { Text("\"${song.title}\" will be deleted from Music/Nobody Listens/YouTube Music. You can still stream it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(song, videoId)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
