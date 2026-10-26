package com.nobodybuilds.listens.data.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.nobodybuilds.listens.data.media.ReplayGainManager
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

/**
 * The playback volume for [MusicService]: ReplayGain (including across crossfades), the user's
 * own volume, and the Sleep space fade-out, which rides on top of both.
 */
internal class PlaybackVolumeController(
    private val engine: DualPlayerEngine,
    private val replayGainManager: ReplayGainManager,
    private val serviceScope: CoroutineScope,
    private val sessionProvider: () -> MediaSession?
) {
    private val mediaSession: MediaSession? get() = sessionProvider()

    var replayGainEnabled = false
    var replayGainUseAlbumGain = false
    var replayGainJob: Job? = null
        private set
    private var replayGainRequestToken = 0L
    var userSelectedVolume = 1f
    private var expectedReplayGainVolume: Float? = null
    private var pendingReplayGainVolume: Float? = null
    // Last successfully applied RG volume — used to avoid a full-volume spike
    // during the IO read for the next track (Repeat/Shuffle/Queue changes).
    var lastAppliedReplayGainVolume: Float? = null
        private set
    // MediaId for which lastAppliedReplayGainVolume was computed.
    var lastReplayGainMediaId: String? = null
        private set

    fun prepareReplayGainForTransitionPlayer(player: Player) {
        // Pre-compute ReplayGain for the incoming track while the crossfade is still running.
        // isTransitionRunning() is true here, so applyReplayGain stores the result as
        // pendingReplayGainVolume. onTransitionFinished() applies it cleanly once the fade
        // loop ends, avoiding any volume jump on the incoming track.
        //
        // Also try to set incomingTrackReplayGainVolume immediately from cache so the
        // fade loop can use the correct final volume even before the IO coroutine finishes.
        val incomingItem = player.currentMediaItem
        val cachedVolume = getCachedReplayGainVolume(incomingItem)
        if (cachedVolume != null) {
            engine.incomingTrackReplayGainVolume = cachedVolume
        }
        applyReplayGain(incomingItem)
    }

    /** The player's volume changed: a change not made by ReplayGain is the user's own volume. */
    fun onPlayerVolumeChanged(volume: Float) {
        if (engine.isTransitionRunning()) return
        val expectedVolume = expectedReplayGainVolume
        if (expectedVolume != null && abs(expectedVolume - volume) < 0.001f) {
            expectedReplayGainVolume = null
            return
        }
        expectedReplayGainVolume = null
        userSelectedVolume = volume.coerceIn(0f, 1f)
    }

    /**
     * Applies ReplayGain volume normalization to the current track.
     * Reads RG tags from the file and adjusts player.volume accordingly.
     */
    fun applyReplayGain(mediaItem: MediaItem?) {
        replayGainJob?.cancel()
        replayGainRequestToken += 1
        val requestToken = replayGainRequestToken

        if (mediaItem == null) {
            return
        }

        if (!replayGainEnabled) {
            pendingReplayGainVolume = null
            if (!engine.isTransitionRunning()) {
                setPlayerVolume(engine.masterPlayer, userSelectedVolume)
            }
            return
        }

        val mediaId = mediaItem.mediaId
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)

        if (filePath.isNullOrBlank()) {
            Timber.tag(TAG).d("ReplayGain: No file path for track, keeping user-selected volume")
            if (!engine.isTransitionRunning()) {
                setPlayerVolume(engine.masterPlayer, userSelectedVolume)
            }
            return
        }

        val useAlbumGain = replayGainUseAlbumGain

        // Apply the last known RG volume immediately so there is no full-volume spike
        // while the IO coroutine reads the tags for the new track.
        if (!engine.isTransitionRunning()) {
            lastAppliedReplayGainVolume?.let { setPlayerVolume(engine.masterPlayer, it) }
        }

        // Read ReplayGain tags on IO thread to avoid blocking main
        replayGainJob = serviceScope.launch {
            val rgValues = withContext(Dispatchers.IO) {
                replayGainManager.readReplayGain(filePath)
            }

            if (requestToken != replayGainRequestToken) {
                return@launch
            }

            val currentMediaId = mediaSession?.player?.currentMediaItem?.mediaId
            if (currentMediaId != mediaId) {
                Timber.tag(TAG).d("ReplayGain: Ignoring stale result for mediaId=%s", mediaId)
                return@launch
            }

            val volume = replayGainManager.getVolumeMultiplier(
                rgValues,
                useAlbumGain = useAlbumGain
            )

            if (engine.isTransitionRunning()) {
                // Store for application after transition completes.
                // Also pass to engine so the crossfade loop ends at the correct RG
                // volume instead of hard-coding 1f, preventing the audible jump.
                pendingReplayGainVolume = volume
                engine.incomingTrackReplayGainVolume = volume
                Timber.tag(TAG).d("ReplayGain: Stored pending volume=%.2f for %s (transition running)",
                    volume, mediaItem.mediaMetadata.title
                )
            } else {
                pendingReplayGainVolume = null
                engine.incomingTrackReplayGainVolume = null
                lastAppliedReplayGainVolume = volume
                lastReplayGainMediaId = mediaId
                setPlayerVolume(engine.masterPlayer, volume)
                Timber.tag(TAG).d("ReplayGain: Applied volume=%.2f for %s",
                    volume, mediaItem.mediaMetadata.title
                )
            }
        }
    }

    /**
     * Returns the cached ReplayGain volume for a media item if already computed, or null.
     * Does NOT trigger an IO read — only reads from the in-memory cache.
     */
    private fun getCachedReplayGainVolume(mediaItem: MediaItem?): Float? {
        if (!replayGainEnabled || mediaItem == null) return null
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH) ?: return null
        if (filePath.isBlank()) return null
        val cached = replayGainManager.getCachedReplayGain(filePath) ?: return null
        return replayGainManager.getVolumeMultiplier(cached, useAlbumGain = replayGainUseAlbumGain)
    }

    /**
     * Pre-fetches ReplayGain tags for a media item into the cache without applying the volume.
     * Called on queue changes and track transitions so the cache is warm by the time
     * applyReplayGain() runs, avoiding the 1-2s JNI read delay on playback start.
     */
    fun prefetchReplayGain(mediaItem: MediaItem?) {
        if (!replayGainEnabled || mediaItem == null) return
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH) ?: return
        if (filePath.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            replayGainManager.readReplayGain(filePath)
        }
    }

    fun setPlayerVolume(player: Player, volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        // During a Sleep fade the fade level rides on top of ReplayGain / the user's volume.
        val applied = if (sleepFadeBase != null) {
            sleepFadeBase = clampedVolume
            clampedVolume * sleepFadeLevel
        } else clampedVolume
        expectedReplayGainVolume = applied
        player.volume = applied
    }

    // ---------------------------------------------------------------- Sleep space fade
    // Owned here (not by the UI), so the volume is always put back, even with the app swiped away.
    // In memory only: a new service starts at the normal volume.

    private var sleepFadeJob: Job? = null
    /** The volume without the fade, while a fade runs. */
    private var sleepFadeBase: Float? = null
    private var sleepFadeLevel = 1f

    fun startSleepFade(startAt: Long, endAt: Long) {
        cancelSleepFade(restore = true)
        if (endAt <= System.currentTimeMillis()) return
        sleepFadeJob = serviceScope.launch {
            delay((startAt - System.currentTimeMillis()).coerceAtLeast(0L))
            val player = engine.masterPlayer
            sleepFadeBase = player.volume.coerceIn(0f, 1f)
            val total = (endAt - System.currentTimeMillis()).coerceAtLeast(1_000L)
            val steps = (total / 5_000L).toInt().coerceIn(10, 360)
            for (i in steps downTo 1) {
                if (!player.playWhenReady) { cancelSleepFade(restore = true); return@launch } // paused by hand
                val f = i / steps.toFloat()
                sleepFadeLevel = f * f
                applySleepFadeVolume()
                delay(total / steps)
            }
            // The sleep timer pauses now; pause here too in case it wasn't set, then restore.
            delay(1_500L)
            runCatching { if (player.isPlaying) player.pause() }
            cancelSleepFade(restore = true)
        }
    }

    private fun applySleepFadeVolume() {
        val base = sleepFadeBase ?: return
        val v = (base * sleepFadeLevel).coerceIn(0f, 1f)
        expectedReplayGainVolume = v
        engine.masterPlayer.volume = v
    }

    fun cancelSleepFade(restore: Boolean) {
        val job = sleepFadeJob
        sleepFadeJob = null
        job?.cancel() // also fine from inside the job: the restore below still runs
        val base = sleepFadeBase
        sleepFadeBase = null
        sleepFadeLevel = 1f
        if (restore && base != null) {
            expectedReplayGainVolume = base
            engine.masterPlayer.volume = base
        }
    }

    fun onTransitionFinished() {
        val player = engine.masterPlayer
        val pending = pendingReplayGainVolume
        pendingReplayGainVolume = null

        if (!replayGainEnabled) {
            setPlayerVolume(player, userSelectedVolume)
            Timber.tag(TAG).d("ReplayGain: Transition finished, RG disabled — restored userSelectedVolume=%.2f", userSelectedVolume)
            return
        }

        if (pending != null) {
            // The crossfade loop ramps to this value; apply it now as the stable post-fade volume.
            // Also update lastAppliedReplayGainVolume so any subsequent onPositionDiscontinuity
            // (REASON_AUTO_TRANSITION fires right after crossfade ends) uses this value
            // immediately instead of launching a new IO coroutine and causing a spike.
            lastAppliedReplayGainVolume = pending
            setPlayerVolume(player, pending)
            Timber.tag(TAG).d("ReplayGain: Transition finished, applied pending volume=%.2f", pending)
        } else {
            // No pending volume was computed during transition, trigger full computation
            applyReplayGain(mediaSession?.player?.currentMediaItem)
            Timber.tag(TAG).d("ReplayGain: Transition finished, no pending volume — triggering full recomputation")
        }
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
    }
}
