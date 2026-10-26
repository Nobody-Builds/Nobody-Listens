package com.nobodybuilds.listens.data.service

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaSession
import com.nobodybuilds.listens.data.gestures.GestureController
import com.nobodybuilds.listens.data.navidrome.NavidromeRepository
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.presentation.viewmodel.ListeningStatsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * [MusicService]'s listener on the session player: keeps stats, Navidrome reports, ReplayGain,
 * the sleep timer, widgets, the notification buttons and the saved queue in step with playback.
 */
internal class MusicServicePlayerListener(
    private val engine: DualPlayerEngine,
    private val sessionProvider: () -> MediaSession?,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val gestureController: GestureController,
    private val navidromeRepository: NavidromeRepository,
    private val appScope: CoroutineScope,
    private val navidromeReporter: NavidromePlaybackReporter,
    private val playbackVolume: PlaybackVolumeController,
    private val headset: HeadsetReconnectResumer,
    private val sleepTimer: SleepTimerScheduler,
    private val snapshotStore: PlaybackSnapshotStore,
    private val mediaButtons: MediaSessionButtons,
    private val widgetUpdates: WidgetUpdateScheduler
) : Player.Listener {
    private val mediaSession: MediaSession? get() = sessionProvider()
    private val lastAppliedReplayGainVolume: Float? get() = playbackVolume.lastAppliedReplayGainVolume
    private val lastReplayGainMediaId: String? get() = playbackVolume.lastReplayGainMediaId
    private var endOfTrackTimerSongId by sleepTimer::endOfTrackTimerSongId
    private val resumeOnHeadsetReconnectEnabled by headset::resumeOnHeadsetReconnectEnabled
    private var shouldResumeAfterHeadsetReconnect by headset::shouldResumeAfterHeadsetReconnect
    private var lastNoisyPauseRealtimeMs by headset::lastNoisyPauseRealtimeMs

    private fun syncLocalListeningStatsFromPlayer(player: Player, forceNewSession: Boolean = false) =
        syncListeningStatsFromPlayer(listeningStatsTracker, player, forceNewSession)

    private fun clearHeadsetReconnectResume() = headset.clearHeadsetReconnectResume()

    private fun setPlayerVolume(player: Player, volume: Float) = playbackVolume.setPlayerVolume(player, volume)

    private fun applyReplayGain(mediaItem: MediaItem?) = playbackVolume.applyReplayGain(mediaItem)

    private fun prefetchReplayGain(mediaItem: MediaItem?) = playbackVolume.prefetchReplayGain(mediaItem)

    private fun getNavidromeId(mediaItem: MediaItem?): String? = navidromeReporter.getNavidromeId(mediaItem)

    private fun isNavidromeMediaItem(mediaItem: MediaItem?): Boolean = navidromeReporter.isNavidromeMediaItem(mediaItem)

    private fun reportNavidromePlayback(state: String, mediaItem: MediaItem? = engine.masterPlayer.currentMediaItem) =
        navidromeReporter.reportNavidromePlayback(state, mediaItem)

    private fun startNavidromePlaybackReporting() = navidromeReporter.startNavidromePlaybackReporting()

    private fun stopNavidromePlaybackReporting() = navidromeReporter.stopNavidromePlaybackReporting()

    private fun refreshMediaSessionUi(session: MediaSession) = mediaButtons.refreshMediaSessionUi(session)

    private fun refreshMediaSessionUiWithFollowUp(session: MediaSession) =
        mediaButtons.refreshMediaSessionUiWithFollowUp(session)

    private fun requestWidgetFullUpdate(force: Boolean) = widgetUpdates.requestWidgetFullUpdate(force)

    private fun schedulePlaybackSnapshotPersist(immediate: Boolean = false) =
        snapshotStore.schedulePlaybackSnapshotPersist(immediate)

    override fun onVolumeChanged(volume: Float) {
        playbackVolume.onPlayerVolumeChanged(volume)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val player = mediaSession?.player ?: engine.masterPlayer
        Timber.tag(TAG).d("onIsPlayingChanged: $isPlaying. Duration: ${player.duration}, Seekable: ${player.isCurrentMediaItemSeekable}")
        syncLocalListeningStatsFromPlayer(player)
        runCatching { gestureController.onIsPlayingChanged(isPlaying) }

        if (isPlaying) {
            reportNavidromePlayback("playing")
            startNavidromePlaybackReporting()
        } else {
            val state = if (player.playbackState == Player.STATE_ENDED) "stopped" else "paused"
            reportNavidromePlayback(state)
            stopNavidromePlaybackReporting()
        }

        // Re-apply the last known RG volume immediately when resuming playback.
        // After a pause, ExoPlayer may reset the audio track volume internally,
        // causing a brief full-volume spike before the IO coroutine finishes.
        if (isPlaying && !engine.isTransitionRunning()) {
            lastAppliedReplayGainVolume?.let { setPlayerVolume(player, it) }
        }
        // Push state immediately so the watch can foreground Nobody Listens before
        // system media surfaces take over.
        requestWidgetFullUpdate(force = true)
        mediaSession?.let { refreshMediaSessionUi(it) }
        schedulePlaybackSnapshotPersist()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        when {
            playWhenReady -> clearHeadsetReconnectResume()
            !resumeOnHeadsetReconnectEnabled -> clearHeadsetReconnectResume()
            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                shouldResumeAfterHeadsetReconnect = true
                lastNoisyPauseRealtimeMs = SystemClock.elapsedRealtime()
                Timber.tag(TAG).d("Marked playback for headset reconnect resume")
            }
            else -> clearHeadsetReconnectResume()
        }
        requestWidgetFullUpdate(force = true)
        mediaSession?.let { refreshMediaSessionUi(it) }
        schedulePlaybackSnapshotPersist()
    }

    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
        val canSeek = availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        val player = engine.masterPlayer
        Timber.tag(TAG).w("onAvailableCommandsChanged. Can Seek Command? $canSeek. IsSeekable? ${player.isCurrentMediaItemSeekable}. Duration: ${player.duration}")
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        Timber.tag(TAG).d("Playback state changed: $playbackState")
        if (playbackState == Player.STATE_ENDED) {
            listeningStatsTracker.finalizeCurrentSession()
            val mediaItem = (mediaSession?.player ?: engine.masterPlayer).currentMediaItem
            getNavidromeId(mediaItem)?.let { navidromeId ->
                appScope.launch(Dispatchers.IO) {
                    navidromeRepository.scrobble(navidromeId, submission = true)
                }
            }

            endOfTrackTimerSongId = null
            reportNavidromePlayback("stopped")
            stopNavidromePlaybackReporting()
        } else {
            syncLocalListeningStatsFromPlayer(mediaSession?.player ?: engine.masterPlayer)
        }
        mediaSession?.let { refreshMediaSessionUi(it) }
        schedulePlaybackSnapshotPersist(immediate = playbackState == Player.STATE_IDLE)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        requestWidgetFullUpdate(force = true)
        schedulePlaybackSnapshotPersist(immediate = timeline.isEmpty)
        // Pre-fetch RG for the next track so the cache is warm before playback starts
        val player = engine.masterPlayer
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex != androidx.media3.common.C.INDEX_UNSET) {
            runCatching { prefetchReplayGain(player.getMediaItemAt(nextIndex)) }
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            val state = if (engine.masterPlayer.isPlaying) "playing" else "paused"
            reportNavidromePlayback(state)
        }

        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            val finishedItem = oldPosition.mediaItem
            if (isNavidromeMediaItem(finishedItem)) {
                val prevId = getNavidromeId(finishedItem)
                reportNavidromePlayback("stopped", finishedItem)
                if (prevId != null) {
                    appScope.launch(Dispatchers.IO) {
                        navidromeRepository.scrobble(prevId, submission = true)
                    }
                }
            }
        }

        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
            reason == Player.DISCONTINUITY_REASON_SEEK
        ) {
            val currentItem = mediaSession?.player?.currentMediaItem
            val oldMediaId = oldPosition.mediaItem?.mediaId
            val newMediaId = newPosition.mediaItem?.mediaId
            if (oldMediaId != null && oldMediaId == newMediaId) {
                // Same track (e.g. repeat, seek) — no IO needed, apply last known RG volume
                // immediately to avoid a spike while the coroutine reads tags again.
                lastAppliedReplayGainVolume?.let {
                    if (!engine.isTransitionRunning()) setPlayerVolume(engine.masterPlayer, it)
                }
            } else {
                // Different track — full recompute needed
                applyReplayGain(currentItem)
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        syncLocalListeningStatsFromPlayer(mediaSession?.player ?: engine.masterPlayer, forceNewSession = true)
        if (isNavidromeMediaItem(mediaItem)) {
            reportNavidromePlayback("starting")
            if (engine.masterPlayer.isPlaying) {
                startNavidromePlaybackReporting()
            }
        } else {
            stopNavidromePlaybackReporting()
        }

        val eotTargetSongId = endOfTrackTimerSongId
        if (!eotTargetSongId.isNullOrBlank()) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val previousSongId = engine.masterPlayer.run {
                    if (previousMediaItemIndex != C.INDEX_UNSET) {
                        runCatching { getMediaItemAt(previousMediaItemIndex).mediaId }.getOrNull()
                    } else {
                        null
                    }
                }
                if (previousSongId == eotTargetSongId) {
                    endOfTrackTimerSongId = null
                    engine.masterPlayer.seekTo(0L)
                    engine.masterPlayer.pause()
                    Timber.tag(TAG).d("Paused playback at end of track from Wear timer")
                }
            } else if (mediaItem?.mediaId != eotTargetSongId) {
                endOfTrackTimerSongId = null
                Timber.tag(TAG).d("Cleared end-of-track timer after manual track change")
            }
        }
        applyReplayGain(mediaSession?.player?.currentMediaItem)
        // Pre-fetch RG for the track after this one so it's cached when needed
        val player = engine.masterPlayer
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex != androidx.media3.common.C.INDEX_UNSET) {
            runCatching { prefetchReplayGain(player.getMediaItemAt(nextIndex)) }
        }
        // Optimization: Don't force-update widgets on every rapid skip.
        // Let the debounced updater handle it to prevent UI freezes.
        requestWidgetFullUpdate(force = false)
        mediaSession?.let { refreshMediaSessionUi(it) }
        schedulePlaybackSnapshotPersist()
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        // Some devices/apps deliver title/artist/art after transition callback.
        // Force an immediate publish for real-time watch metadata.
        requestWidgetFullUpdate(force = true)
        mediaSession?.let { refreshMediaSessionUiWithFollowUp(it) }
        // Only recompute RG if the track actually changed — onMediaMetadataChanged
        // also fires on queue edits (add/remove) without a track change, which would
        // launch a redundant IO coroutine and cause a brief volume spike.
        val currentMediaId = mediaSession?.player?.currentMediaItem?.mediaId
        if (currentMediaId != null && currentMediaId != lastReplayGainMediaId) {
            applyReplayGain(mediaSession?.player?.currentMediaItem)
        } else if (currentMediaId != null) {
            lastAppliedReplayGainVolume?.let {
                if (!engine.isTransitionRunning()) setPlayerVolume(engine.masterPlayer, it)
            }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        Timber.tag("MusicService")
            .d("playerListener.onShuffleModeEnabledChanged: $shuffleModeEnabled")
        requestWidgetFullUpdate(force = true)
        mediaSession?.let { refreshMediaSessionUi(it) }
        schedulePlaybackSnapshotPersist()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        requestWidgetFullUpdate(force = true)
        mediaSession?.let { refreshMediaSessionUi(it) }
        schedulePlaybackSnapshotPersist()
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.tag(TAG).e(error, "Error en el reproductor: ")
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
    }
}
