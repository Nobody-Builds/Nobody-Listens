package com.nobodybuilds.listens.data.party

import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** The host's library as the party sees it: a search index plus songs by id, reloaded after a TTL. */
internal class PartyLibraryCache(
    private val musicRepository: MusicRepository
) {
    private val libraryMutex = Mutex()
    @Volatile var library: PartyLibraryIndex? = null
        private set
    @Volatile var librarySongs: Map<String, Song> = emptyMap()
    @Volatile private var libraryLoadedAt = 0L

    suspend fun loadLibrary(force: Boolean = false): PartyLibraryIndex? = libraryMutex.withLock {
        val now = System.currentTimeMillis()
        val current = library
        if (!force && current != null && now - libraryLoadedAt < LIBRARY_TTL_MS) return current
        val songs = runCatching { musicRepository.getAllSongsOnce() }.getOrElse {
            Timber.tag(TAG).w(it, "Party library load failed")
            return current
        }
        val index = PartyLibraryIndex(songs.map { PartyLibraryIndex.Entry(it.toPartySong(), it.dateAdded) })
        librarySongs = librarySongs + songs.associateBy { it.id }
        library = index
        libraryLoadedAt = now
        index
    }

    fun clear() {
        library = null
        librarySongs = emptyMap()
    }

    private fun Song.toPartySong() = PartySong(
        id = id,
        title = title.ifBlank { "Unknown song" },
        artist = displayArtist.ifBlank { "Unknown artist" },
        album = album,
        durationMs = duration,
    )

    private companion object {
        const val TAG = "PartyServer"
        const val LIBRARY_TTL_MS = 10 * 60_000L
    }
}
