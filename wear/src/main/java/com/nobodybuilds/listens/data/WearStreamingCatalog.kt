package com.nobodybuilds.listens.data

import com.nobodybuilds.listens.data.jiosaavn.JioSaavnTrack
import com.nobodybuilds.listens.data.stream.StreamingTrack
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicFormats
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.shared.WearHandoffSong
import com.nobodybuilds.listens.shared.WearPlayerState
import javax.inject.Inject
import javax.inject.Singleton

/** Online services the watch searches and streams from itself, over its own Wi-Fi or LTE. */
enum class WatchStreamingService(val label: String) {
    YOUTUBE_MUSIC("YouTube Music"),
    JIOSAAVN("JioSaavn"),
}

/**
 * One way in to every streaming service, so search and playback treat them alike: the same
 * [StreamingTrack]s the phone's shared streaming screens use, played through `ytmusic://` or `jiosaavn://`.
 */
@Singleton
class WearStreamingCatalog @Inject constructor(
    private val youTube: WearYouTubeRepository,
    private val jioSaavn: WearJioSaavnRepository,
) {
    suspend fun search(service: WatchStreamingService, query: String): List<StreamingTrack> = when (service) {
        WatchStreamingService.YOUTUBE_MUSIC -> youTube.search(query)
        WatchStreamingService.JIOSAAVN -> jioSaavn.search(query)
    }

    fun queueSong(service: WatchStreamingService, track: StreamingTrack): WearQueueSong = when (service) {
        WatchStreamingService.YOUTUBE_MUSIC -> WearQueueSong(
            songId = WearYouTubeRepository.songId(track.key),
            title = track.title,
            artist = track.artist,
            album = (track as? YouTubeMusicTrack)?.album.orEmpty(),
            uri = WearYouTubeRepository.trackUri(track.key),
            artworkUrl = YouTubeMusicFormats.largeThumbnail(track.thumbnailUrl),
        )
        WatchStreamingService.JIOSAAVN -> WearQueueSong(
            songId = WearJioSaavnRepository.songId(track.key),
            title = track.title,
            artist = track.artist,
            album = (track as? JioSaavnTrack)?.album.orEmpty(),
            uri = WearJioSaavnRepository.trackUri(track.key),
            artworkUrl = track.thumbnailUrl,
        )
    }

    /** The phone's streamed song ([WearPlayerState.streamId]) as a watch queue song, or null for other ids. */
    fun queueSongForPhoneStream(state: WearPlayerState): WearQueueSong? {
        val id = state.streamId
        val uri = when {
            id.startsWith(WearHandoffSong.YOUTUBE_PREFIX) -> WearYouTubeRepository.trackUri(id.removePrefix(WearHandoffSong.YOUTUBE_PREFIX))
            id.startsWith(WearHandoffSong.JIOSAAVN_PREFIX) -> WearJioSaavnRepository.trackUri(id.removePrefix(WearHandoffSong.JIOSAAVN_PREFIX))
            else -> return null
        }
        return WearQueueSong(
            songId = id,
            title = state.songTitle,
            artist = state.artistName,
            album = state.albumName,
            uri = uri,
            artworkUrl = state.artworkUrl,
        )
    }
}
