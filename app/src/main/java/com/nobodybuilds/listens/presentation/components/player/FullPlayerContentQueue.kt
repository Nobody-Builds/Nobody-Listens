package com.nobodybuilds.listens.presentation.components.player

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.coerceAtLeast
import androidx.media3.common.Player
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.viewmodel.PlayerSheetState
import kotlinx.collections.immutable.ImmutableList

private const val PREVIOUS_TRACK_RESTART_THRESHOLD_MS = 10_000L

internal fun resolveQueueIndex(
    queue: ImmutableList<Song>,
    songId: String,
    currentMediaItemIndex: Int
): Int? {
    if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == songId) {
        return currentMediaItemIndex
    }
    return queue.indexOfFirst { it.id == songId }.takeIf { it >= 0 }
}

internal fun predictSkipNextCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex < queue.lastIndex -> safeCurrentIndex + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> null
    }
}

internal fun predictSkipPreviousCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    currentPositionMs: Long,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    if (currentPositionMs > PREVIOUS_TRACK_RESTART_THRESHOLD_MS) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex > 0 -> safeCurrentIndex - 1
        repeatMode == Player.REPEAT_MODE_ALL -> queue.lastIndex
        else -> null
    }
}

/** Swipe up anywhere on the expanded player (away from the system gesture edge) to open the queue. */
internal fun Modifier.fullPlayerQueueSwipeGesture(
    currentSheetState: PlayerSheetState,
    queueGestureBottomExclusionPx: Float,
    expansionFractionProvider: () -> Float,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit
): Modifier = pointerInput(currentSheetState, queueGestureBottomExclusionPx) {
    val queueDragActivationThresholdPx = 4.dp.toPx()
    val quickFlickVelocityThreshold = -520f

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Check condition AFTER the down event occurs
        val isFullyExpanded = currentSheetState == PlayerSheetState.EXPANDED && expansionFractionProvider() >= 0.99f

        if (!isFullyExpanded) {
            return@awaitEachGesture
        }

        val bottomGestureBoundaryY =
            (size.height.toFloat() - queueGestureBottomExclusionPx).coerceAtLeast(0f)
        if (down.position.y >= bottomGestureBoundaryY) {
            // Let the system Home/back gesture win near the bottom edge.
            return@awaitEachGesture
        }

        // Proceed with gesture logic
        var dragConsumedByQueue = false
        val velocityTracker = VelocityTracker()
        var totalDrag = 0f
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        drag(down.id) { change ->
            val dragAmount = change.positionChange().y
            totalDrag += dragAmount
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val isDraggingUp = totalDrag < -queueDragActivationThresholdPx

            if (isDraggingUp && !dragConsumedByQueue) {
                dragConsumedByQueue = true
                onQueueDragStart()
            }

            if (dragConsumedByQueue) {
                change.consume()
                onQueueDrag(dragAmount)
            }
        }

        val velocity = velocityTracker.calculateVelocity().y
        if (dragConsumedByQueue) {
            onQueueRelease(totalDrag, velocity)
        } else if (
            totalDrag < -(queueDragActivationThresholdPx * 2f) &&
            velocity < quickFlickVelocityThreshold
        ) {
            // Treat short/fast upward flick as queue-open intent.
            onQueueRelease(totalDrag, velocity)
        }
    }
}
