package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import android.os.Trace
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.EotStateHolder
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRadio
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Listens to the MediaController for PlayerViewModel and mirrors the player (current song,
 * queue, play state, buffering, repeat/shuffle, audio format) into the UI state.
 */
@UnstableApi
internal class PlayerControllerListener(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    private val youTubeMusicRadio: YouTubeMusicRadio,
    private val playbackStateHolder: PlaybackStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val themeStateHolder: ThemeStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val castStateHolder: CastStateHolder,
    private val sleepTimerStateHolder: SleepTimerStateHolder,
    private val currentSongSync: CurrentSongStateSync,
    private val songResolver: PlayerSongResolver,
    private val audioMetadataTracker: PlaybackAudioMetadataTracker,
    private val playbackLauncher: PlayerPlaybackLauncher,
    private val transport: PlayerTransportController
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val _isSheetVisible get() = shared.isSheetVisible
    private val _trackVolume get() = shared.trackVolume
    private val _toastEvents get() = shared.toastEvents
    private val _showNoInternetDialog get() = shared.showNoInternetDialog
    private val mediaController: MediaController? get() = shared.mediaController
    private var transitionSchedulerJob: Job?
        get() = shared.transitionSchedulerJob
        set(value) { shared.transitionSchedulerJob = value }
    private val isEndOfTrackTimerActive: StateFlow<Boolean> = sleepTimerStateHolder.isEndOfTrackTimerActive
    private val isRemotePlaybackActive: StateFlow<Boolean> = castStateHolder.isRemotePlaybackActive
    private val isCastConnecting: StateFlow<Boolean> = castStateHolder.isCastConnecting

    private val RADIO_REFILL_WHEN_LEFT = 3

    private var mediaControllerPlaybackListener: Player.Listener? = null
    private var bufferingDebounceJob: Job? = null

    private var lastQueueUpdateRequestId = 0L
    private var lastQueueSignature: String? = null
    private var lastQueueUpdateJob: Job? = null

    private fun updateCurrentPlaybackQueueFromPlayer(playerCtrl: MediaController?) {
        val currentMediaController = playerCtrl ?: mediaController ?: return
        val count = currentMediaController.mediaItemCount

        // Heuristic: skip if size hasn't changed.
        // We don't include currentMediaId here because we don't need to rebuild the whole
        // queue list just because the selection moved. Selection is handled by stablePlayerState.
        val signature = "$count"
        if (signature == lastQueueSignature) return
        lastQueueSignature = signature

        if (count == 0) {
            _playerUiState.update { it.copy(currentPlaybackQueue = persistentListOf()) }
            return
        }

        val requestId = ++lastQueueUpdateRequestId
        lastQueueUpdateJob?.cancel()
        lastQueueUpdateJob = scope.launch {
            // Debounce slightly to handle rapid-fire timeline events
            delay(100)
            
            val timeline = currentMediaController.currentTimeline
            val mediaItems = mutableListOf<MediaItem>()
            val window = Timeline.Window()
            
            // Collect MediaItems on Main thread (inside coroutine)
            for (i in 0 until count) {
                mediaItems.add(timeline.getWindow(i, window).mediaItem)
                if (i % 500 == 0) kotlinx.coroutines.yield()
            }

            val allSongsById = libraryStateHolder.allSongsById.value
            
            val queue = withContext(Dispatchers.Default) {
                mediaItems.mapNotNull { mediaItem ->
                    songResolver.resolveSongFromMediaItem(mediaItem, allSongsById)
                }
            }

            if (requestId != lastQueueUpdateRequestId) return@launch

            _playerUiState.update { it.copy(currentPlaybackQueue = queue.toPlaybackQueue()) }
            if (queue.isNotEmpty()) {
                _isSheetVisible.value = true
            }
        }
    }

    private fun isRemoteSessionControllingPlayback(): Boolean {
        val remoteClient = castStateHolder.castSession.value?.remoteMediaClient
        return remoteClient != null &&
                (castStateHolder.isRemotePlaybackActive.value || castStateHolder.isCastConnecting.value)
    }

    private fun syncPlaybackPositionFromPlayer(
        mediaId: String?,
        reportedPositionMs: Long
    ): Long {
        playbackStateHolder.syncCurrentPositionFromPlayer(mediaId, reportedPositionMs)
        return playbackStateHolder.currentPosition.value
    }

    private fun syncDisplayedMediaItemIfChanged(player: Player) {
        if (isRemoteSessionControllingPlayback()) return

        val mediaItem = player.currentMediaItem ?: return
        val currentSongId = playbackStateHolder.stablePlayerState.value.currentSong?.id
        if (currentSongId == mediaItem.mediaId) return

        playbackStateHolder.onPlaybackOccurrenceTransition(mediaItem.mediaId)
        audioMetadataTracker.preparePlaybackAudioMetadataForMedia(mediaItem.mediaId)
        transitionSchedulerJob?.cancel()
        lyricsStateHolder.cancelLoading()
        resetLyricsSearchState()

        val song = songResolver.resolveSongFromMediaItem(mediaItem)
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val resolvedDuration = if (song != null) {
            playbackStateHolder.resolveDurationForPlaybackState(
                reportedDurationMs = player.duration,
                songDurationHintMs = song.duration.coerceAtLeast(0L),
                currentPositionMs = currentPosition
            )
        } else {
            0L
        }

        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = song,
                currentMediaItemIndex = player.currentMediaItemIndex,
                totalDuration = resolvedDuration,
                lyrics = null,
                isLoadingLyrics = song != null,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady
            )
        }
        syncPlaybackPositionFromPlayer(mediaItem.mediaId, currentPosition)

        song?.let { currentSongValue ->
            scope.launch {
                val uri = currentSongValue.albumArtUriString?.toUri()
                val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
            }
            currentSongSync.loadLyricsForCurrentSong()
        }
    }

    fun setupMediaControllerListeners() {
        Trace.beginSection("PlayerViewModel.setupMediaControllerListeners")
        val playerCtrl = mediaController ?: return Trace.endSection()
        _trackVolume.value = playerCtrl.volume
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                isShuffleEnabled = it.isShuffleEnabled,
                repeatMode = playerCtrl.repeatMode,
                isPlaying = playerCtrl.isPlaying,
                playWhenReady = playerCtrl.playWhenReady
            )
        }
        audioMetadataTracker.preparePlaybackAudioMetadataForMedia(playerCtrl.currentMediaItem?.mediaId)
        audioMetadataTracker.refreshPlaybackAudioMetadata(playerCtrl)

        updateCurrentPlaybackQueueFromPlayer(playerCtrl)

        playerCtrl.currentMediaItem?.let { mediaItem ->
            playbackStateHolder.ensureCurrentPlaybackOccurrence(mediaItem.mediaId)
            val song = songResolver.resolveSongFromMediaItem(mediaItem)

            if (song != null) {
                val initialPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                    reportedDurationMs = playerCtrl.duration,
                    songDurationHintMs = song.duration.coerceAtLeast(0L),
                    currentPositionMs = initialPosition
                )
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = song,
                        totalDuration = resolvedDuration
                    )
                }
                syncPlaybackPositionFromPlayer(mediaItem.mediaId, initialPosition)
                scope.launch {
                    val uri = song.albumArtUriString?.toUri()
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                }
                currentSongSync.loadLyricsForCurrentSong()
                if (playerCtrl.isPlaying) {
                    _isSheetVisible.value = true
                    startProgressUpdates()
                }
            } else {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false
                    )
                }
                playbackStateHolder.clearCurrentPositionHints()
                playbackStateHolder.setCurrentPosition(0L)
                audioMetadataTracker.resetPlaybackAudioMetadata()
            }
        }

        mediaControllerPlaybackListener?.let(playerCtrl::removeListener)
        mediaControllerPlaybackListener = object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                _trackVolume.value = volume
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        isPlaying = isPlaying,
                        playWhenReady = playerCtrl.playWhenReady
                    )
                }
                if (isPlaying) {
                    _isSheetVisible.value = true
                    playbackLauncher.clearPreparingSongIfMatching(playerCtrl.currentMediaItem?.mediaId)
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                    val pausedPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    syncPlaybackPositionFromPlayer(playerCtrl.currentMediaItem?.mediaId, pausedPosition)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.updateStablePlayerState { it.copy(playWhenReady = playWhenReady) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                handleMediaItemTransition(playerCtrl, mediaItem, reason)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isRemoteSessionControllingPlayback()) return
                handlePlaybackStateChanged(playerCtrl, playbackState)
            }
            override fun onTracksChanged(tracks: Tracks) {
                if (isRemoteSessionControllingPlayback()) return
                audioMetadataTracker.refreshPlaybackAudioMetadata(playerCtrl, tracks)
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                syncDisplayedMediaItemIfChanged(playerCtrl)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // IMPORTANT: We don't use ExoPlayer's shuffle mode anymore
                // Instead, we manually shuffle the queue to fix crossfade issues
                // If ExoPlayer's shuffle gets enabled (e.g., from media button), turn it off and use our toggle
                if (shuffleModeEnabled) {
                    playerCtrl.shuffleModeEnabled = false
                    // Trigger our manual shuffle instead
                    if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        transport.toggleShuffle()
                    }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = repeatMode) }
                scope.launch { userPreferencesRepository.setRepeatMode(repeatMode) }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                syncDisplayedMediaItemIfChanged(playerCtrl)
                // Skip updates during crossfade transitions to prevent UI freeze and jumpy state.
                if (dualPlayerEngine.isTransitionRunning()) return

                transitionSchedulerJob?.cancel()
                
                // Only refresh full queue on structural changes or source updates (metadata)
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED ||
                    reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                    updateCurrentPlaybackQueueFromPlayer(mediaController)
                }
            }
        }
        playerCtrl.addListener(checkNotNull(mediaControllerPlaybackListener))
        Trace.endSection()
    }

    private fun handleMediaItemTransition(playerCtrl: MediaController, mediaItem: MediaItem?, reason: Int) {
        playbackStateHolder.onPlaybackOccurrenceTransition(mediaItem?.mediaId)
        audioMetadataTracker.preparePlaybackAudioMetadataForMedia(mediaItem?.mediaId)
        transitionSchedulerJob?.cancel()
        lyricsStateHolder.cancelLoading()
        transitionSchedulerJob = scope.launch {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val activeEotSongId = EotStateHolder.eotTargetSongId.value
                val previousSongId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                if (isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                    playerCtrl.seekTo(0L)
                    playerCtrl.pause()

                    val finishedSongTitle = libraryStateHolder.allSongsById.value[previousSongId]?.title
                        ?: context.getString(R.string.player_default_track_title)

                    scope.launch {
                        _toastEvents.emit(
                            context.getString(R.string.player_playback_stopped_eot, finishedSongTitle),
                        )
                    }
                    sleepTimerStateHolder.cancelSleepTimer(suppressDefaultToast = true)
                }
            }

            mediaItem?.let { transitionedItem ->
                val song = songResolver.resolveSongFromMediaItem(transitionedItem)

                // YouTube radio: top the queue up before it runs out. Other queues are untouched.
                val songsLeft = playerCtrl.mediaItemCount - playerCtrl.currentMediaItemIndex - 1
                if (youTubeMusicRadio.isRadioSong(song?.id) && songsLeft <= RADIO_REFILL_WHEN_LEFT) {
                    scope.launch {
                        youTubeMusicRadio.more().forEach { next ->
                            mediaController?.addMediaItem(playbackLauncher.buildPlaybackMediaItem(next))
                        }
                    }
                }
                
                // Offline check for Telegram songs
                if (song?.contentUriString?.startsWith("telegram:") == true) {
                    currentSongSync.ensureTelegramPlaybackObserversStarted()
                    val isOnline = connectivityStateHolder.isOnline.value
                    if (!isOnline) {
                        val fileId = song.telegramFileId
                        if (fileId != null) {
                            val isCached = musicRepository.telegramRepository.isFileCached(fileId)
                            if (!isCached) {
                                playerCtrl.pause()
                                _showNoInternetDialog.emit(Unit)
                            }
                        }
                    }
                }

                val resolvedDuration = if (song != null) {
                    playbackStateHolder.resolveDurationForPlaybackState(
                        reportedDurationMs = playerCtrl.duration,
                        songDurationHintMs = song.duration.coerceAtLeast(0L),
                        currentPositionMs = playerCtrl.currentPosition.coerceAtLeast(0L)
                    )
                } else {
                    0L
                }
                resetLyricsSearchState()
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = song,
                        currentMediaItemIndex = playerCtrl.currentMediaItemIndex,
                        totalDuration = resolvedDuration,
                        lyrics = null,
                        isLoadingLyrics = song != null,
                        playWhenReady = playerCtrl.playWhenReady
                    )
                }
                val transitionPosition = syncPlaybackPositionFromPlayer(
                    transitionedItem.mediaId,
                    playerCtrl.currentPosition.coerceAtLeast(0L)
                )

                song?.let { currentSongValue ->
                    scope.launch {
                        val uri = currentSongValue.albumArtUriString?.toUri()
                        val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                        themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                    }
                    currentSongSync.loadLyricsForCurrentSong()
                }
            } ?: run {
                if (!isCastConnecting.value && !isRemotePlaybackActive.value) {
                    lyricsStateHolder.cancelLoading()
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            currentSong = null,
                            isPlaying = false,
                            playWhenReady = false,
                            lyrics = null,
                            isLoadingLyrics = false,
                            totalDuration = 0L
                        )
                    }
                    playbackStateHolder.clearCurrentPositionHints()
                    audioMetadataTracker.resetPlaybackAudioMetadata()
                }
            }
        }
    }

    private fun handlePlaybackStateChanged(playerCtrl: MediaController, playbackState: Int) {
        audioMetadataTracker.refreshPlaybackAudioMetadata(playerCtrl)
        syncDisplayedMediaItemIfChanged(playerCtrl)

        // Debounce buffering state to avoid flickering
        bufferingDebounceJob?.cancel()
        if (playbackState == Player.STATE_BUFFERING) {
            bufferingDebounceJob = scope.launch {
                delay(150) // Wait 150ms before showing buffering indicator
                playbackStateHolder.updateStablePlayerState { state ->
                    state.copy(isBuffering = true)
                }
            }
        } else {
            // Immediately hide buffering when not buffering
            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(isBuffering = false)
            }
        }

        if (playbackState == Player.STATE_READY) {
            playbackLauncher.clearPreparingSongIfMatching(playerCtrl.currentMediaItem?.mediaId)
            val readyPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
            val songDurationHint = playbackStateHolder.stablePlayerState.value.currentSong?.duration ?: 0L
            val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                reportedDurationMs = playerCtrl.duration,
                songDurationHintMs = songDurationHint,
                currentPositionMs = readyPosition
            )
            syncPlaybackPositionFromPlayer(playerCtrl.currentMediaItem?.mediaId, readyPosition)
            playbackStateHolder.updateStablePlayerState { state ->
                // Streams queued without a length (YouTube search rows) take it from the player.
                val song = state.currentSong
                val withLength = if (
                    song != null && song.duration <= 0L && resolvedDuration > 0L &&
                    song.id == playerCtrl.currentMediaItem?.mediaId
                ) song.copy(duration = resolvedDuration) else song
                state.copy(totalDuration = resolvedDuration, currentSong = withLength)
            }
            startProgressUpdates()
        }
        if (playbackState == Player.STATE_IDLE && playerCtrl.mediaItemCount == 0) {
            playbackLauncher.clearPreparingSongIfMatching()
            if (!isCastConnecting.value && !isRemotePlaybackActive.value) {
                lyricsStateHolder.cancelLoading()
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false,
                        lyrics = null,
                        isLoadingLyrics = false,
                        totalDuration = 0L
                    )
                }
                playbackStateHolder.clearCurrentPositionHints()
                playbackStateHolder.setCurrentPosition(0L)
                audioMetadataTracker.resetPlaybackAudioMetadata()
            }
        }
    }

    /** Removes the playback listener from the controller. Called from PlayerViewModel.onCleared. */
    fun detach() {
        mediaControllerPlaybackListener?.let { listener ->
            mediaController?.removeListener(listener)
            mediaControllerPlaybackListener = null
        }
    }

    private fun startProgressUpdates() {
        playbackStateHolder.startProgressUpdates()
    }

    private fun stopProgressUpdates() {
        playbackStateHolder.stopProgressUpdates()
    }

    private fun resetLyricsSearchState() {
        lyricsStateHolder.resetSearchState()
    }
}
