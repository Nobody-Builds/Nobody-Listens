package com.nobodybuilds.listens.data.service.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * [DualPlayerEngine]'s copy of the full queue, plus where the active and the prepared player's
 * timelines start in it. Long queues are given to the auxiliary player as a window of at most
 * [MAX_AUXILIARY_TIMELINE_ITEMS] songs, so its indexes must be mapped back to the full queue.
 */
internal class DualPlayerQueueWindow {
    var queueSnapshot: List<MediaItem> = emptyList()
        private set
    private var activeWindowStartIndex = 0
    private var activePlayerUsesWindowedQueue = false
    private var preparedWindowStartIndex = 0
    private var preparedPlayerUsesWindowedQueue = false

    fun reset() {
        queueSnapshot = emptyList()
        activeWindowStartIndex = 0
        activePlayerUsesWindowedQueue = false
        resetPreparedWindowState()
    }

    fun setPreparedWindow(startIndex: Int, usesWindowedQueue: Boolean) {
        preparedWindowStartIndex = startIndex
        preparedPlayerUsesWindowedQueue = usesWindowedQueue
    }

    /** The prepared player became the master: its window is now the active one. */
    fun adoptPreparedWindow() {
        activeWindowStartIndex = preparedWindowStartIndex
        activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
        resetPreparedWindowState()
    }

    fun ensureQueueSnapshot(playerA: ExoPlayer): List<MediaItem> {
        if (!activePlayerUsesWindowedQueue && queueSnapshot.size != playerA.mediaItemCount) {
            refreshQueueSnapshotFromMaster(playerA, windowStartIndex = 0, usesWindowedQueue = false)
        }
        if (queueSnapshot.isEmpty()) {
            refreshQueueSnapshotFromMaster(playerA, windowStartIndex = 0, usesWindowedQueue = false)
        }
        return queueSnapshot
    }

    fun refreshQueueSnapshotFromMaster(playerA: ExoPlayer, windowStartIndex: Int, usesWindowedQueue: Boolean) {
        val count = playerA.mediaItemCount
        if (count <= 0) {
            queueSnapshot = emptyList()
            activeWindowStartIndex = 0
            activePlayerUsesWindowedQueue = false
            return
        }

        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            items.add(playerA.getMediaItemAt(i))
        }

        queueSnapshot = items
        activeWindowStartIndex = windowStartIndex
        activePlayerUsesWindowedQueue = usesWindowedQueue
    }

    fun resolveCurrentAbsoluteIndex(playerA: ExoPlayer, mediaItem: MediaItem, snapshot: List<MediaItem>): Int {
        if (snapshot.isEmpty()) return C.INDEX_UNSET

        val playerIndex = playerA.currentMediaItemIndex
        if (activePlayerUsesWindowedQueue) {
            val absoluteIndex = activeWindowStartIndex + playerIndex
            if (absoluteIndex in snapshot.indices &&
                snapshot[absoluteIndex].mediaId == mediaItem.mediaId
            ) {
                return absoluteIndex
            }
        } else if (playerIndex in snapshot.indices &&
            snapshot[playerIndex].mediaId == mediaItem.mediaId
        ) {
            return playerIndex
        }

        return findMediaItemIndex(snapshot, mediaItem.mediaId, preferAfterExclusive = C.INDEX_UNSET)
    }

    fun findMediaItemIndex(
        items: List<MediaItem>,
        mediaId: String,
        preferAfterExclusive: Int
    ): Int {
        var fallback = C.INDEX_UNSET
        for (i in items.indices) {
            if (items[i].mediaId == mediaId) {
                if (preferAfterExclusive != C.INDEX_UNSET && i > preferAfterExclusive) return i
                if (fallback == C.INDEX_UNSET) fallback = i
            }
        }
        return fallback
    }

    fun auxiliaryWindowBounds(targetIndex: Int, count: Int): Pair<Int, Int> {
        if (count <= MAX_AUXILIARY_TIMELINE_ITEMS) return 0 to count

        val halfWindow = MAX_AUXILIARY_TIMELINE_ITEMS / 2
        var start = (targetIndex - halfWindow).coerceAtLeast(0)
        var end = (start + MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtMost(count)
        start = (end - MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtLeast(0)
        return start to end
    }

    fun resetPreparedWindowState() {
        preparedWindowStartIndex = 0
        preparedPlayerUsesWindowedQueue = false
    }

    companion object {
        const val MAX_AUXILIARY_TIMELINE_ITEMS = 200
    }
}
