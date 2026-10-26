package com.nobodybuilds.listens.shared

import kotlinx.serialization.Serializable

/**
 * One song of the watch's queue, sent with [WearPlaybackCommand.CONTINUE_ON_PHONE] so the phone can carry on
 * where the watch was. [songId] is the phone's song id for songs saved from the phone, or a stream id
 * ([YOUTUBE_PREFIX] + video id, [JIOSAAVN_PREFIX] + song id) that the phone can stream itself.
 */
@Serializable
data class WearHandoffSong(
    val songId: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
) {
    companion object {
        const val YOUTUBE_PREFIX = "yt:"
        const val JIOSAAVN_PREFIX = "jiosaavn:"
    }
}
