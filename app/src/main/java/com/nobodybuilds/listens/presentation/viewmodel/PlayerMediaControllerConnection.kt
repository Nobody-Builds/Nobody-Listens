package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nobodybuilds.listens.data.media.MediaControllerFactory
import com.nobodybuilds.listens.data.service.MusicNotificationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PlayerViewModel's connection to the MusicService session: builds the MediaController,
 * wires it up once connected, answers the notification's shuffle command and releases it.
 */
@UnstableApi
@SuppressLint("LogNotTimber")
internal class PlayerMediaControllerConnection(
    private val shared: PlayerSharedState,
    private val context: Context,
    sessionToken: SessionToken,
    mediaControllerFactory: MediaControllerFactory,
    private val playbackStateHolder: PlaybackStateHolder,
    private val controllerListener: PlayerControllerListener,
    private val transport: PlayerTransportController
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private var mediaController: MediaController?
        get() = shared.mediaController
        set(value) { shared.mediaController = value }
    private var pendingPlaybackAction: (() -> Unit)?
        get() = shared.pendingPlaybackAction
        set(value) { shared.pendingPlaybackAction = value }

    private val _isMediaControllerReady = MutableStateFlow(false)
    val isMediaControllerReady: StateFlow<Boolean> = _isMediaControllerReady.asStateFlow()
    // SessionToken injected via constructor
    private val mediaControllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command.customAction == MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE) {
                val enabled = args.getBoolean(
                    MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                    false
                )
                scope.launch {
                    if (enabled != playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        transport.toggleShuffle()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }
    private val mediaControllerFuture: ListenableFuture<MediaController> =
        mediaControllerFactory.create(context, sessionToken, mediaControllerListener)

    /** Wires the controller up once it connects. Called from PlayerViewModel's init. */
    fun connect() {
        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                // Pass controller to PlaybackStateHolder
                playbackStateHolder.setMediaController(mediaController)
                _isMediaControllerReady.value = true


                controllerListener.setupMediaControllerListeners()
                transport.flushPendingRepeatMode()
                transport.syncShuffleStateWithSession(playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                // Execute any pending action that was queued while the controller was connecting
                pendingPlaybackAction?.invoke()
                pendingPlaybackAction = null
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Detaches and releases the controller. Called from PlayerViewModel.onCleared. */
    fun release() {
        controllerListener.detach()
        playbackStateHolder.setMediaController(null)
        mediaController?.release()
        mediaController = null
        mediaControllerFuture.cancel(true)
    }
}
