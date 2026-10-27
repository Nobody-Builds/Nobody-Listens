package com.nobodybuilds.listens.presentation.ytmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRadio
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.presentation.streaming.StreamingStartRadioAction
import dagger.hilt.android.lifecycle.HiltViewModel
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
    StreamingStartRadioAction(
        song = song,
        subtitle = "Similar songs from YouTube Music",
        onDismiss = onDismiss,
        start = { viewModel.start(song, videoId) },
    )
}
