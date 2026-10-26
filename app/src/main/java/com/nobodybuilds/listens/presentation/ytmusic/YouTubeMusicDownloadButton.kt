package com.nobodybuilds.listens.presentation.ytmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicDownloader
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.presentation.streaming.StreamingDownloadButton
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
    val activeVideoId by viewModel.activeVideoId.collectAsStateWithLifecycle()
    val savedVideoIds by viewModel.savedVideoIds.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    StreamingDownloadButton(
        song = song,
        downloading = activeVideoId == videoId,
        saved = videoId in savedVideoIds,
        status = status,
        folder = "Music/Nobody Listens/YouTube Music",
        containerColor = containerColor,
        contentColor = contentColor,
        onDownload = { viewModel.download(song, videoId) },
        onDelete = { viewModel.delete(song, videoId) },
    )
}
