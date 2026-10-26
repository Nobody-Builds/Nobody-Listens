package com.nobodybuilds.listens.data

import com.nobodybuilds.listens.data.local.LocalPlaylistDao
import com.nobodybuilds.listens.data.local.LocalPlaylistEntity
import com.nobodybuilds.listens.data.local.LocalPlaylistSongEntity
import com.nobodybuilds.listens.shared.WearLibraryItem
import com.nobodybuilds.listens.shared.WearTransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A saved playlist with how many of its songs are on the watch so far. */
data class WatchPlaylist(
    val id: String,
    val name: String,
    val songCount: Int,
    val savedCount: Int,
)

/**
 * Phone playlists saved to the watch. Saving stores the playlist and then fetches its songs from the
 * phone one at a time; afterwards it plays with no phone.
 */
@Singleton
class WearPlaylistRepository @Inject constructor(
    private val playlistDao: LocalPlaylistDao,
    private val transferRepository: WearTransferRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private val _savingPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val savingPlaylistIds: StateFlow<Set<String>> = _savingPlaylistIds.asStateFlow()

    val playlists: Flow<List<WatchPlaylist>> = combine(
        playlistDao.getPlaylists(),
        playlistDao.getAllSongs(),
        transferRepository.downloadedSongIds,
    ) { playlists, songs, downloaded ->
        val songsByPlaylist = songs.groupBy { it.playlistId }
        playlists.map { playlist ->
            val entries = songsByPlaylist[playlist.playlistId].orEmpty()
            WatchPlaylist(
                id = playlist.playlistId,
                name = playlist.name,
                songCount = entries.size,
                savedCount = entries.count { it.songId in downloaded },
            )
        }
    }

    fun playlist(playlistId: String): Flow<LocalPlaylistEntity?> = playlistDao.getPlaylist(playlistId)

    fun songs(playlistId: String): Flow<List<LocalPlaylistSongEntity>> = playlistDao.getSongs(playlistId)

    /** Saves the playlist now and fetches its songs in the background, even after the screen closes. */
    fun savePlaylist(playlistId: String, name: String, items: List<WearLibraryItem>) {
        if (playlistId in _savingPlaylistIds.value) return
        val songs = items.filter { it.type == WearLibraryItem.TYPE_SONG }
        _savingPlaylistIds.update { it + playlistId }
        scope.launch {
            try {
                playlistDao.replacePlaylist(
                    LocalPlaylistEntity(playlistId, name, System.currentTimeMillis()),
                    songs.mapIndexed { index, song ->
                        LocalPlaylistSongEntity(playlistId, index, song.id, song.title, song.subtitle)
                    },
                )
                saveMutex.withLock { fetchSongs(songs.filter { it.canSaveToWatch }.map { it.id }) }
            } finally {
                _savingPlaylistIds.update { it - playlistId }
            }
        }
    }

    suspend fun deletePlaylist(playlistId: String) = playlistDao.deletePlaylist(playlistId)

    private suspend fun fetchSongs(songIds: List<String>) {
        var failuresInARow = 0
        for (songId in songIds) {
            if (songId in transferRepository.downloadedSongIds.first()) continue
            failuresInARow = if (fetchSong(songId)) 0 else failuresInARow + 1
            // The phone is probably gone; the rest would fail the same way.
            if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                Timber.tag(TAG).w("Stopped saving the playlist after $failuresInARow failed songs")
                return
            }
        }
    }

    /**
     * Requests one song (or follows a transfer of it that is already running) and waits until it's
     * saved or the transfer ends. Returns whether it was saved.
     */
    private suspend fun fetchSong(songId: String): Boolean {
        val requestId = UUID.randomUUID().toString()
        transferRepository.requestTransfer(songId, requestId = requestId)
        var seen = false
        val saved = withTimeoutOrNull(SONG_TIMEOUT_MS) {
            combine(transferRepository.downloadedSongIds, transferRepository.activeTransfers) { downloaded, active ->
                val transfer = active[requestId] ?: active.values.firstOrNull {
                    it.songId == songId && it.status == WearTransferProgress.STATUS_TRANSFERRING
                }
                if (transfer != null) seen = true
                when {
                    songId in downloaded -> true
                    transfer?.status in ENDED_STATUSES -> false
                    // Cancelled transfers are removed rather than marked; a finished one leaves a row.
                    transfer == null && seen -> false
                    else -> null
                }
            }.first { it != null }
        }
        if (saved == true) return true
        // The row can land just after the transfer entry goes away.
        delay(ROW_SETTLE_MS)
        return songId in transferRepository.downloadedSongIds.first()
    }

    private companion object {
        const val TAG = "WearPlaylistRepo"
        const val SONG_TIMEOUT_MS = 5 * 60_000L
        const val MAX_FAILURES_IN_A_ROW = 3
        const val ROW_SETTLE_MS = 1_000L
        val ENDED_STATUSES = setOf(WearTransferProgress.STATUS_FAILED, WearTransferProgress.STATUS_CANCELLED)
    }
}
