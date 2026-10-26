package com.twilighty.lmusic.presentation.ytmusic

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.data.ytmusic.YouTubeMusicRadio
import com.twilighty.lmusic.data.ytmusic.YouTubeMusicTrack
import com.twilighty.lmusic.data.ytmusic.YouTubeMusicUris
import com.twilighty.lmusic.presentation.viewmodel.PlayerViewModel
import com.twilighty.lmusic.presentation.nb.NbActionRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeMusicRadioViewModel @Inject constructor(
    private val radio: YouTubeMusicRadio,
) : ViewModel() {
    suspend fun start(song: Song, videoId: String): List<Song> = radio.start(
        YouTubeMusicTrack(
            videoId = videoId,
            title = song.title,
            artist = song.displayArtist.takeIf { it != "YouTube Music" }.orEmpty(),
            album = song.album.takeIf { it != "YouTube Music" }.orEmpty(),
            thumbnailUrl = song.albumArtUriString,
            durationMs = song.duration,
        )
    )
}

/**
 * "Start radio" in the song options sheet, for YouTube songs only: plays the song
 * followed by YouTube's mix of similar songs, which keeps topping itself up.
 */
@Composable
fun YouTubeMusicStartRadioAction(
    song: Song,
    onDismiss: () -> Unit,
    viewModel: YouTubeMusicRadioViewModel = hiltViewModel(),
) {
    val videoId = remember(song.contentUriString) { YouTubeMusicUris.videoIdFrom(song.contentUriString) } ?: return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val playerViewModel: PlayerViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val scope = rememberCoroutineScope()
    var loading by remember(song.id) { mutableStateOf(false) }

    NbActionRow(
        text = if (loading) "Starting radio…" else "Start radio",
        subtitle = "Similar songs from YouTube Music",
        icon = rememberVectorPainter(Icons.Rounded.Radio),
        enabled = !loading,
        onClick = {
            scope.launch {
                loading = true
                val songs = runCatching { viewModel.start(song, videoId) }.getOrDefault(emptyList())
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
