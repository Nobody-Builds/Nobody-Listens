package com.nobodybuilds.listens.data

import android.net.Uri
import androidx.media3.common.Player

/**
 * Local player state mirroring WearPlayerState structure for unified UI.
 */
data class WearLocalPlayerState(
    val songId: String = "",
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val canToggleFavorite: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
) {
    val isEmpty: Boolean get() = songId.isEmpty()
}

data class WearQueueSong(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    /** Cover for songs with no embedded art, such as YouTube streams. */
    val artworkUrl: String? = null,
)
