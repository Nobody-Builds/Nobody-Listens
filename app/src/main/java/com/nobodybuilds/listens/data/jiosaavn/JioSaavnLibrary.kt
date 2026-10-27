package com.nobodybuilds.listens.data.jiosaavn

import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.SongArtistCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps JioSaavn tracks that are actually played in the songs table, so likes,
 * playlists, stats and the source filter treat them like any other cloud song.
 * Queued tracks are only remembered; each one is saved when its stream is first
 * prepared, so browsing a shelf does not flood the library.
 */
@Singleton
class JioSaavnLibrary @Inject constructor(
    private val musicDao: MusicDao,
) {
    private val queued = ConcurrentHashMap<String, JioSaavnTrack>()

    fun remember(tracks: List<JioSaavnTrack>) {
        tracks.forEach { queued[it.id] = it }
    }

    /** Saves a remembered track the first time it is about to play. */
    suspend fun saveIfQueued(songId: String) {
        val track = queued.remove(songId) ?: return
        runCatching { save(track) }.onFailure { Timber.w(it, "Could not save JioSaavn track $songId") }
    }

    /** Saves [tracks] now, for a playlist import; returns their song ids, skipping any that failed. */
    suspend fun saveAll(tracks: List<JioSaavnTrack>): List<Long> = tracks.mapNotNull { track ->
        queued.remove(track.id)
        runCatching { save(track) }
            .onFailure { Timber.w(it, "Could not save JioSaavn track ${track.id}") }
            .map { JioSaavnUris.songId(track.id) }
            .getOrNull()
    }

    private suspend fun save(track: JioSaavnTrack) = withContext(Dispatchers.IO) {
        val song = JioSaavnUris.toEntity(track)
        musicDao.insertArtistsIgnoreConflicts(listOf(JioSaavnUris.toArtist(track)))
        musicDao.insertAlbumsIgnoreConflicts(listOf(JioSaavnUris.toAlbum(track)))
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
