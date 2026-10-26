package com.nobodybuilds.listens.presentation.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaStatus
import com.nobodybuilds.listens.data.model.Song
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.abs

/**
 * Callbacks that [CastTransferStateHolder.initialize] receives from PlayerViewModel, shared with
 * the holder's Cast collaborators.
 */
internal class CastTransferCallbacks {
    // Provides current queue from UI state
    var getCurrentQueue: (() -> List<Song>)? = null
    // Syncs queue updates back to UI
    var updateQueue: ((List<Song>) -> Unit)? = null
    // Provides song lookups without rebuilding maps on every remote status update
    var getSongsByIdMap: (() -> Map<String, Song>)? = null
    // Callback when transfer is finished
    var onTransferBackComplete: (() -> Unit)? = null
    // Callback to ensure UI sheet is visible
    var onSheetVisible: (() -> Unit)? = null
    // Callback to handle disconnection/errors
    var onDisconnect: (() -> Unit)? = null
    // Callback to surface cast errors to UI
    var onCastError: ((String) -> Unit)? = null
    // Callback to update color scheme
    var onSongChanged: ((String?) -> Unit)? = null
}

/**
 * Follows what the Cast receiver reports (queue, current song, position) and mirrors it into
 * the playback state. Owned by [CastTransferStateHolder], which reads and resets the same state.
 */
