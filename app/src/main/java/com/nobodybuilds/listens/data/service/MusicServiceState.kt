package com.nobodybuilds.listens.data.service

/** Playback flags shared by [MusicService] and the helpers it delegates to. */
internal class MusicServiceState {
    var favoriteSongIds = emptySet<String>()
    var keepPlayingInBackground = true
    var isManualShuffleEnabled = false
    var persistentShuffleEnabled = false
    var isPlaybackUnloadInProgress = false

    fun isSongFavorite(songId: String?): Boolean {
        return songId != null && favoriteSongIds.contains(songId)
    }
}
