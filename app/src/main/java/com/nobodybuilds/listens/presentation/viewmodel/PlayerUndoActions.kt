package com.nobodybuilds.listens.presentation.viewmodel

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Undo flows of PlayerViewModel: removing one song from the queue, and dismissing the whole
 * playlist (which stops playback, and disconnects Cast) with an undo bar.
 */
@UnstableApi
internal class PlayerUndoActions(
    private val shared: PlayerSharedState,
    private val playbackStateHolder: PlaybackStateHolder,
    private val castStateHolder: CastStateHolder,
    private val castRouteStateHolder: CastRouteStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val queueUndoStateHolder: QueueUndoStateHolder,
    private val playlistDismissUndoStateHolder: PlaylistDismissUndoStateHolder,
    private val queuePlayback: PlayerQueuePlayback
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val _sheetState get() = shared.sheetState
    private val _isSheetVisible get() = shared.isSheetVisible
    private val _toastEvents get() = shared.toastEvents
    private val mediaController: MediaController? get() = shared.mediaController
    private val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState

    /** Hides the playlist undo bar once a new song starts. Called from PlayerViewModel's init. */
    fun observeDismissUndo() {
        // Auto-hide undo bar when a new song starts playing
        playlistDismissUndoStateHolder.observeUndoStateAgainstPlayback(
            scope = scope,
            currentSongIdFlow = stablePlayerState.map { it.currentSong?.id },
            getUiState = { _playerUiState.value },
            onHideDismissUndoBar = { hideDismissUndoBar() }
        )
    }

    fun removeSongFromQueue(songId: String) {
        queueUndoStateHolder.removeSongFromQueue(
            scope = scope,
            mediaController = mediaController,
            songId = songId,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) }
        )
    }

    fun undoRemoveSongFromQueue() {
        queueUndoStateHolder.undoRemoveSongFromQueue(
            mediaController = mediaController,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) }
        )
    }

    fun hideQueueItemUndoBar() {
        queueUndoStateHolder.hideQueueItemUndoBar { mutation ->
            _playerUiState.update(mutation)
        }
    }

    fun dismissPlaylistAndShowUndo() {
        playlistDismissUndoStateHolder.dismissPlaylistAndShowUndo(
            scope = scope,
            currentSong = playbackStateHolder.stablePlayerState.value.currentSong,
            queue = _playerUiState.value.currentPlaybackQueue,
            queueName = _playerUiState.value.currentQueueSourceName,
            position = playbackStateHolder.currentPosition.value,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            disconnectRemoteIfNeeded = {
                val hasCastSession = castStateHolder.castSession.value != null
                val shouldDisconnectRemote = hasCastSession ||
                    castStateHolder.isRemotePlaybackActive.value ||
                    castStateHolder.isCastConnecting.value
                if (shouldDisconnectRemote) {
                    if (hasCastSession) {
                        castTransferStateHolder.skipNextTransferBack()
                    }
                    castRouteStateHolder.disconnect()
                }
            },
            clearPlayback = {
                mediaController?.stop()
                mediaController?.clearMediaItems()
            },
            clearStablePlaybackState = {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false,
                        totalDuration = 0L
                    )
                }
            },
            setCurrentPosition = { playbackStateHolder.setCurrentPosition(it) },
            setSheetVisible = { _isSheetVisible.value = it }
        )
    }

    fun hideDismissUndoBar() {
        playlistDismissUndoStateHolder.hideDismissUndoBar { mutation ->
            _playerUiState.update(mutation)
        }
    }

    fun undoDismissPlaylist() {
        playlistDismissUndoStateHolder.undoDismissPlaylist(
            scope = scope,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            playSongs = { songs, startSong, queueName ->
                queuePlayback.playSongs(songs, startSong, queueName)
            },
            seekTo = { position -> mediaController?.seekTo(position) },
            setSheetVisible = { _isSheetVisible.value = it },
            setSheetCollapsed = { _sheetState.value = PlayerSheetState.COLLAPSED },
            emitToast = { message -> _toastEvents.emit(message) }
        )
    }
}
