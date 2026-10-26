package com.nobodybuilds.listens.presentation.viewmodel

import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nobodybuilds.listens.data.model.Song
import timber.log.Timber
import com.nobodybuilds.listens.utils.MediaItemBuilder
import com.nobodybuilds.listens.presentation.viewmodel.PlaybackStateHolder.Companion.TAG

/**
 * Applies a reordered queue (shuffle on or off) to the player with as few IPC calls as possible.
 * Owned by [PlaybackStateHolder].
 */
internal class PlaybackQueueReorderer(
    private val dualPlayerEngine: DualPlayerEngine
) {
    data class PreparedQueueReplacement(
        val mediaItems: List<MediaItem>,
        val targetIndex: Int
    )

    data class PreparedQueueSegments(
        val beforeCurrent: List<MediaItem>,
        val afterCurrent: List<MediaItem>
    )

    fun reorderQueueInPlace(player: Player, desiredQueue: List<Song>): Boolean {
        if (desiredQueue.isEmpty()) return false

        val currentCount = player.mediaItemCount
        if (currentCount != desiredQueue.size) {
            Timber.tag(TAG).w(
                "Cannot reorder queue in place: size mismatch (player=%d, desired=%d)",
                currentCount,
                desiredQueue.size
            )
            return false
        }

        val currentIds = MutableList(currentCount) { index ->
            player.getMediaItemAt(index).mediaId
        }
        val desiredIds = desiredQueue.map { it.id }

        val currentCounts = currentIds.groupingBy { it }.eachCount()
        val desiredCounts = desiredIds.groupingBy { it }.eachCount()
        if (currentCounts != desiredCounts) {
            Timber.tag(TAG).w("Cannot reorder queue in place: mediaId mismatch")
            return false
        }

        for (targetIndex in desiredIds.indices) {
            val desiredId = desiredIds[targetIndex]
            if (currentIds[targetIndex] == desiredId) continue

            var fromIndex = -1
            for (searchIndex in targetIndex + 1 until currentIds.size) {
                if (currentIds[searchIndex] == desiredId) {
                    fromIndex = searchIndex
                    break
                }
            }

            if (fromIndex == -1) {
                Timber.tag(TAG).w(
                    "Cannot reorder queue in place: target mediaId '%s' not found",
                    desiredId
                )
                return false
            }

            player.moveMediaItem(fromIndex, targetIndex)
            val movedId = currentIds.removeAt(fromIndex)
            currentIds.add(targetIndex, movedId)
        }

        return true
    }

    /**
     * Replaces the player timeline with [newQueue] in a single setMediaItems call,
     * preserving the currently playing song and its position. This is O(1) IPC calls
     * versus O(n) for reorderQueueInPlace, making it suitable for large queue shuffles.
     */
    suspend fun buildQueueReplacement(
        newQueue: List<Song>,
        targetIndex: Int,
        currentMediaItem: MediaItem?
    ): PreparedQueueReplacement = withContext(Dispatchers.Default) {
        val safeTargetIndex = targetIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        val mediaItems = List(newQueue.size) { index ->
            currentMediaItem
                ?.takeIf { index == safeTargetIndex && it.mediaId == newQueue[safeTargetIndex].id }
                ?: MediaItemBuilder.build(newQueue[index])
        }

        PreparedQueueReplacement(
            mediaItems = mediaItems,
            targetIndex = safeTargetIndex
        )
    }

    suspend fun buildQueueSegments(
        newQueue: List<Song>,
        currentIndex: Int,
        currentMediaItem: MediaItem?
    ): PreparedQueueSegments? = withContext(Dispatchers.Default) {
        val safeCurrentIndex = currentIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        val currentQueueSong = newQueue.getOrNull(safeCurrentIndex) ?: return@withContext null
        if (currentMediaItem?.mediaId != currentQueueSong.id) {
            return@withContext null
        }

        val beforeCurrent = List(safeCurrentIndex) { index ->
            MediaItemBuilder.build(newQueue[index])
        }
        val afterStartIndex = safeCurrentIndex + 1
        val afterCurrent = List((newQueue.size - afterStartIndex).coerceAtLeast(0)) { offset ->
            MediaItemBuilder.build(newQueue[afterStartIndex + offset])
        }

        PreparedQueueSegments(
            beforeCurrent = beforeCurrent,
            afterCurrent = afterCurrent
        )
    }

    fun replacePlayerQueuePreservingCurrent(
        currentIndex: Int,
        preparedSegments: PreparedQueueSegments
    ): Boolean {
        val masterPlayer = dualPlayerEngine.masterPlayer
        val mediaItemCount = masterPlayer.mediaItemCount
        if (currentIndex !in 0 until mediaItemCount) {
            return false
        }

        val afterStartIndex = currentIndex + 1
        if (preparedSegments.beforeCurrent.size != currentIndex) {
            return false
        }
        if (preparedSegments.afterCurrent.size != (mediaItemCount - afterStartIndex)) {
            return false
        }

        if (currentIndex > 0) {
            masterPlayer.replaceMediaItems(0, currentIndex, preparedSegments.beforeCurrent)
        }
        masterPlayer.replaceMediaItems(afterStartIndex, mediaItemCount, preparedSegments.afterCurrent)
        return masterPlayer.currentMediaItemIndex == currentIndex
    }

    fun replacePlayerQueue(
        player: Player,
        preparedQueue: PreparedQueueReplacement,
        currentPosition: Long
    ) {
        val shouldResumePlayback = player.playWhenReady || player.isPlaying
        val masterPlayer = dualPlayerEngine.masterPlayer

        masterPlayer.setMediaItems(
            preparedQueue.mediaItems,
            preparedQueue.targetIndex,
            currentPosition
        )

        if (shouldResumePlayback) {
            masterPlayer.playWhenReady = true
            if (!masterPlayer.isPlaying) {
                masterPlayer.play()
            }
        }
    }
}
