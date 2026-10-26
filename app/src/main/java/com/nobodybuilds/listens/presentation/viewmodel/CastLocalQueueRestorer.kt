package com.nobodybuilds.listens.presentation.viewmodel

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaStatus
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rebuilds the local player queue from the last known Cast state when a Cast session ends.
 * Owned by [CastTransferStateHolder].
 */
internal class CastLocalQueueRestorer(
    private val dualPlayerEngine: DualPlayerEngine,
    private val playbackStateHolder: PlaybackStateHolder,
    private val callbacks: CastTransferCallbacks
) {
    private val getSongsByIdMap: (() -> Map<String, Song>)? get() = callbacks.getSongsByIdMap
    private val updateQueue: ((List<Song>) -> Unit)? get() = callbacks.updateQueue

    suspend fun restoreLocalQueue(
        transferSnapshot: TransferSnapshot,
        lastItemId: Int?,
        lastRepeatMode: Int,
        wasPlaying: Boolean
    ) {
        // We defer getting the player until AFTER the suspension to avoid race conditions
        // where a transition might have released the reference we held.
        val queueData = withContext(Dispatchers.Default) {
            val fallbackQueue = if (transferSnapshot.lastKnownStatus?.queueItems?.isNotEmpty() == true) {
                transferSnapshot.lastKnownStatus.queueItems.mapNotNull { item ->
                    item.customData?.optString("songId")?.let { songId ->
                        getSongsByIdMap?.invoke()?.get(songId)
                    }
                }.toImmutableList()
            } else {
                transferSnapshot.lastRemoteQueue
            }
            val chosenQueue = if (fallbackQueue.isEmpty()) transferSnapshot.lastRemoteQueue else fallbackQueue
            val songMap = getSongsByIdMap?.invoke() ?: emptyMap()
            val finalQueue = chosenQueue.mapNotNull { song -> songMap[song.id] }

            val targetSongId = transferSnapshot.lastKnownStatus
                ?.getQueueItemById(lastItemId ?: 0)
                ?.customData
                ?.optString("songId")
                ?: transferSnapshot.lastRemoteSongId

            QueueTransferData(
                finalQueue = finalQueue,
                targetSongId = targetSongId,
                isShuffleEnabled = transferSnapshot.isShuffleEnabled
            )
        }

        if (queueData.finalQueue.isNotEmpty() && queueData.targetSongId != null) {
            val desiredRepeatMode = when (lastRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }

            // Reusing local queue logic simplification: always rebuild for safety/completeness
            val rebuildResult = withContext(Dispatchers.Default) {
                val startIndex = queueData.finalQueue.indexOfFirst { it.id == queueData.targetSongId }.coerceAtLeast(0)
                val mediaItems = queueData.finalQueue.map { song -> MediaItemBuilder.build(song) }
                RebuildArtifacts(startIndex, mediaItems, queueData.finalQueue.getOrNull(startIndex))
            }

            // Now retrieve the FRESH local player reference
            val localPlayer = dualPlayerEngine.masterPlayer

            localPlayer.shuffleModeEnabled = queueData.isShuffleEnabled
            localPlayer.repeatMode = desiredRepeatMode
            localPlayer.setMediaItems(rebuildResult.mediaItems, rebuildResult.startIndex, transferSnapshot.lastPosition)
            localPlayer.prepare()

            if (wasPlaying) localPlayer.play() else localPlayer.pause()

            // Sync UI
            updateQueue?.invoke(queueData.finalQueue)
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = rebuildResult.targetSong,
                    isPlaying = wasPlaying,
                    totalDuration = rebuildResult.targetSong?.duration ?: it.totalDuration,
                    isShuffleEnabled = queueData.isShuffleEnabled,
                    repeatMode = desiredRepeatMode
                )
            }

            if (wasPlaying) {
                playbackStateHolder.startProgressUpdates()
            } else {
                playbackStateHolder.setCurrentPosition(transferSnapshot.lastPosition)
            }
        }
    }

    // Helper Data Classes
    data class TransferSnapshot(
        val lastKnownStatus: MediaStatus?,
        val lastRemoteQueue: List<Song>,
        val lastRemoteSongId: String?,
        val lastRemoteStreamPosition: Long,
        val lastRemoteRepeatMode: Int,
        val wasPlaying: Boolean,
        val lastPosition: Long,
        val isShuffleEnabled: Boolean
    )
    
    private data class QueueTransferData(
        val finalQueue: List<Song>,
        val targetSongId: String?,
        val isShuffleEnabled: Boolean
    )

    private data class RebuildArtifacts(
        val startIndex: Int,
        val mediaItems: List<MediaItem>,
        val targetSong: Song?
    )
}
