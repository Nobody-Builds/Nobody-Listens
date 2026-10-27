package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.utils.ZipShareHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val MAX_ALBUM_BATCH_SELECTION = 6

/** Batch actions PlayerViewModel runs on the songs or albums selected in a list. */
@SuppressLint("LogNotTimber")
internal class PlayerSelectionActions(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val multiSelectionStateHolder: MultiSelectionStateHolder,
    private val queuePlayback: PlayerQueuePlayback,
    private val transport: PlayerTransportController
) {
    private data class ResolvedAlbumSelection(
        val albums: List<Album>,
        val songs: List<Song>,
        val wasTrimmed: Boolean
    )

    private val scope: CoroutineScope get() = shared.scope
    private val _toastEvents get() = shared.toastEvents
    private val _isSheetVisible get() = shared.isSheetVisible

    // =====================================================
    // Multi-Selection Batch Operations
    // =====================================================

    /**
     * Plays all selected songs, preserving their selection order.
     * Clears selection after starting playback.
     */
    fun playSelectedSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        queuePlayback.playSongs(songs, songs.first(), "Selected Songs")
        multiSelectionStateHolder.clearSelection()
    }

    /**
     * Adds all selected songs to the end of the queue.
     * Clears selection after adding.
     */
    fun addSelectedToQueue(songs: List<Song>) {
        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().successLight()
        songs.forEach { transport.addSongToQueue(it) }
        scope.launch {
            val n = songs.size
            _toastEvents.emit(
                context.resources.getQuantityString(R.plurals.n_songs_added_to_queue, n, n),
            )
        }
        multiSelectionStateHolder.clearSelection()
    }

    /**
     * Adds all selected songs to play next, preserving selection order.
     * Songs are inserted in reverse order so they play in the correct sequence.
     * Clears selection after adding.
     */
    fun addSelectedAsNext(songs: List<Song>) {
        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().successLight()
        songs.reversed().forEach { transport.addSongNextToQueue(it) }
        scope.launch {
            val n = songs.size
            _toastEvents.emit(
                context.resources.getQuantityString(R.plurals.n_songs_will_play_next, n, n),
            )
        }
        multiSelectionStateHolder.clearSelection()
    }

    fun playSelectedAlbums(albums: List<Album>) {
        if (albums.isEmpty()) return
        scope.launch {
            try {
                val resolvedSelection = resolveSelectedAlbumSongs(albums)
                if (resolvedSelection.songs.isEmpty()) {
                    _toastEvents.emit(context.getString(R.string.player_no_playable_songs_in_albums))
                    return@launch
                }

                val queueName = if (resolvedSelection.albums.size == 1) {
                    resolvedSelection.albums.first().title
                } else {
                    context.getString(R.string.player_queue_name_selected_albums)
                }

                queuePlayback.playSongs(resolvedSelection.songs, resolvedSelection.songs.first(), queueName, null)
                _isSheetVisible.value = true

                if (resolvedSelection.wasTrimmed) {
                    _toastEvents.emit(
                        context.getString(R.string.player_only_first_n_albums_queued, MAX_ALBUM_BATCH_SELECTION),
                    )
                } else {
                    _toastEvents.emit(
                        context.getString(
                            R.string.player_albums_queued_format,
                            resolvedSelection.albums.size,
                            resolvedSelection.songs.size,
                        ),
                    )
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing selected albums", e)
                _toastEvents.emit(context.getString(R.string.player_could_not_queue_albums))
            }
        }
    }

    fun addSelectedAlbumsAsNext(albums: List<Album>) {
        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().successLight()
        if (albums.isEmpty()) return

        scope.launch {
            try {
                val resolvedSelection = resolveSelectedAlbumSongs(albums)
                if (resolvedSelection.songs.isEmpty()) {
                    _toastEvents.emit("No playable songs found in selected albums")
                    return@launch
                }

                resolvedSelection.songs
                    .asReversed()
                    .forEach(transport::addSongNextToQueue)

                if (resolvedSelection.wasTrimmed) {
                    _toastEvents.emit("Only the first $MAX_ALBUM_BATCH_SELECTION albums were added as next")
                } else {
                    _toastEvents.emit("${resolvedSelection.albums.size} albums will play next")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error adding selected albums as next", e)
                _toastEvents.emit("Could not add selected albums as next")
            }
        }
    }

    fun addSelectedAlbumsToQueue(albums: List<Album>) {
        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().successLight()
        if (albums.isEmpty()) return

        scope.launch {
            try {
                val resolvedSelection = resolveSelectedAlbumSongs(albums)
                if (resolvedSelection.songs.isEmpty()) {
                    _toastEvents.emit("No playable songs found in selected albums")
                    return@launch
                }

                resolvedSelection.songs.forEach(transport::addSongToQueue)

                if (resolvedSelection.wasTrimmed) {
                    _toastEvents.emit("Only the first $MAX_ALBUM_BATCH_SELECTION albums were added to queue")
                } else {
                    _toastEvents.emit("${resolvedSelection.albums.size} albums added to queue")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error adding selected albums to queue", e)
                _toastEvents.emit("Could not add selected albums to queue")
            }
        }
    }

    fun queueAndPlaySelectedAlbums(albums: List<Album>) {
        playSelectedAlbums(albums)
    }

    /**
     * Shares all selected songs as a ZIP file.
     * Clears selection after initiating share.
     */
    fun shareSelectedAsZip(songs: List<Song>) {
        scope.launch {
            _toastEvents.emit(context.getString(R.string.player_creating_zip))

            val result = ZipShareHelper.createAndShareZip(context, songs)

            result.onSuccess {
                multiSelectionStateHolder.clearSelection()
            }.onFailure { error ->
                _toastEvents.emit(
                    context.getString(R.string.player_share_zip_failed_format, error.localizedMessage ?: ""),
                )
                println(
                    "Failed to share: ${error.localizedMessage}"
                )
            }
        }
    }

    private suspend fun resolveSelectedAlbumSongs(albums: List<Album>): ResolvedAlbumSelection {
        val albumsToProcess = albums.take(MAX_ALBUM_BATCH_SELECTION)
        val wasTrimmed = albums.size > albumsToProcess.size

        val songs = withContext(Dispatchers.IO) {
            buildList {
                albumsToProcess.forEach { album ->
                    val albumSongs = musicRepository.getSongsForAlbum(album.id).first()
                    if (albumSongs.isNotEmpty()) {
                        addAll(sortSongsForAlbumSelection(albumSongs))
                    }
                }
            }
        }

        return ResolvedAlbumSelection(
            albums = albumsToProcess,
            songs = songs,
            wasTrimmed = wasTrimmed
        )
    }

    private fun sortSongsForAlbumSelection(songs: List<Song>): List<Song> {
        return songs.sortedWith(
            compareBy<Song> { it.discNumber ?: 1 }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.getDefault()) }
        )
    }
}
