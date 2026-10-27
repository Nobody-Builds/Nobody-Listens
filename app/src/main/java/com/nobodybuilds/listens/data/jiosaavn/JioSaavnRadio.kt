package com.nobodybuilds.listens.data.jiosaavn

import com.nobodybuilds.listens.data.model.Song
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Radio for a JioSaavn song: a JioSaavn station seeded with it, topped up as it plays
 * so the queue never runs dry. Only JioSaavn songs start or extend a radio; local
 * queues are never touched.
 */
@Singleton
class JioSaavnRadio @Inject constructor(
    private val repository: JioSaavnRepository,
    private val library: JioSaavnLibrary,
) {
    private val lock = Mutex()
    private var stationId: String? = null
    /** Song ids of the active radio, to tell it apart from any other queue. */
    private val radioSongIds = ConcurrentHashMap.newKeySet<String>()

    /** Starts a radio from [seed]; returns the songs to play, the seed first. */
    suspend fun start(seed: JioSaavnTrack): List<Song> = lock.withLock {
        val (station, tracks) = repository.startRadio(seed.id)
        stationId = station
        radioSongIds.clear()
        val queue = (listOf(seed) + tracks.filterNot { it.id == seed.id }).distinctBy { it.id }
        library.remember(queue)
        queue.map(JioSaavnUris::toSong).also { songs -> radioSongIds += songs.map { it.id } }
    }

    fun isRadioSong(songId: String?): Boolean = songId != null && songId in radioSongIds

    /** The next batch for the active radio, or empty when it has run out. */
    suspend fun more(): List<Song> = lock.withLock {
        val station = stationId ?: return emptyList()
        val tracks = runCatching { repository.radioMore(station) }.getOrNull() ?: return emptyList()
        val fresh = tracks.filterNot { JioSaavnUris.songId(it.id).toString() in radioSongIds }.distinctBy { it.id }
        library.remember(fresh)
        fresh.map(JioSaavnUris::toSong).also { songs -> radioSongIds += songs.map { it.id } }
    }
}
