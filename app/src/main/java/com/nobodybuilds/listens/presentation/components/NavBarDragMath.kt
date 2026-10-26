package com.nobodybuilds.listens.presentation.components

import kotlin.math.abs
import kotlin.math.floor

/*
 * Pure hit-testing helpers for the draggable bottom navigation bar.
 *
 * All x values are physical (left-to-right) pixels inside the row's content area. The tabs are laid
 * out as `count` equal-width slots. In RTL the Row places index 0 on the right, so the visual slot of
 * an index is mirrored.
 */

/** Visual (left-to-right) slot of a logical tab index. */
internal fun navBarVisualSlot(index: Int, count: Int, isRtl: Boolean): Int =
    if (isRtl) count - 1 - index else index

/**
 * The tab index under [x], clamped to the first/last tab when the finger leaves the bar sideways.
 * Returns -1 when there is nothing to hit (no tabs or no width).
 */
internal fun navBarIndexAt(x: Float, width: Float, count: Int, isRtl: Boolean): Int {
    if (count <= 0 || !width.isFinite() || width <= 0f || x.isNaN()) return -1
    val slotWidth = width / count
    val visual = floor(x / slotWidth).toInt().coerceIn(0, count - 1)
    return navBarVisualSlot(visual, count, isRtl)
}

/** Centre x of the slot holding tab [index]. */
internal fun navBarSlotCenterX(index: Int, width: Float, count: Int, isRtl: Boolean): Float {
    if (count <= 0 || width <= 0f) return 0f
    val slotWidth = width / count
    val visual = navBarVisualSlot(index.coerceIn(0, count - 1), count, isRtl)
    return (visual + 0.5f) * slotWidth
}

/** Where the drag pill should sit for a finger at [x]: the finger, kept between the outer tab centres. */
internal fun navBarPillTrackX(x: Float, width: Float, count: Int): Float {
    if (count <= 0 || width <= 0f) return 0f
    val slotWidth = width / count
    val min = slotWidth / 2f
    val max = width - slotWidth / 2f
    return x.coerceIn(min, max)
}

/**
 * Which tab a drag on the bar ends on.
 *
 * - If the finger crossed into a different tab, that tab wins (the pill followed the finger there).
 * - Otherwise a quick horizontal fling (|velocityX| >= [flingThresholdPx]) steps one tab in the
 *   visual direction of the fling (rightwards = the tab to the right, mirrored for RTL), clamped.
 * - Otherwise the drag snaps back to [startIndex].
 *
 * [velocityX] is in physical px/s (positive = rightwards).
 */
internal fun resolveNavBarDragTarget(
    startIndex: Int,
    hoverIndex: Int,
    velocityX: Float,
    flingThresholdPx: Float,
    count: Int,
    isRtl: Boolean
): Int {
    if (count <= 0) return -1
    val start = startIndex.coerceIn(0, count - 1)
    if (hoverIndex in 0 until count && hoverIndex != start) return hoverIndex
    if (!velocityX.isFinite() || abs(velocityX) < flingThresholdPx) return start
    val visualStep = if (velocityX > 0f) 1 else -1
    val step = if (isRtl) -visualStep else visualStep
    return (start + step).coerceIn(0, count - 1)
}
