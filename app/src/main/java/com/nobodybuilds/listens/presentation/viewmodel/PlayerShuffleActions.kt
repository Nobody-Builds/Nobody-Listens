package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.worker.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Shuffle entry points of PlayerViewModel (all songs, favourites, a random album or artist,
 * the Quick Settings tile). Every one respects the Library source filter.
 */
@UnstableApi
@SuppressLint("LogNotTimber")
internal class PlayerShuffleActions(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val libraryStateHolder: LibraryStateHolder,
    private val syncManager: SyncManager,
    private val songResolver: PlayerSongResolver,
    private val queuePlayback: PlayerQueuePlayback
) {
    private val scope: CoroutineScope get() = shared.scope
    private val mediaController: MediaController? get() = shared.mediaController
    private var pendingPlaybackAction: (() -> Unit)?
        get() = shared.pendingPlaybackAction
        set(value) { shared.pendingPlaybackAction = value }

    fun shuffleAllSongs(queueName: String = "All Songs (Shuffled)") {
        Log.d("ShuffleDebug", "shuffleAllSongs called.")
        
        // Whole library (respecting the Library source filter), not a fixed-size sample.
        scope.launch {
            val songs = loadShuffleAllSongs()
            if (songs.isNotEmpty()) {
                queuePlayback.playSongsShuffled(songs, queueName, startAtZero = true)
            } else {
                shared.sendToast(context.getString(R.string.player_no_songs_to_shuffle))
            }
        }
    }

    /**
     * Every song of the source-filtered library, in random order. Ids come from one cheap SQL
     * query; songs are resolved from the in-memory cache or in chunks on IO, so 10k+ songs are OK.
     */
    private suspend fun loadShuffleAllSongs(): List<Song> {
        val filter = songResolver.currentLibrarySourceFilter()
        val ids = com.nobodybuilds.listens.data.model.ShuffleSourceSelector.selectIds(
            musicRepository.getShuffledSongIds(filter)
        )
        return songResolver.resolvePlaybackQueueFromSortedIds(ids)
    }

    /**
     * Called from Quick Settings tile. Unlike shuffleAllSongs(), this always starts
     * fresh playback regardless of current state, and correctly handles the case
     * where the MediaController isn't ready yet (cold start from tile).
     *
     * Queries a bounded random sample directly from the repository so the tile does
     * not depend on the eager in-memory song cache being populated first.
     */
    fun triggerShuffleAllFromTile() {
        Timber.d("[TileDebug] triggerShuffleAllFromTile called. mediaController=${mediaController != null}")
        val action: () -> Unit = {
            Timber.d("[TileDebug] action() invoked")
            scope.launch {
                var songs = loadShuffleAllSongs()
                Timber.d("[TileDebug] Repository returned ${songs.size} songs immediately")

                if (songs.isEmpty()) {
                    // Cold start or stale DB state: trigger a sync and retry the bounded query.
                    Timber.d("[TileDebug] No songs available yet, triggering sync and retrying repository sample")
                    syncManager.sync()
                    songs = withTimeoutOrNull(30_000L) {
                        var refreshedSongs = emptyList<Song>()
                        while (refreshedSongs.isEmpty()) {
                            refreshedSongs = loadShuffleAllSongs()
                            if (refreshedSongs.isEmpty()) {
                                delay(500L)
                            }
                        }
                        refreshedSongs
                    }
                        ?: emptyList()
                    Timber.d("[TileDebug] After retry, repository returned ${songs.size} songs")
                }

                if (songs.isNotEmpty()) {
                    Timber.d("[TileDebug] Calling playSongsShuffled with ${songs.size} songs")
                    queuePlayback.playSongsShuffled(songs, "All Songs (Shuffled)", startAtZero = true)
                } else {
                    Timber.w("[TileDebug] No songs found even after sync - library may be empty")
                    shared.sendToast(context.getString(R.string.player_no_songs_in_library_toast))
                }
            }
        }

        if (mediaController == null) {
            Timber.d("[TileDebug] mediaController null, queuing as pendingPlaybackAction")
            pendingPlaybackAction = action
        } else {
            Timber.d("[TileDebug] mediaController ready, calling action immediately")
            action()
        }
    }

    fun playRandomSong() {
        scope.launch {
            val randomSongs = loadShuffleAllSongs()
            if (randomSongs.isNotEmpty()) {
                queuePlayback.playSongsShuffled(randomSongs, "All Songs (Shuffled)", startAtZero = true)
            }
        }
    }

    fun shuffleFavoriteSongs() {
        Log.d("ShuffleDebug", "shuffleFavoriteSongs called.")

        // Load favorite songs from DB on-demand instead of holding them in memory
        scope.launch {
            val favSongs = musicRepository.getFavoriteSongsOnce(songResolver.currentLibrarySourceFilter())
            if (favSongs.isNotEmpty()) {
                queuePlayback.playSongsShuffled(favSongs, "Liked Songs (Shuffled)", startAtZero = true)
            }
        }
    }

    fun shuffleRandomAlbum() {
        scope.launch {
            val allAlbums = libraryStateHolder.albums.value
            if (allAlbums.isNotEmpty()) {
                val randomAlbum = allAlbums.random()
                val albumSongs = com.nobodybuilds.listens.data.model.ShuffleSourceSelector.selectSongs(
                    songResolver.currentLibrarySourceFilter(),
                    musicRepository.getSongsForAlbum(randomAlbum.id).first()
                )
                if (albumSongs.isNotEmpty()) {
                    queuePlayback.playSongsShuffled(albumSongs, randomAlbum.title, startAtZero = true)
                }
            }
        }
    }

    fun shuffleRandomArtist() {
        scope.launch {
            val allArtists = libraryStateHolder.artists.value
            if (allArtists.isNotEmpty()) {
                val randomArtist = allArtists.random()
                val artistSongs = com.nobodybuilds.listens.data.model.ShuffleSourceSelector.selectSongs(
                    songResolver.currentLibrarySourceFilter(),
                    musicRepository.getSongsForArtist(randomArtist.id).first()
                )
                if (artistSongs.isNotEmpty()) {
                    queuePlayback.playSongsShuffled(artistSongs, randomArtist.name, startAtZero = true)
                }
            }
        }
    }
}
