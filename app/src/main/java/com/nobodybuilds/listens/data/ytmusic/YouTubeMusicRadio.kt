package com.nobodybuilds.listens.data.ytmusic

import com.nobodybuilds.listens.data.model.Song
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Radio for a YouTube song: YouTube's own "up next" mix, topped up as it plays so the
 * queue never runs dry. Only YouTube songs start or extend a radio; local queues are
 * never touched.
 */
@Singleton
class YouTubeMusicRadio @Inject constructor(
    private val repository: YouTubeMusicRepository,
    private val library: YouTubeMusicLibrary,
) {
    private val lock = Mutex()
    private var continuation: String? = null
    /** Song ids of the active radio, to tell it apart from any other queue. */
    private val radioSongIds = ConcurrentHashMap.newKeySet<String>()

    /** Starts a radio from [seed]; returns the songs to play, the seed first. */
    suspend fun start(seed: YouTubeMusicTrack): List<Song> = lock.withLock {
        val page = repository.radio(seed.videoId)
        continuation = page.continuation
        radioSongIds.clear()
        val tracks = (listOf(seed) + page.tracks.filterNot { it.videoId == seed.videoId }).distinctBy { it.videoId }
        library.remember(tracks)
        tracks.map(YouTubeMusicUris::toSong).also { songs -> radioSongIds += songs.map { it.id } }
    }

    fun isRadioSong(songId: String?): Boolean = songId != null && songId in radioSongIds

    /** The next batch for the active radio, or empty when it has run out. */
    suspend fun more(): List<Song> = lock.withLock {
        val token = continuation ?: return emptyList()
        val page = runCatching { repository.radioMore(token) }.getOrNull() ?: return emptyList()
        continuation = page.continuation
        val fresh = page.tracks.map(YouTubeMusicUris::toSong).filterNot { it.id in radioSongIds }
        library.remember(page.tracks)
        radioSongIds += fresh.map { it.id }
        fresh
    }
}