internal class CastRemoteStatusTracker(
    private val castStateHolder: CastStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val callbacks: CastTransferCallbacks
) {
    private val CAST_LOG_TAG = "PlayerCastTransfer"

    private val getCurrentQueue: (() -> List<Song>)? get() = callbacks.getCurrentQueue
    private val updateQueue: ((List<Song>) -> Unit)? get() = callbacks.updateQueue
    private val getSongsByIdMap: (() -> Map<String, Song>)? get() = callbacks.getSongsByIdMap
    private val onSheetVisible: (() -> Unit)? get() = callbacks.onSheetVisible
    private val onSongChanged: ((String?) -> Unit)? get() = callbacks.onSongChanged

    // State tracking variables
    var lastRemoteMediaStatus: MediaStatus? = null
    var lastRemoteQueue: List<Song> = emptyList()
    var lastRemoteSongId: String? = null
    var lastRemoteStreamPosition: Long = 0L
    var lastRemoteRepeatMode: Int = MediaStatus.REPEAT_MODE_REPEAT_OFF
    var lastKnownRemoteIsPlaying: Boolean = false
    private var lastRemoteItemId: Int? = null

    var pendingRemoteSongId: String? = null
    var pendingRemoteSongMarkedAt: Long = 0L
    var pendingMismatchStatusRequestCount: Int = 0
    var lastPendingMismatchStatusRequestAt: Long = 0L
    var pendingForceJumpAttempts: Int = 0
    var lastPendingForceJumpAt: Long = 0L
    private var lastRemoteItemErrorSongId: String? = null
    private var lastRemoteItemErrorLoggedAt: Long = 0L
    private var lastRemoteIdleLogKey: String? = null
    private var lastRemoteIdleLoggedAt: Long = 0L

    fun handleRemoteStatusUpdate() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient ?: return
        val mediaStatus = remoteMediaClient.mediaStatus ?: return

        lastRemoteMediaStatus = mediaStatus
        
        val songMap = getSongsByIdMap?.invoke() ?: emptyMap()
        
        val newQueue = mediaStatus.queueItems.mapNotNull { item ->
            item.customData?.optString("songId")?.let { songId -> songMap[songId] }
        }.toImmutableList()

        val currentItemId = mediaStatus.getCurrentItemId()
        val currentRemoteItem = mediaStatus.getQueueItemById(currentItemId)
        val currentSongId = currentRemoteItem?.customData?.optString("songId")
        val streamPosition = mediaStatus.streamPosition

        if (castStateHolder.isRemotelySeeking.value) {
            val expectedPosition = castStateHolder.remotePosition.value
            // Unlock remote seek as soon as Cast reports the new position within tolerance.
            if (abs(streamPosition - expectedPosition) <= 1500L) {
                castStateHolder.setRemotelySeeking(false)
            }
        }

        val pendingId = pendingRemoteSongId
        val now = SystemClock.elapsedRealtime()
        val pendingAgeMs = now - pendingRemoteSongMarkedAt
        val pendingIsFresh = pendingId != null && pendingAgeMs < 4000
        if (pendingIsFresh && currentSongId != null && currentSongId != pendingId) {
            pendingMismatchStatusRequestCount += 1
            val shouldAcceptRemoteState =
                pendingAgeMs > 3500L || (pendingMismatchStatusRequestCount >= 12 && pendingAgeMs > 1800L)
            if (!shouldAcceptRemoteState) {
                if (pendingAgeMs >= 700L && now - lastPendingForceJumpAt >= 900L && pendingForceJumpAttempts < 1) {
                    val pendingItemId = mediaStatus.queueItems
                        .firstOrNull { it.customData?.optString("songId") == pendingId }
                        ?.itemId
                    if (pendingItemId != null && pendingItemId != currentItemId) {
                        pendingForceJumpAttempts += 1
                        lastPendingForceJumpAt = now
                        Log.w(
                            "PX_CAST_STATE",
                            "pending_force_jump pending=$pendingId current=$currentSongId itemId=$pendingItemId ageMs=$pendingAgeMs attempt=$pendingForceJumpAttempts"
                        )
                        castStateHolder.castPlayer?.jumpToItem(pendingItemId, 0L)
                    }
                }
                if (now - lastPendingMismatchStatusRequestAt >= 600L) {
                    lastPendingMismatchStatusRequestAt = now
                    remoteMediaClient.requestStatus()
                }
                return
            }
            Timber.tag(CAST_LOG_TAG).w(
                "Pending target mismatch persisted (pending=%s current=%s ageMs=%d attempts=%d). Accepting remote state.",
                pendingId,
                currentSongId,
                pendingAgeMs,
                pendingMismatchStatusRequestCount
            )
            Log.w(
                "PX_CAST_STATE",
                "pending_mismatch pending=$pendingId current=$currentSongId ageMs=$pendingAgeMs attempts=$pendingMismatchStatusRequestCount"
            )
            pendingRemoteSongId = null
            pendingRemoteSongMarkedAt = 0L
            pendingMismatchStatusRequestCount = 0
            pendingForceJumpAttempts = 0
            lastPendingForceJumpAt = 0L
        } else if (pendingId == null || currentSongId == pendingId) {
            pendingMismatchStatusRequestCount = 0
            pendingForceJumpAttempts = 0
            lastPendingForceJumpAt = 0L
        }

        if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            mediaStatus.idleReason != MediaStatus.IDLE_REASON_NONE) {
            val idleSongId = currentSongId ?: lastRemoteSongId ?: pendingRemoteSongId
            val idleLogKey = "$idleSongId:${mediaStatus.idleReason}:$currentItemId"
            if (idleLogKey != lastRemoteIdleLogKey || now - lastRemoteIdleLoggedAt > 3000L) {
                lastRemoteIdleLogKey = idleLogKey
                lastRemoteIdleLoggedAt = now
                val durationMs = remoteMediaClient.streamDuration
                val prematureFinish = mediaStatus.idleReason == MediaStatus.IDLE_REASON_FINISHED &&
                    durationMs > 0L &&
                    streamPosition in 0 until (durationMs - 3000L)
                Log.w(
                    "PX_CAST_STATE",
                    "idle songId=$idleSongId itemId=$currentItemId reason=${mediaStatus.idleReason} streamPos=$streamPosition duration=$durationMs pending=$pendingRemoteSongId prematureFinish=$prematureFinish"
                )
                Log.w(
                    "PX_CAST_STATE",
                    "idle media title=${currentRemoteItem?.media?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)} contentType=${currentRemoteItem?.media?.contentType} contentId=${currentRemoteItem?.media?.contentId}"
                )
            }
        }

        if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            mediaStatus.idleReason == MediaStatus.IDLE_REASON_ERROR) {
            val errorSongId = currentSongId ?: lastRemoteSongId
            if (errorSongId != null &&
                (errorSongId != lastRemoteItemErrorSongId || now - lastRemoteItemErrorLoggedAt > 4000L)
            ) {
                lastRemoteItemErrorSongId = errorSongId
                lastRemoteItemErrorLoggedAt = now
                Timber.tag(CAST_LOG_TAG).e(
                    "Remote item playback error. songId=%s itemId=%d queueRepeat=%d streamPos=%d duration=%d pending=%s queueSize=%d",
                    errorSongId,
                    currentItemId,
                    mediaStatus.queueRepeatMode,
                    streamPosition,
                    remoteMediaClient.streamDuration,
                    pendingRemoteSongId,
                    mediaStatus.queueItems.size
                )
                Log.e(
                    "PX_CAST_ITEM_ERROR",
                    "songId=$errorSongId itemId=$currentItemId idleReason=${mediaStatus.idleReason} streamPos=$streamPosition duration=${remoteMediaClient.streamDuration} queueSize=${mediaStatus.queueItems.size}"
                )
                Timber.tag(CAST_LOG_TAG).e(
                    "Remote item media info. title=%s contentType=%s contentId=%s",
                    currentRemoteItem?.media?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE),
                    currentRemoteItem?.media?.contentType,
                    currentRemoteItem?.media?.contentId
                )
                Log.e(
                    "PX_CAST_ITEM_ERROR",
                    "media title=${currentRemoteItem?.media?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)} contentType=${currentRemoteItem?.media?.contentType} contentId=${currentRemoteItem?.media?.contentId}"
                )
            }
        }

        val itemChanged = lastRemoteItemId != currentItemId
        if (itemChanged) {
             lastRemoteItemId = currentItemId
             if (pendingRemoteSongId != null && pendingRemoteSongId != currentSongId) {
                 pendingRemoteSongId = null
                 pendingMismatchStatusRequestCount = 0
                 pendingForceJumpAttempts = 0
                 lastPendingForceJumpAt = 0L
             }
             castStateHolder.setRemotelySeeking(false)
             castStateHolder.setRemotePosition(streamPosition)
             playbackStateHolder.setCurrentPosition(streamPosition)
        }

        var queueForUi = getCurrentQueue?.invoke() ?: emptyList()
        if (newQueue.isNotEmpty()) {
            val isShrunkSubset = newQueue.size < lastRemoteQueue.size && newQueue.all { song ->
                lastRemoteQueue.any { it.id == song.id }
            }
            if (!isShrunkSubset || lastRemoteQueue.isEmpty()) {
                lastRemoteQueue = newQueue
                queueForUi = newQueue
            } else {
                // Some Cast status updates report only a small window of the queue
                // (often current + next). Keep the last known full queue in UI.
                queueForUi = if (lastRemoteQueue.isNotEmpty()) lastRemoteQueue else queueForUi
            }
        } else if (lastRemoteQueue.isNotEmpty()) {
            queueForUi = lastRemoteQueue
        }
        
        // Update current song
        val reportedSong = currentSongId?.let { songMap[it] }
        val effectiveSong = resolvePendingRemoteSong(reportedSong, currentSongId, songMap)
        val effectiveSongId = effectiveSong?.id ?: currentSongId ?: lastRemoteSongId
        
        if (effectiveSongId != null) {
            lastRemoteSongId = effectiveSongId
        }
        
        val currentSongFallback = effectiveSong 
            ?: run {
                val pId = pendingRemoteSongId
                val stableSong = playbackStateHolder.stablePlayerState.value.currentSong
                if (pId != null && pId == stableSong?.id && SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000) {
                    stableSong
                } else {
                    playbackStateHolder.stablePlayerState.value.currentSong
                }
            }
            ?: lastRemoteQueue.firstOrNull { it.id == lastRemoteSongId }

        val songChanged = currentSongFallback?.id != playbackStateHolder.stablePlayerState.value.currentSong?.id

        val isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING
        lastKnownRemoteIsPlaying = isPlaying
        lastRemoteStreamPosition = streamPosition
        lastRemoteRepeatMode = mediaStatus.queueRepeatMode
        val effectiveDurationMs = when {
            remoteMediaClient.streamDuration > 0L -> remoteMediaClient.streamDuration
            (currentSongFallback?.duration ?: 0L) > 0L -> currentSongFallback?.duration ?: 0L
            else -> playbackStateHolder.stablePlayerState.value.totalDuration.coerceAtLeast(0L)
        }

        if (!castStateHolder.isRemotelySeeking.value) {
            castStateHolder.setRemotePosition(streamPosition)
            playbackStateHolder.setCurrentPosition(streamPosition)
             playbackStateHolder.updateStablePlayerState {
                 it.copy(
                     totalDuration = effectiveDurationMs,
                     currentSong = currentSongFallback,
                     lyrics = if (songChanged) null else it.lyrics,
                     isLoadingLyrics = if (songChanged && currentSongFallback != null) true else it.isLoadingLyrics,
                     isPlaying = isPlaying,
                     repeatMode = if (mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_SINGLE) Player.REPEAT_MODE_ONE
                                  else if (mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL || mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE) Player.REPEAT_MODE_ALL
                                  else Player.REPEAT_MODE_OFF,
                     isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
                 )
             }
        }

        if (songChanged) {
            // Trigger theme + dependent UI updates only after state has the new song.
            onSongChanged?.invoke(currentSongFallback?.albumArtUriString)
        }
        
        // Update Queue if changed
        val previousQueue = getCurrentQueue?.invoke() ?: emptyList()
        if (queueForUi.isNotEmpty() && queueForUi != previousQueue) {
             updateQueue?.invoke(queueForUi)
        }
        
        if (newQueue.isNotEmpty() || previousQueue.isNotEmpty()) {
            onSheetVisible?.invoke()
        }
    }

    fun markPendingRemoteSong(song: Song) {
        pendingRemoteSongId = song.id
        pendingRemoteSongMarkedAt = SystemClock.elapsedRealtime()
        pendingMismatchStatusRequestCount = 0
        lastPendingMismatchStatusRequestAt = 0L
        pendingForceJumpAttempts = 0
        lastPendingForceJumpAt = 0L
        lastRemoteSongId = song.id
        lastRemoteItemId = null
        Timber.tag(CAST_LOG_TAG).d("Marked pending remote song: %s", song.id)

        val songChanged = playbackStateHolder.stablePlayerState.value.currentSong?.id != song.id
        playbackStateHolder.updateStablePlayerState { state ->
            state.copy(
                currentSong = song,
                lyrics = if (songChanged) null else state.lyrics,
                isLoadingLyrics = if (songChanged) true else state.isLoadingLyrics
            )
        }
        onSheetVisible?.invoke()

        if (songChanged) {
            onSongChanged?.invoke(song.albumArtUriString)
        }

        val queue = getCurrentQueue?.invoke() ?: lastRemoteQueue
        val updatedQueue = if (queue.any { it.id == song.id } || queue.isEmpty()) {
            queue
        } else {
            queue + song
        }
        
        if (updatedQueue != queue) {
             updateQueue?.invoke(updatedQueue)
        }
        
        castStateHolder.setRemotePosition(0L)
        playbackStateHolder.setCurrentPosition(0L)
    }

    suspend fun alignRemotePlaybackToSong(targetSongId: String) {
        val remoteClient = castStateHolder.castSession.value?.remoteMediaClient ?: return
        var lastObservedSongId: String? = null
        repeat(2) { attempt ->
            if (attempt > 0) delay(350L)
            runCatching { remoteClient.requestStatus() }
            delay(120L)

            val status = remoteClient.mediaStatus ?: return@repeat
            val currentSongId = status.getQueueItemById(status.currentItemId)
                ?.customData
                ?.optString("songId")
                ?.takeIf { it.isNotBlank() }
            if (currentSongId == targetSongId) {
                Log.i("PX_CAST_QLOAD", "align ok targetSongId=$targetSongId attempt=$attempt")
                return
            }

            if (attempt > 0 && currentSongId != null && currentSongId == lastObservedSongId) {
                Log.w(
                    "PX_CAST_QLOAD",
                    "align stuck targetSongId=$targetSongId currentSongId=$currentSongId attempt=$attempt"
                )
                return
            }

            val targetItemId = status.queueItems
                .firstOrNull { it.customData?.optString("songId") == targetSongId }
                ?.itemId

            if (targetItemId != null && targetItemId != status.currentItemId) {
                Log.w(
                    "PX_CAST_QLOAD",
                    "align jump targetSongId=$targetSongId currentSongId=$currentSongId itemId=$targetItemId attempt=$attempt"
                )
                castStateHolder.castPlayer?.jumpToItem(targetItemId, 0L)
            } else {
                Log.w(
                    "PX_CAST_QLOAD",
                    "align miss targetSongId=$targetSongId currentSongId=$currentSongId queueSize=${status.queueItems.size} attempt=$attempt"
                )
            }
            lastObservedSongId = currentSongId
        }
    }

    private fun resolvePendingRemoteSong(
        reportedSong: Song?,
        currentSongId: String?,
        songMap: Map<String, Song>
    ): Song? {
        if (reportedSong != null) return reportedSong

        val pendingId = pendingRemoteSongId
        if (pendingId == null) return null

        val pendingIsFresh = SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000
        if (!pendingIsFresh) return null

        if (currentSongId != pendingId) {
             return null
        }

        return songMap[pendingId]
    }
}
