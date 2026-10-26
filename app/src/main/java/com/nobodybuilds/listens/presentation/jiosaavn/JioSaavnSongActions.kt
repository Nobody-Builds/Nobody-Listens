package com.nobodybuilds.listens.presentation.jiosaavn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnAudioQuality
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnDownloader
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnRadio
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnRepository
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.streaming.StreamingDownloadButton
import com.nobodybuilds.listens.presentation.streaming.StreamingStartRadioAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class JioSaavnSongActionsViewModel @Inject constructor(
    private val downloader: JioSaavnDownloader,
    private val radio: JioSaavnRadio,
) : ViewModel() {
    val activeSongId = downloader.activeSongId
    val savedSongIds = downloader.savedSongIds
    val status = downloader.status

    fun download(song: Song, id: String) = downloader.enqueue(listOf(JioSaavnUris.toTrack(song, id)))

    fun delete(song: Song, id: String) = downloader.delete(id, song.title)

    suspend fun startRadio(song: Song, id: String): List<Song> = radio.start(JioSaavnUris.toTrack(song, id))
}

/** Backs the JioSaavn rows in Settings › Playback. */
@HiltViewModel
class JioSaavnSettingsViewModel @Inject constructor(
    private val repository: JioSaavnRepository,
) : ViewModel() {
    val streamQuality: StateFlow<JioSaavnAudioQuality> = repository.streamQuality
    val downloadQuality: StateFlow<JioSaavnAudioQuality> = repository.downloadQuality

    fun setStreamQuality(quality: JioSaavnAudioQuality) = repository.setStreamQuality(quality)

    fun setDownloadQuality(quality: JioSaavnAudioQuality) = repository.setDownloadQuality(quality)
}

/**
 * Now-playing download button for streamed JioSaavn songs. Shows nothing for other
 * songs. Saves a tagged .m4a to Music/Nobody Listens/JioSaavn.
 */
@Composable
fun JioSaavnDownloadButton(
    song: Song,
    containerColor: Color,
    contentColor: Color,
    viewModel: JioSaavnSongActionsViewModel = hiltViewModel(),
) {
    val id = remember(song.contentUriString) { JioSaavnUris.songIdFrom(song.contentUriString) } ?: return
    val activeSongId by viewModel.activeSongId.collectAsStateWithLifecycle()
    val savedSongIds by viewModel.savedSongIds.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    StreamingDownloadButton(
        song = song,
        downloading = activeSongId == id,
        saved = id in savedSongIds,
        status = status,
        folder = "Music/Nobody Listens/JioSaavn",
        containerColor = containerColor,
        contentColor = contentColor,
        onDownload = { viewModel.download(song, id) },
        onDelete = { viewModel.delete(song, id) },
    )
}

/** "Start radio" in the song options sheet, for JioSaavn songs only. */
@Composable
fun JioSaavnStartRadioAction(
    song: Song,
    onDismiss: () -> Unit,
    viewModel: JioSaavnSongActionsViewModel = hiltViewModel(),
) {
    val id = remember(song.contentUriString) { JioSaavnUris.songIdFrom(song.contentUriString) } ?: return
    StreamingStartRadioAction(
        song = song,
        subtitle = "Similar songs from JioSaavn",
        onDismiss = onDismiss,
        start = { viewModel.startRadio(song, id) },
    )
}
