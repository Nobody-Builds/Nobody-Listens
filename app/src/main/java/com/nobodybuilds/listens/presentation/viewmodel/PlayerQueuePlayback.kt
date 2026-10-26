package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.utils.QueueUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val CAST_LOG_TAG = "PlayerCastTransfer"

/**
 * Starts playback of a song list for PlayerViewModel: library and favourites queues, albums,
 * artists, plain and shuffled lists, locally or on a Cast receiver.
 */
@UnstableApi
@SuppressLint("LogNotTimber")
internal class PlayerQueuePlayback(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val queueStateHolder: QueueStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val castStateHolder: CastStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val songResolver: PlayerSongResolver,
    private val playbackLauncher: PlayerPlaybackLauncher,
    private val sheetNavigator: PlayerSheetNavigator,
    private val currentSongSync: CurrentSongStateSync
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val playerUiState: StateFlow<PlayerUiState> get() = shared.playerUiState
    private val _isSheetVisible get() = shared.isSheetVisible
    private val _toastEvents get() = shared.toastEvents
    private val _showNoInternetDialog get() = shared.showNoInternetDialog
    private val mediaController: MediaController? get() = shared.mediaController
    private var pendingPlaybackAction: (() -> Unit)?
        get() = shared.pendingPlaybackAction
        set(value) { shared.pendingPlaybackAction = value }
    private val transitionSchedulerJob: Job? get() = shared.transitionSchedulerJob

    private var fullQueuePlaybackJob: Job? = null
    private var fullQueuePlaybackToken: Long = 0L
    private var remoteQueueLoadJob: Job? = null

    fun showAndPlaySongFromLibrary(
        song: Song,
        queueName: String = "Library",
        isVoluntaryPlay: Boolean = true
    ) {
        launchLatestFullQueuePlayback(
            song = song,
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            failureMessage = "Failed to build full library queue for songId=%s"
        ) {
            val sortOption = playerUiState.value.currentSongSortOption
            val storageFilter = songResolver.currentLibrarySourceFilter()
            musicRepository.getSongIdsSorted(sortOption, storageFilter)
        }
    }

    fun showAndPlaySongFromFavorites(
        song: Song,
        queueName: String = "Liked Songs",
        isVoluntaryPlay: Boolean = true
    ) {
        launchLatestFullQueuePlayback(
            song = song,
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            failureMessage = "Failed to build favorites queue for songId=%s"
        ) {
            val sortOption = playerUiState.value.currentFavoriteSortOption
            val storageFilter = songResolver.currentLibrarySourceFilter()
            musicRepository.getFavoriteSongIdsSorted(sortOption, storageFilter)
        }
    }

    suspend fun getSongsForCurrentLibrarySelection(): List<Song> {
        val sortOption = playerUiState.value.currentSongSortOption
        val storageFilter = songResolver.currentLibrarySourceFilter()
        val sortedIds = musicRepository.getSongIdsSorted(sortOption, storageFilter)
        return songResolver.resolvePlaybackQueueFromSortedIds(sortedIds)
    }

    private fun launchLatestFullQueuePlayback(
        song: Song,
        queueName: String,
        isVoluntaryPlay: Boolean,
        failureMessage: String,
        sortedIdsProvider: suspend () -> List<Long>
    ) {
        cancelPendingFullQueuePlayback()
        val requestToken = fullQueuePlaybackToken

        fullQueuePlaybackJob = scope.launch {
            try {
                val sortedIds = sortedIdsProvider()
                throwIfFullQueuePlaybackRequestIsStale(requestToken)

                val fullQueue = songResolver.resolvePlaybackQueueFromSortedIds(sortedIds)
                throwIfFullQueuePlaybackRequestIsStale(requestToken)

                showAndPlaySong(
                    song = song,
                    contextSongs = fullQueue.ifEmpty { listOf(song) },
                    queueName = queueName,
                    isVoluntaryPlay = isVoluntaryPlay,
                    cancelPendingQueueBuild = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestToken != fullQueuePlaybackToken) {
                    return@launch
                }

                Timber.e(error, failureMessage, song.id)
                showAndPlaySong(
                    song = song,
                    contextSongs = listOf(song),
                    queueName = queueName,
                    isVoluntaryPlay = isVoluntaryPlay,
                    cancelPendingQueueBuild = false
                )
            }
        }
    }

    fun cancelPendingFullQueuePlayback() {
        fullQueuePlaybackToken += 1L
        fullQueuePlaybackJob?.cancel()
        fullQueuePlaybackJob = null
    }

    private fun throwIfFullQueuePlaybackRequestIsStale(requestToken: Long) {
        if (requestToken != fullQueuePlaybackToken) {
            throw CancellationException("Stale full-queue playback request")
        }
    }

    private fun incrementSongScore(song: Song) {
        listeningStatsTracker.onVoluntarySelection(song.id)
    }

    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true,
        cancelPendingQueueBuild: Boolean = true
    ) {
        if (cancelPendingQueueBuild) {
            cancelPendingFullQueuePlayback()
        }
        val playbackContext =
            if (contextSongs.any { it.id == song.id }) contextSongs else listOf(song)
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            val mediaStatus = remoteMediaClient.mediaStatus
            val desiredQueue = playbackContext
            val lastRemoteQueue = castTransferStateHolder.lastRemoteQueue
            val contextMatchesRemoteSnapshot = lastRemoteQueue.matchesSongOrder(desiredQueue)
            val targetIndexInDesiredQueue = desiredQueue.indexOfFirst { it.id == song.id }

            val currentRemoteId = mediaStatus
                ?.let { status ->
                    status.getQueueItemById(status.getCurrentItemId())
                        ?.customData?.optString("songId")
                        ?.takeIf { it.isNotBlank() }
                } ?: castTransferStateHolder.lastRemoteSongId

            val itemIdFromStatus = mediaStatus
                ?.queueItems
                ?.firstOrNull { it.customData?.optString("songId") == song.id }
                ?.itemId

            val targetItemId = itemIdFromStatus?.takeIf { it > 0 }
            val canJumpInCurrentRemoteQueue = contextMatchesRemoteSnapshot && targetIndexInDesiredQueue >= 0 && targetItemId != null

            when {
                canJumpInCurrentRemoteQueue -> {
                    // Same queue context: jump directly for immediate, deterministic song changes.
                    remoteQueueLoadJob?.cancel()
                    castTransferStateHolder.markPendingRemoteSong(song)
                    val itemId = requireNotNull(targetItemId)
                    castStateHolder.castPlayer?.jumpToItem(itemId, 0L)
                }
                contextMatchesRemoteSnapshot && currentRemoteId == song.id -> {
                    // Already on target.
                    remoteQueueLoadJob?.cancel()
                    castTransferStateHolder.markPendingRemoteSong(song)
                }
                else -> {
                    // Queue context changed: perform a single remote queue load.
                    remoteQueueLoadJob?.cancel()
                    remoteQueueLoadJob = scope.launch {
                        val hydratedQueue = songResolver.hydrateSongsIfNeeded(desiredQueue)
                        if (hydratedQueue.isEmpty()) return@launch
                        val hydratedStartSong =
                            hydratedQueue.firstOrNull { it.id == song.id } ?: hydratedQueue.first()
                        val loaded = castTransferStateHolder.playRemoteQueue(
                            songsToPlay = hydratedQueue,
                            startSong = hydratedStartSong,
                            isShuffleEnabled = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
                        )
                        if (!loaded) {
                            Timber.tag(CAST_LOG_TAG).w(
                                "Failed to load requested remote queue (songId=%s size=%d).",
                                song.id,
                                desiredQueue.size
                            )
                        }
                    }
                }
            }

            if (isVoluntaryPlay) incrementSongScore(song)
            return
        }    // Local playback logic
        mediaController?.let { controller ->
            val currentQueue = _playerUiState.value.currentPlaybackQueue
            val songIndexInQueue = currentQueue.indexOfFirst { it.id == song.id }
            val queueMatchesContext = currentQueue.matchesSongOrder(playbackContext)

            if (songIndexInQueue != -1 && queueMatchesContext) {
                if (controller.currentMediaItemIndex == songIndexInQueue) {
                    if (!controller.isPlaying) controller.play()
                } else {
                    controller.seekTo(songIndexInQueue, 0L)
                    controller.play()
                }
                if (isVoluntaryPlay) incrementSongScore(song)
            } else {
                if (isVoluntaryPlay) incrementSongScore(song)
                playSongs(playbackContext, song, queueName, null)
            }
        }
        sheetNavigator.resetPredictiveBackState()
    }

    fun showAndPlaySong(song: Song) {
        Log.d("ShuffleDebug", "showAndPlaySong (single song overload) called for '${song.title}'")
        // Use the song directly without scanning allSongs — the caller provides up-to-date data
        showAndPlaySong(song, listOf(song), "Library")
    }

    fun playAlbum(album: Album) {
        Log.d("ShuffleDebug", "playAlbum called for album: ${album.title}")
        scope.launch {
            try {
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForAlbum(album.id).first()
                }

                if (songsList.isNotEmpty()) {
                    val sortedSongs = songsList.sortedWith(
                        compareBy<Song> { it.discNumber ?: 1 }
                            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                            .thenBy { it.title.lowercase() }
                    )

                    playSongs(sortedSongs, sortedSongs.first(), album.title, null)
                    _isSheetVisible.value = true // Mostrar reproductor
                } else {
                    Log.w("PlayerViewModel", "Album '${album.title}' has no playable songs.")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing album ${album.title}", e)
            }
        }
    }

    fun playArtist(artist: Artist) {
        Log.d("ShuffleDebug", "playArtist called for artist: ${artist.name}")
        scope.launch {
            try {
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForArtist(artist.id).first()
                }

                if (songsList.isNotEmpty()) {
                    playSongs(songsList, songsList.first(), artist.name, null)
                    _isSheetVisible.value = true
                } else {
                    Log.w("PlayerViewModel", "Artist '${artist.name}' has no playable songs.")
                    // podrías emitir un evento Toast
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing artist ${artist.name}", e)
            }
        }
    }

    // rebuildPlayerQueue functionality moved to PlaybackStateHolder (simplified)
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        cancelPendingFullQueuePlayback()
        scope.launch {
            transitionSchedulerJob?.cancel()

            val validSongs = songResolver.hydrateSongsIfNeeded(songsToPlay)

            if (validSongs.isEmpty()) {
                _toastEvents.emit(context.getString(R.string.no_valid_songs))
                return@launch
            }

            // Adjust startSong if it was filtered out
            val validStartSong =
                validSongs.firstOrNull { it.id == startSong.id } ?: validSongs.first()

            // Offline check for the starting song if it is a Telegram song
            if (validStartSong.contentUriString.startsWith("telegram:")) {
                currentSongSync.ensureTelegramPlaybackObserversStarted()
                val isOnline = connectivityStateHolder.isOnline.value
                val fileId = validStartSong.telegramFileId
                
                Timber.d("Offline Check: fileId=$fileId, contentUri=${validStartSong.contentUriString}, isOnline=$isOnline")

                if (!isOnline) {
                     if (fileId != null) {
                         val isCached = musicRepository.telegramRepository.isFileCached(fileId)
                         Timber.d("Offline Check: isCached=$isCached")
                         if (!isCached) {
                             Timber.w("Blocked playback: Offline and not cached.")
                             _showNoInternetDialog.tryEmit(Unit)
                             return@launch
                         }
                     }
                }
            }

            // Store the original order so we can "unshuffle" later if the user turns shuffle off
            queueStateHolder.setOriginalQueueOrder(validSongs)
            queueStateHolder.saveOriginalQueueState(validSongs, queueName)

            // Check if the user wants shuffle to be persistent across different albums
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            // Check if shuffle is currently active in the player
            val isShuffleOn = playbackStateHolder.stablePlayerState.value.isShuffleEnabled

            // If Persistent Shuffle is OFF, we reset shuffle to "false" every time a new album starts
            if (!isPersistent) {
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = false) }
            }

            // If shuffle is persistent and currently ON, we shuffle the new songs immediately
            val finalSongsToPlay = if (isPersistent && isShuffleOn) {
                // Shuffle the list but make sure the song you clicked stays at its current index or starts first
                withContext(Dispatchers.Default) {
                    QueueUtils.buildAnchoredShuffleQueueSuspending(
                        validSongs,
                        validSongs.indexOfFirst { it.id == validStartSong.id }.coerceAtLeast(0)
                    )
                }
            } else {
                // Otherwise, just use the normal sequential order
                validSongs
            }

            // Send the final list (shuffled or not) to the player engine
            playbackLauncher.internalPlaySongs(finalSongsToPlay, validStartSong, queueName, playlistId)
        }
    }

    // Start playback with shuffle enabled in one coroutine to avoid racing queue updates
    fun playSongsShuffled(
        songsToPlay: List<Song>, 
        queueName: String = "None", 
        playlistId: String? = null,
        startAtZero: Boolean = false
    ) {
        cancelPendingFullQueuePlayback()
        scope.launch {
            val result = queueStateHolder.prepareShuffledQueueSuspending(songsToPlay, queueName, startAtZero)
            if (result == null) {
                shared.sendToast(context.getString(R.string.player_no_songs_to_shuffle))
                return@launch
            }

            val (shuffledQueue, startSong) = result
            transitionSchedulerJob?.cancel()

            // Optimistically update shuffle state
            playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = true) }
            launch { userPreferencesRepository.setShuffleOn(true) }

            playbackLauncher.internalPlaySongs(shuffledQueue, startSong, queueName, playlistId)
        }
    }

    fun loadAndPlaySong(song: Song) {
        cancelPendingFullQueuePlayback()
        playbackLauncher.beginPreparingSong(song)
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = song,
                isPlaying = true,
                playWhenReady = true
            )
        }
        _isSheetVisible.value = true

        val controller = mediaController
        if (controller == null) {
            pendingPlaybackAction = {
                loadAndPlaySong(song)
            }
            return
        }

        scope.launch {
            val mediaItem = playbackLauncher.buildResolvedPlaybackMediaItem(song)
            if (controller.currentMediaItem?.mediaId == song.id) {
                if (!controller.isPlaying) controller.play()
            } else {
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
        }
    }

    fun prepareBenchmarkPlayerFromLibrary() {
        scope.launch {
            repeat(90) { attempt ->
                val controllerReady = mediaController != null
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongsOnce()
                }
                Log.i(
                    "NobodyListensBenchmark",
                    "prepare player attempt=$attempt controllerReady=$controllerReady songs=${songs.size}"
                )
                if (controllerReady && songs.isNotEmpty()) {
                    playSongs(songs, songs.first(), "Benchmark Player")
                    delay(700L)
                    sheetNavigator.collapsePlayerSheet()
                    Log.i("NobodyListensBenchmark", "Benchmark player prepared with ${songs.first().title}")
                    return@launch
                }
                delay(500L)
            }
            Log.w("NobodyListensBenchmark", "Unable to prepare benchmark player from library")
        }
    }

    fun onCleared() {
        remoteQueueLoadJob?.cancel()
    }
}
