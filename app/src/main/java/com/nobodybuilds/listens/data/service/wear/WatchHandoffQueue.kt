package com.nobodybuilds.listens.data.service.wear

import com.nobodybuilds.listens.data.jiosaavn.JioSaavnLibrary
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnTrack
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicLibrary
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.shared.WearHandoffSong
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Turns the watch's queue into phone songs, so a song playing on the watch can carry on here. */
class WatchHandoffQueue @Inject constructor(
    private val musicRepository: MusicRepository,
    private val youTube: YouTubeMusicLibrary,
    private val jioSaavn: JioSaavnLibrary,
) {
    /**
     * The songs the phone can play, and where the watch's current song ([currentIndex] in [queue]) is among
     * them. Null when the phone can't play that song.
     */
    suspend fun resolve(queue: List<WearHandoffSong>, currentIndex: Int): Pair<List<Song>, Int>? {
        val phoneIds = queue.map { it.songId }.filter { it.toLongOrNull() != null }
        val phoneSongs = if (phoneIds.isEmpty()) {
            emptyMap()
        } else {
            musicRepository.getSongsByIds(phoneIds).first().associateBy { it.id }
        }
        val songs = queue.mapIndexedNotNull { index, item -> song(item, phoneSongs)?.let { index to it } }
        val start = songs.indexOfFirst { it.first == currentIndex }
        if (start == -1) return null
        return songs.map { it.second } to start
    }

    /** Streams are only remembered here; each is saved to the library when it first plays, as on the phone. */
    private fun song(item: WearHandoffSong, phoneSongs: Map<String, Song>): Song? = when {
        item.songId.startsWith(WearHandoffSong.YOUTUBE_PREFIX) -> {
            val track = YouTubeMusicTrack(
                videoId = item.songId.removePrefix(WearHandoffSong.YOUTUBE_PREFIX),
                title = item.title,
                artist = item.artist,
                album = item.album,
                thumbnailUrl = item.artworkUrl,
            )
            youTube.remember(listOf(track))
            YouTubeMusicUris.toSong(track)
        }
        item.songId.startsWith(WearHandoffSong.JIOSAAVN_PREFIX) -> {
            val track = JioSaavnTrack(
                id = item.songId.removePrefix(WearHandoffSong.JIOSAAVN_PREFIX),
                title = item.title,
                artist = item.artist,
                album = item.album,
                thumbnailUrl = item.artworkUrl,
            )
            jioSaavn.remember(listOf(track))
            JioSaavnUris.toSong(track)
        }
        else -> phoneSongs[item.songId]
    }
}
