package com.nobodybuilds.listens.presentation.viewmodel

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.MusicNotificationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val CAST_LOG_TAG = "PlayerCastTransfer"

/**
 * Transport controls of PlayerViewModel: play/pause (local or Cast), shuffle, repeat,
 * volume and queue edits on the MediaController.
 */
@UnstableApi
internal class PlayerTransportController(
    private val shared: PlayerSharedState,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val castStateHolder: CastStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val songResolver: PlayerSongResolver,
    private val playbackLauncher: PlayerPlaybackLauncher,
    private val queuePlayback: PlayerQueuePlayback
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val _trackVolume get() = shared.trackVolume
    private val mediaController: MediaController? get() = shared.mediaController
    private val transitionSchedulerJob: Job? get() = shared.transitionSchedulerJob
    private val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState
    private var pendingRepeatMode: Int? = null

    fun setTrackVolume(volume: Float) {
        mediaController?.let {
            val clampedVolume = volume.coerceIn(0f, 1f)
            it.volume = clampedVolume
            _trackVolume.value = clampedVolume
        }
    }

    fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.let { controller ->
            if (fromIndex >= 0 && fromIndex < controller.mediaItemCount &&
                toIndex >= 0 && toIndex < controller.mediaItemCount) {

                // Move the item in the MediaController's timeline.
                // This is the source of truth for playback.
                controller.moveMediaItem(fromIndex, toIndex)

            }
        }
    }

    fun applyPreferredRepeatMode(@Player.RepeatMode mode: Int) {
        playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = mode) }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            pendingRepeatMode = mode
            return
        }

        val controller = mediaController
        if (controller == null) {
            pendingRepeatMode = mode
            return
        }

        if (controller.repeatMode != mode) {
            controller.repeatMode = mode
        }
        pendingRepeatMode = null
    }

    fun flushPendingRepeatMode() {
        pendingRepeatMode?.let { applyPreferredRepeatMode(it) }
    }

    fun syncShuffleStateWithSession(enabled: Boolean) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
        }
        controller.sendCustomCommand(
            SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle()),
            args
        )
    }

    fun toggleShuffle(currentSongOverride: Song? = null) {
        queuePlayback.cancelPendingFullQueuePlayback()
        val currentQueue = _playerUiState.value.currentPlaybackQueue.toList()
        val currentSong = currentSongOverride
            ?: playbackStateHolder.stablePlayerState.value.currentSong
            ?: mediaController?.currentMediaItem?.let { songResolver.resolveSongFromMediaItem(it) }
            ?: currentQueue.firstOrNull()

        playbackStateHolder.toggleShuffle(
            currentSongs = currentQueue,
            currentSong = currentSong,
            currentQueueSourceName = _playerUiState.value.currentQueueSourceName,
            updateQueueCallback = { newQueue ->
                _playerUiState.update { it.copy(currentPlaybackQueue = newQueue.toPlaybackQueue()) }
            }
        )
    }

    fun addSongToQueue(song: Song) {
        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().successLight()
        mediaController?.let { controller ->
            val mediaItem = playbackLauncher.buildPlaybackMediaItem(song)
            controller.addMediaItem(mediaItem)
            // Queue UI is synced via onTimelineChanged listener
        }
    }

    fun addSongNextToQueue(song: Song) {
        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().successLight()
        mediaController?.let { controller ->
            val mediaItem = playbackLauncher.buildPlaybackMediaItem(song)

            val insertionIndex = if (controller.currentMediaItemIndex != C.INDEX_UNSET) {
                (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            } else {
                controller.mediaItemCount
            }

            controller.addMediaItem(insertionIndex, mediaItem)
            // Queue UI is synced via onTimelineChanged listener
        }
    }

    private fun hasRemoteQueueItems(remoteMediaClient: RemoteMediaClient): Boolean {
        val mediaQueueCount = remoteMediaClient.mediaQueue?.itemCount ?: 0
        val statusQueueCount = remoteMediaClient.mediaStatus?.queueItems?.size ?: 0
        val snapshotQueueCount = castTransferStateHolder.lastRemoteQueue.size
        return mediaQueueCount > 0 || statusQueueCount > 0 || snapshotQueueCount > 0
    }

    private fun remoteQueueMatchesLocalQueue(
        remoteMediaClient: RemoteMediaClient,
        localQueue: List<Song>,
        localStartSong: Song?
    ): Boolean {
        if (localQueue.isEmpty()) return true

        val localQueueIds = localQueue.map { it.id }
        val status = remoteMediaClient.mediaStatus
        val remoteQueueIdsFromStatus = status
            ?.queueItems
            ?.mapNotNull { item ->
                item.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
            }
            .orEmpty()
        val remoteQueueIdsFromSnapshot = castTransferStateHolder.lastRemoteQueue.map { it.id }

        val queueMatches = when {
            remoteQueueIdsFromStatus.size == localQueueIds.size ->
                remoteQueueIdsFromStatus == localQueueIds
            remoteQueueIdsFromSnapshot.size == localQueueIds.size ->
                remoteQueueIdsFromSnapshot == localQueueIds
            remoteQueueIdsFromStatus.isNotEmpty() -> false
            remoteQueueIdsFromSnapshot.isNotEmpty() -> false
            else -> false
        }

        if (!queueMatches) return false

        val expectedSongId = localStartSong?.id ?: return true
        val remoteCurrentSongId = status
            ?.let { mediaStatus ->
                mediaStatus.getQueueItemById(mediaStatus.getCurrentItemId())
                    ?.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
            }
            ?: castTransferStateHolder.lastRemoteSongId

        return remoteCurrentSongId == null || remoteCurrentSongId == expectedSongId
    }

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        isPlaying = false,
                        playWhenReady = false
                    )
                }
            } else {
                val localQueue = _playerUiState.value.currentPlaybackQueue.toList()
                val startSong = playbackStateHolder.stablePlayerState.value.currentSong ?: localQueue.firstOrNull()
                val remoteHasQueue = hasRemoteQueueItems(remoteMediaClient)
                val remoteQueueAligned = remoteQueueMatchesLocalQueue(remoteMediaClient, localQueue, startSong)
                val shouldResumeRemoteQueue = remoteHasQueue && (localQueue.isEmpty() || remoteQueueAligned)

                if (shouldResumeRemoteQueue) {
                    castStateHolder.castPlayer?.play()
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            isPlaying = true,
                            playWhenReady = true
                        )
                    }
                } else if (localQueue.isNotEmpty() && startSong != null) {
                    Timber.tag(CAST_LOG_TAG).i(
                        "Remote queue out of sync. Reloading remote queue (local=%d status=%d snapshot=%d).",
                        localQueue.size,
                        remoteMediaClient.mediaStatus?.queueItems?.size ?: 0,
                        castTransferStateHolder.lastRemoteQueue.size
                    )
                    scope.launch {
                        playbackLauncher.internalPlaySongs(localQueue, startSong, _playerUiState.value.currentQueueSourceName)
                    }
                } else if (remoteHasQueue) {
                    // No local queue available to reconcile; fallback to resuming remote queue.
                    castStateHolder.castPlayer?.play()
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            isPlaying = true,
                            playWhenReady = true
                        )
                    }
                } else {
                    Timber.tag(CAST_LOG_TAG).w("Cannot resume Cast playback: both local and remote queues are empty.")
                }
            }
        } else {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.currentMediaItem == null) {
                        val currentQueue = _playerUiState.value.currentPlaybackQueue
                        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                        when {
                            currentQueue.isNotEmpty() && currentSong != null -> {
                                scope.launch {
                                    transitionSchedulerJob?.cancel()
                                    playbackLauncher.internalPlaySongs(
                                        currentQueue.toList(),
                                        currentSong,
                                        _playerUiState.value.currentQueueSourceName
                                    )
                                }
                            }
                            currentSong != null -> {
                                queuePlayback.loadAndPlaySong(currentSong)
                            }
                            else -> {
                                scope.launch {
                                    val fallbackSong = musicRepository.getFirstPlayableSong()
                                    if (fallbackSong != null) {
                                        queuePlayback.loadAndPlaySong(fallbackSong)
                                    } else {
                                        controller.play()
                                    }
                                }
                            }
                        }
                    } else {
                        controller.play()
                    }
                }
            }
        }
    }

    fun clearQueueExceptCurrent() {
        mediaController?.let { controller ->
            val currentSongIndex = controller.currentMediaItemIndex
            if (currentSongIndex == C.INDEX_UNSET) return@let
            val indicesToRemove = (0 until controller.mediaItemCount)
                .filter { it != currentSongIndex }
                .sortedDescending()

            for (index in indicesToRemove) {
                controller.removeMediaItem(index)
            }
        }
    }

    /** Restores the saved repeat mode and keeps the session's shuffle flag in sync. Called from PlayerViewModel's init. */
    fun startSessionSync() {
        scope.launch {
            // Repeat preference is only a startup restore value.
            // Keeping a live collector here creates a feedback path:
            // player -> DataStore -> collector -> player, which can cause
            // repeat mode oscillation if a transient player state is persisted.
            val savedRepeatMode = userPreferencesRepository.repeatModeFlow.first()
            applyPreferredRepeatMode(savedRepeatMode)
        }

        scope.launch {
            stablePlayerState
                .map { it.isShuffleEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    syncShuffleStateWithSession(enabled)
                }
        }
    }
}
