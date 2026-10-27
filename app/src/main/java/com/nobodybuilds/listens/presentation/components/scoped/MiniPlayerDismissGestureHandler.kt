package com.nobodybuilds.listens.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign

/**
 * Mini player swipe gestures (collapsed state only):
 * - horizontal: left = next song, right = previous song. The content follows the finger,
 *   commits past ~30% of the width (or on a fling), slides out and the new song slides in.
 * - down: dismiss (stops playback and hides the mini player, with undo).
 * - up: delegated to the sheet's vertical drag handler (expands the player).
 */
internal class MiniPlayerSwipeGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val haptics: () -> NobodyListensHaptics,
    /** Horizontal content offset (skip gesture). */
    val offsetX: Animatable<Float, AnimationVector1D>,
    /** Vertical card offset (dismiss gesture). */
    val offsetY: Animatable<Float, AnimationVector1D>,
    private val widthPxProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val currentSongIdProvider: () -> String?,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var accumulatedX = 0f
    private var accumulatedY = 0f
    private var thresholdCrossed = false
    private var job: Job? = null

    val skipThresholdPx: Float get() = widthPxProvider() * SKIP_THRESHOLD_FRACTION
    val dismissThresholdPx: Float get() = miniHeightPxProvider() * DISMISS_THRESHOLD_FRACTION

    private fun launchAnim(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)
    }

    // ------------------------------------------------------------ horizontal (skip)

    fun onHorizontalStart(initial: Float) {
        accumulatedX = offsetX.value + initial
        thresholdCrossed = abs(accumulatedX) >= skipThresholdPx
        launchAnim { offsetX.snapTo(accumulatedX) }
    }

    fun onHorizontalDrag(dx: Float) {
        accumulatedX += dx
        val crossed = abs(accumulatedX) >= skipThresholdPx
        if (crossed && !thresholdCrossed) haptics().swipeThreshold()
        thresholdCrossed = crossed
        val value = accumulatedX
        launchAnim { offsetX.snapTo(value) }
    }

    fun onHorizontalEnd(velocityX: Float) {
        val width = widthPxProvider().coerceAtLeast(1f)
        val flingVelocity = FLING_VELOCITY_DP * density.density
        val minFlingDistance = MIN_FLING_DISTANCE_DP * density.density
        val isFling = abs(velocityX) > flingVelocity &&
            sign(velocityX) == sign(accumulatedX) &&
            abs(accumulatedX) > minFlingDistance
        val commit = thresholdCrossed || isFling
        val direction = sign(accumulatedX)
        thresholdCrossed = false
        if (!commit || direction == 0f) {
            launchAnim {
                offsetX.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    initialVelocity = velocityX
                )
            }
            return
        }
        val songIdBefore = currentSongIdProvider()
        launchAnim {
            offsetX.animateTo(
                direction * width,
                tween(durationMillis = 140, easing = FastOutLinearInEasing)
            )
            haptics().skip()
            if (com.nobodybuilds.listens.BuildConfig.SMOKE_TEST) {
                android.util.Log.w("NobodyListensSwipe", "mini player swipe dir=$direction")
            }
            if (direction < 0f) onNext() else onPrevious()
            // Wait briefly for the new song so it slides in (previous may just restart the song).
            withTimeoutOrNull(SONG_CHANGE_TIMEOUT_MS) {
                snapshotFlow { currentSongIdProvider() }.first { it != songIdBefore }
            }
            offsetX.snapTo(-direction * width * 0.45f)
            offsetX.animateTo(
                0f,
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    // ------------------------------------------------------------ vertical down (dismiss)

    fun onDismissStart(initial: Float) {
        accumulatedY = (offsetY.value + initial).coerceAtLeast(0f)
        thresholdCrossed = accumulatedY >= dismissThresholdPx
        val value = accumulatedY
        launchAnim { offsetY.snapTo(value) }
    }

    fun onDismissDrag(dy: Float) {
        accumulatedY = (accumulatedY + dy).coerceAtLeast(0f)
        val crossed = accumulatedY >= dismissThresholdPx
        if (crossed && !thresholdCrossed) haptics().swipeThreshold()
        thresholdCrossed = crossed
        val value = accumulatedY
        launchAnim { offsetY.snapTo(value) }
    }

    fun onDismissEnd(velocityY: Float) {
        val isFling = velocityY > FLING_VELOCITY_DP * density.density &&
            accumulatedY > MIN_FLING_DISTANCE_DP * density.density * 0.5f
        val commit = thresholdCrossed || isFling
        thresholdCrossed = false
        if (!commit) {
            launchAnim {
                offsetY.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
            return
        }
        launchAnim {
            offsetY.animateTo(
                miniHeightPxProvider() * 1.6f,
                tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            onDismiss()
            offsetY.snapTo(0f)
            offsetX.snapTo(0f)
        }
        accumulatedY = 0f
    }

    companion object {
        const val SKIP_THRESHOLD_FRACTION = 0.3f
        const val DISMISS_THRESHOLD_FRACTION = 0.6f
        private const val FLING_VELOCITY_DP = 700f
        private const val MIN_FLING_DISTANCE_DP = 24f
        private const val SONG_CHANGE_TIMEOUT_MS = 350L
    }
}

@Composable
internal fun rememberMiniPlayerSwipeGestureHandler(
    scope: CoroutineScope,
    density: Density,
    haptics: NobodyListensHaptics,
    offsetX: Animatable<Float, AnimationVector1D>,
    offsetY: Animatable<Float, AnimationVector1D>,
    widthPx: Float,
    miniHeightPx: Float,
    currentSongId: String?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
): MiniPlayerSwipeGestureHandler {
    val hapticsState = rememberUpdatedState(haptics)
    val widthState = rememberUpdatedState(widthPx)
    val heightState = rememberUpdatedState(miniHeightPx)
    val songIdState = rememberUpdatedState(currentSongId)
    val onNextState = rememberUpdatedState(onNext)
    val onPreviousState = rememberUpdatedState(onPrevious)
    val onDismissState = rememberUpdatedState(onDismiss)
    return remember(scope, density, offsetX, offsetY) {
        MiniPlayerSwipeGestureHandler(
            scope = scope,
            density = density,
            haptics = { hapticsState.value },
            offsetX = offsetX,
            offsetY = offsetY,
            widthPxProvider = { widthState.value },
            miniHeightPxProvider = { heightState.value },
            currentSongIdProvider = { songIdState.value },
            onNext = { onNextState.value() },
            onPrevious = { onPreviousState.value() },
            onDismiss = { onDismissState.value() },
        )
    }
}

/**
 * Collapsed mini player gestures. Swipes up are forwarded to [verticalHandler] so the
 * player still expands by dragging; [canDismissProvider] guards the swipe-down dismiss
 * (e.g. false while the sheet is still animating).
 */
internal fun Modifier.miniPlayerSwipeGestures(
    enabled: Boolean,
    handler: MiniPlayerSwipeGestureHandler,
    verticalHandler: SheetVerticalDragGestureHandler?,
    canDismissProvider: () -> Boolean,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler, verticalHandler) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var overSlop = androidx.compose.ui.geometry.Offset.Zero
            val slopChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = over
            } ?: return@awaitEachGesture

            val total = slopChange.position - down.position
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            tracker.addPosition(slopChange.uptimeMillis, slopChange.position)

            when {
                abs(total.x) > abs(total.y) -> {
                    handler.onHorizontalStart(overSlop.x)
                    drag(slopChange.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        handler.onHorizontalDrag(change.positionChange().x)
                        change.consume()
                    }
                    handler.onHorizontalEnd(tracker.calculateVelocity().x)
                }

                total.y > 0f && canDismissProvider() -> {
                    handler.onDismissStart(overSlop.y)
                    drag(slopChange.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        handler.onDismissDrag(change.positionChange().y)
                        change.consume()
                    }
                    handler.onDismissEnd(tracker.calculateVelocity().y)
                }

                verticalHandler != null -> {
                    verticalHandler.onDragStart()
                    verticalHandler.onVerticalDrag(
                        uptimeMillis = slopChange.uptimeMillis,
                        position = slopChange.position,
                        dragAmount = overSlop.y
                    )
                    val completed = drag(slopChange.id) { change ->
                        verticalHandler.onVerticalDrag(
                            uptimeMillis = change.uptimeMillis,
                            position = change.position,
                            dragAmount = change.positionChange().y
                        )
                        change.consume()
                    }
                    if (completed) verticalHandler.onDragEnd() else verticalHandler.onDragCancel()
                }
            }
        }
    }
}
