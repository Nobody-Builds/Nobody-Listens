package com.nobodybuilds.listens.data.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.nobodybuilds.listens.data.model.PlaybackQueueItemSnapshot
import com.nobodybuilds.listens.data.model.PlaybackQueueSnapshot
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Saves the queue, position and flags so playback can be restored after the process dies, and
 * restores them on a cold start.
 */
internal class PlaybackSnapshotStore(
    private val context: Context,
    private val engine: DualPlayerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val serviceScope: CoroutineScope,
    private val state: MusicServiceState
) {
    private val isPlaybackUnloadInProgress by state::isPlaybackUnloadInProgress
    private var isManualShuffleEnabled by state::isManualShuffleEnabled
    private val keepPlayingInBackground by state::keepPlayingInBackground
    private var playbackSnapshotPersistJob: Job? = null
    private var isRestoringPlaybackSnapshot = false

    fun cancelPendingPersist() {
        playbackSnapshotPersistJob?.cancel()
    }

    fun schedulePlaybackSnapshotPersist(immediate: Boolean = false) {
        if (isPlaybackUnloadInProgress) {
            return
        }
        playbackSnapshotPersistJob?.cancel()
        playbackSnapshotPersistJob = serviceScope.launch {
            if (!immediate) {
                delay(PLAYBACK_SNAPSHOT_DEBOUNCE_MS)
            }
            persistPlaybackSnapshot()
        }
    }

    private suspend fun persistPlaybackSnapshot(playWhenReadyOverride: Boolean? = null) {
        if (isRestoringPlaybackSnapshot) return
        val snapshot = capturePlaybackSnapshot(playWhenReadyOverride)
        runCatching {
            userPreferencesRepository.setPlaybackQueueSnapshot(snapshot)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to persist playback snapshot")
        }
    }

    private suspend fun capturePlaybackSnapshot(playWhenReadyOverride: Boolean? = null): PlaybackQueueSnapshot? =
        withContext(Dispatchers.Main.immediate) {
            capturePlaybackSnapshotFromPlayer(playWhenReadyOverride)
        }

    private fun capturePlaybackSnapshotFromPlayer(
        playWhenReadyOverride: Boolean? = null
    ): PlaybackQueueSnapshot? {
        val player = engine.masterPlayer
        val mediaItemCount = player.mediaItemCount
        if (mediaItemCount <= 0) {
            return null
        }

        val snapshotItems = ArrayList<PlaybackQueueItemSnapshot>(mediaItemCount)
        for (index in 0 until mediaItemCount) {
            val mediaItem = player.getMediaItemAt(index)
            val metadata = mediaItem.mediaMetadata
            val uri = mediaItem.localConfiguration?.uri?.toString()
                ?: metadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)

            if (mediaItem.mediaId.isBlank() || uri.isNullOrBlank()) {
                continue
            }

            val durationMs = metadata.extras
                ?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION)
                ?.takeIf { it > 0L }

            snapshotItems.add(
                PlaybackQueueItemSnapshot(
                    mediaId = mediaItem.mediaId,
                    uri = uri,
                    title = metadata.title?.toString(),
                    artist = metadata.artist?.toString(),
                    albumTitle = metadata.albumTitle?.toString(),
                    artworkUri = resolveStoredArtworkUriString(metadata),
                    durationMs = durationMs,
                )
            )
        }

        if (snapshotItems.isEmpty()) {
            return null
        }

        val currentMediaId = player.currentMediaItem?.mediaId
        val indexFromMediaId = currentMediaId
            ?.let { id -> snapshotItems.indexOfFirst { it.mediaId == id } }
            ?.takeIf { it >= 0 }

        val safeCurrentIndex = when {
            indexFromMediaId != null -> indexFromMediaId
            player.currentMediaItemIndex in snapshotItems.indices -> player.currentMediaItemIndex
            else -> 0
        }

        val safeRepeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF,
            Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ALL -> player.repeatMode
            else -> Player.REPEAT_MODE_OFF
        }

        return PlaybackQueueSnapshot(
            items = snapshotItems,
            currentMediaId = currentMediaId,
            currentIndex = safeCurrentIndex,
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = playWhenReadyOverride ?: player.playWhenReady,
            repeatMode = safeRepeatMode,
            shuffleEnabled = isManualShuffleEnabled,
        )
    }

    suspend fun restorePlaybackQueueSnapshotIfNeeded() {
        val alreadyHasQueue = withContext(Dispatchers.Main.immediate) {
            engine.masterPlayer.mediaItemCount > 0
        }
        if (alreadyHasQueue) return

        val snapshot = runCatching {
            userPreferencesRepository.getPlaybackQueueSnapshotOnce()
        }.getOrNull() ?: return

        if (snapshot.items.isEmpty()) {
            return
        }

        val allowBackgroundPlayback = runCatching {
            userPreferencesRepository.keepPlayingInBackgroundFlow.first()
        }.getOrDefault(keepPlayingInBackground)
        val shouldRestorePlaying = snapshot.playWhenReady && allowBackgroundPlayback

        val restoredItems = snapshot.items.mapNotNull(::buildMediaItemFromSnapshot)
        if (restoredItems.isEmpty()) {
            userPreferencesRepository.setPlaybackQueueSnapshot(null)
            return
        }

        val resolvedIndex = when {
            snapshot.currentIndex in restoredItems.indices -> snapshot.currentIndex
            !snapshot.currentMediaId.isNullOrBlank() -> {
                restoredItems.indexOfFirst { it.mediaId == snapshot.currentMediaId }
                    .takeIf { it >= 0 } ?: 0
            }
            else -> 0
        }

        val preparedItems = restoredItems.toMutableList()
        preparedItems.getOrNull(resolvedIndex)?.let { currentItem ->
            val resolvedCurrentItem = runCatching { engine.resolveMediaItem(currentItem) }.getOrNull()
            if (resolvedCurrentItem != null && resolvedCurrentItem != currentItem) {
                preparedItems[resolvedIndex] = resolvedCurrentItem
            }
        }

        withContext(Dispatchers.Main.immediate) {
            val player = engine.masterPlayer
            if (player.mediaItemCount > 0) {
                return@withContext
            }

            val safeRepeatMode = when (snapshot.repeatMode) {
                Player.REPEAT_MODE_OFF,
                Player.REPEAT_MODE_ONE,
                Player.REPEAT_MODE_ALL -> snapshot.repeatMode
                else -> Player.REPEAT_MODE_OFF
            }

            isRestoringPlaybackSnapshot = true
            try {
                player.setMediaItems(
                    preparedItems,
                    resolvedIndex,
                    snapshot.currentPositionMs.coerceAtLeast(0L)
                )
                // Even paused restores must prepare the timeline so duration/seek state is
                // available immediately when the UI opens after a cold start.
                player.prepare()
                player.repeatMode = safeRepeatMode
                player.shuffleModeEnabled = false
                isManualShuffleEnabled = snapshot.shuffleEnabled
                if (shouldRestorePlaying) {
                    player.playWhenReady = true
                } else {
                    player.playWhenReady = false
                }
            } finally {
                isRestoringPlaybackSnapshot = false
            }
        }

        Timber.tag(TAG).i(
            "Restored playback snapshot: items=%d index=%d playWhenReady=%s",
            restoredItems.size,
            snapshot.currentIndex,
            shouldRestorePlaying
        )
        schedulePlaybackSnapshotPersist(immediate = true)
    }

    private fun buildMediaItemFromSnapshot(snapshotItem: PlaybackQueueItemSnapshot): MediaItem? {
        if (snapshotItem.mediaId.isBlank() || snapshotItem.uri.isBlank()) {
            return null
        }

        val metadataBuilder = MediaMetadata.Builder()
        snapshotItem.title?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setTitle(it) }
        snapshotItem.artist?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setArtist(it) }
        snapshotItem.albumTitle?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setAlbumTitle(it) }
        MediaItemBuilder.externalControllerArtworkUri(context, snapshotItem.artworkUri)
            ?.let { metadataBuilder.setArtworkUri(it) }

        val extras = Bundle().apply {
            putBoolean(
                MediaItemBuilder.EXTERNAL_EXTRA_FLAG,
                snapshotItem.mediaId.startsWith("external:")
            )
            putString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI, snapshotItem.uri)
            snapshotItem.albumTitle?.takeIf { it.isNotBlank() }?.let {
                putString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM, it)
            }
            snapshotItem.artworkUri?.takeIf { it.isNotBlank() }?.let {
                putString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART, it)
            }
            snapshotItem.durationMs?.takeIf { it > 0L }?.let {
                putLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, it)
            }
        }
        metadataBuilder.setExtras(extras)

        return MediaItem.Builder()
            .setMediaId(snapshotItem.mediaId)
            .setUri(MediaItemBuilder.playbackUri(snapshotItem.uri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun persistPlaybackSnapshotBlocking() {
        val snapshot = capturePlaybackSnapshotFromPlayer(playWhenReadyOverride = false)
        writePlaybackSnapshotBlocking(snapshot)
    }

    fun clearPlaybackSnapshotBlocking() {
        writePlaybackSnapshotBlocking(null)
    }

    private fun writePlaybackSnapshotBlocking(snapshot: PlaybackQueueSnapshot?) {
        runCatching {
            runBlocking(Dispatchers.IO) {
                userPreferencesRepository.setPlaybackQueueSnapshot(snapshot)
            }
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to persist playback snapshot during unload")
        }
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
        // Queue/index/flags snapshot is only used for restore on process death. A full-queue
        // JSON+DataStore rewrite on every Media3 event (track transition fires 3-4 listeners
        // within ~200ms) is unnecessary work. 1500ms coalesces those without harming restore.
        const val PLAYBACK_SNAPSHOT_DEBOUNCE_MS = 1500L
    }
}
