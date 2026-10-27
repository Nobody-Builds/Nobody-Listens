package com.nobodybuilds.listens.data.ytmusic

import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.SongArtistCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps YouTube Music tracks that are actually played in the songs table, so likes,
 * playlists, stats and the source filter treat them like any other cloud song.
 * Queued tracks are only remembered; each one is saved when its stream is first
 * prepared, so browsing a shelf does not flood the library.
 */
@Singleton
class YouTubeMusicLibrary @Inject constructor(
    private val musicDao: MusicDao,
    okHttpClient: OkHttpClient,
) {
    private val queued = ConcurrentHashMap<String, YouTubeMusicTrack>()
    private val http = YouTubeMusicClients.http(okHttpClient)
    private val _artworkUpdates = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)

    /** (song id, sharper art URL) once a song's HD cover has been found. */
    val artworkUpdates: SharedFlow<Pair<String, String>> = _artworkUpdates.asSharedFlow()

    fun remember(tracks: List<YouTubeMusicTrack>) {
        tracks.forEach { queued[it.videoId] = it }
    }

    /** Search rows often carry no length: store the one from the stream once it is known. */
    suspend fun fillDuration(videoId: String, durationMs: Long) {
        if (durationMs <= 0L) return
        runCatching { withContext(Dispatchers.IO) { musicDao.fillMissingDuration(YouTubeMusicUris.songId(videoId), durationMs) } }
    }

    /** Saves a remembered track the first time it is about to play. */
    suspend fun saveIfQueued(videoId: String) {
        val track = queued.remove(videoId) ?: return
        runCatching { save(track) }.onFailure { Timber.w(it, "Could not save YouTube track $videoId") }
    }

    /** Saves [tracks] now, for a playlist import; returns their song ids, skipping any that failed. */
    suspend fun saveAll(tracks: List<YouTubeMusicTrack>): List<Long> = tracks.mapNotNull { track ->
        queued.remove(track.videoId)
        runCatching { save(track) }
            .onFailure { Timber.w(it, "Could not save YouTube track ${track.videoId}") }
            .map { YouTubeMusicUris.songId(track.videoId) }
            .getOrNull()
    }

    private suspend fun save(track: YouTubeMusicTrack) = withContext(Dispatchers.IO) {
        val listed = YouTubeMusicUris.toEntity(track)
        // Video results come with small 4:3 thumbnails; swap in the HD one when it exists.
        val hdArt = if (track.thumbnailUrl == null || YouTubeMusicThumbnails.isVideoThumbnail(track.thumbnailUrl)) {
            YouTubeMusicThumbnails.bestVideoThumbnail(http, track.videoId)
        } else null
        val song = if (hdArt != null) listed.copy(albumArtUriString = hdArt) else listed
        if (hdArt != null && hdArt != listed.albumArtUriString) _artworkUpdates.tryEmit(song.id.toString() to hdArt)
        musicDao.insertArtistsIgnoreConflicts(listOf(YouTubeMusicUris.toArtist(track)))
        musicDao.insertAlbumsIgnoreConflicts(listOf(YouTubeMusicUris.toAlbum(track)))
        val inserted = musicDao.insertSongsIgnoreConflicts(listOf(song))
        // An existing row only gets a fresh title, artist and cover, so likes,
        // cached lyrics and the date added stay as they were.
        if (inserted.single() == -1L) {
            musicDao.updateStreamedSongMetadata(
                id = song.id,
                title = song.title,
                artistName = song.artistName,
                artistId = song.artistId,
                albumName = song.albumName,
                albumId = song.albumId,
                albumArtUri = song.albumArtUriString,
                duration = song.duration,
            )
        }
        musicDao.deleteCrossRefsBySongIds(listOf(song.id))
        musicDao.insertSongArtistCrossRefs(
            listOf(SongArtistCrossRef(songId = song.id, artistId = song.artistId, isPrimary = true))
        )
    }
}
