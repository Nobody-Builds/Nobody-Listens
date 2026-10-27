package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.viewmodel.WearWatchPlaylistsViewModel
import com.nobodybuilds.listens.shared.WearLibraryItem

/** Playlists saved to the watch. They play with no phone. */
@Composable
fun WatchPlaylistsScreen(
    onPlaylistClick: (playlistId: String, name: String) -> Unit,
    viewModel: WearWatchPlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    val saving by viewModel.savingPlaylistIds.collectAsState()

    WatchListScaffold {
        item { Spacer(modifier = Modifier.height(2.dp)) }
        item { WatchListTitle(stringResource(R.string.wear_watch_playlists)) }
        if (playlists.isEmpty()) {
            item { WatchListMessage(stringResource(R.string.wear_watch_playlists_empty)) }
        }
        items(playlists.size) { index ->
            val playlist = playlists[index]
            val count = stringResource(R.string.wear_playlist_saved_count, playlist.savedCount, playlist.songCount)
            WatchListChip(
                title = playlist.name,
                subtitle = if (playlist.id in saving) stringResource(R.string.wear_saving_playlist) else count,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                iconTint = LocalWearPalette.current.shuffleActive,
            ) { onPlaylistClick(playlist.id, playlist.name) }
        }
    }
}

/** One saved playlist: play it, play from a song, or remove it from the watch. */
@Composable
fun WatchPlaylistScreen(
    playlistId: String,
    name: String,
    onRemoved: () -> Unit,
    viewModel: WearWatchPlaylistsViewModel = hiltViewModel(),
) {
    val songsFlow = remember(playlistId) { viewModel.songs(playlistId) }
    val songs by songsFlow.collectAsState(initial = emptyList())
    val saving by viewModel.savingPlaylistIds.collectAsState()
    val palette = LocalWearPalette.current

    WatchListScaffold {
        item { Spacer(modifier = Modifier.height(2.dp)) }
        item { WatchListTitle(name) }
        item {
            WatchListChip(
                title = stringResource(R.string.wear_play_all),
                subtitle = stringResource(
                    R.string.wear_playlist_saved_count,
                    songs.count { it.saved != null },
                    songs.size,
                ),
                icon = Icons.Rounded.PlayArrow,
                iconTint = palette.textPrimary,
                enabled = songs.any { it.saved != null },
            ) { viewModel.play(playlistId) }
        }
        items(songs.size) { index ->
            val song = songs[index]
            val status = when {
                song.saved != null -> song.entry.artist
                playlistId in saving -> stringResource(R.string.wear_saving)
                else -> stringResource(R.string.wear_not_on_watch_yet)
            }
            WatchListChip(
                title = song.entry.title,
                subtitle = status,
                icon = if (song.saved != null) Icons.Rounded.MusicNote else Icons.Rounded.HourglassTop,
                enabled = song.saved != null,
            ) { viewModel.play(playlistId, fromSongId = song.entry.songId) }
        }
        item {
            WatchListChip(
                title = stringResource(R.string.wear_remove_playlist),
                subtitle = "",
                icon = Icons.Rounded.Delete,
                iconTint = palette.textError,
            ) {
                viewModel.deletePlaylist(playlistId)
                onRemoved()
            }
        }
    }
}

/** Shown on a phone playlist's song list: saves the whole playlist to the watch. */
@Composable
fun SavePlaylistToWatchChip(
    playlistId: String,
    name: String,
    items: List<WearLibraryItem>,
    viewModel: WearWatchPlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    val saving by viewModel.savingPlaylistIds.collectAsState()
    val savedPlaylist = playlists.firstOrNull { it.id == playlistId }
    val isSaving = playlistId in saving
    WatchListChip(
        title = stringResource(
            when {
                isSaving -> R.string.wear_saving_playlist
                savedPlaylist != null -> R.string.wear_update_playlist_on_watch
                else -> R.string.wear_save_playlist_to_watch
            }
        ),
        subtitle = savedPlaylist?.let {
            stringResource(R.string.wear_playlist_saved_count, it.savedCount, it.songCount)
        }.orEmpty(),
        icon = if (savedPlaylist != null) Icons.Rounded.Watch else Icons.Rounded.Download,
        iconTint = LocalWearPalette.current.textPrimary,
        enabled = !isSaving,
    ) { viewModel.savePlaylist(playlistId, name, items) }
}
