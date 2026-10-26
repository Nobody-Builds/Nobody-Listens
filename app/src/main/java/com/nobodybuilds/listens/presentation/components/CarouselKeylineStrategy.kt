package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.pager.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.fastMapIndexed
import kotlin.math.*

/* ================================================================================================
   STRATEGY (keylines shifting + snapping) — versión compacta y compatible
   ================================================================================================ */

internal class Strategy(
    val defaultKeylines: KeylineList,
    val startKeylineSteps: List<KeylineList>,
    val endKeylineSteps: List<KeylineList>,
    val availableSpace: Float,
    val itemSpacing: Float,
    val beforeContentPadding: Float,
    val afterContentPadding: Float,
) {
    constructor(
        defaultKeylines: KeylineList,
        availableSpace: Float,
        itemSpacing: Float,
        beforeContentPadding: Float,
        afterContentPadding: Float,
    ) : this(
        defaultKeylines = defaultKeylines,
        startKeylineSteps = getStartKeylineSteps(defaultKeylines, availableSpace, itemSpacing, beforeContentPadding),
        endKeylineSteps = getEndKeylineSteps(defaultKeylines, availableSpace, itemSpacing, afterContentPadding),
        availableSpace = availableSpace,
        itemSpacing = itemSpacing,
        beforeContentPadding = beforeContentPadding,
        afterContentPadding = afterContentPadding
    )

    val itemMainAxisSize: Float get() = defaultKeylines.firstFocal.size
    val isValid: Boolean =
        defaultKeylines.isNotEmpty() && availableSpace != 0f && itemMainAxisSize != 0f

    private val startShiftDistance = getStartShiftDistance(startKeylineSteps, beforeContentPadding)
    private val endShiftDistance = getEndShiftDistance(endKeylineSteps, afterContentPadding)
    private val startShiftPoints = getStepInterpolationPoints(startShiftDistance, startKeylineSteps, true)
    private val endShiftPoints = getStepInterpolationPoints(endShiftDistance, endKeylineSteps, false)
    private var lastStartAndEndKeylineListSteps: List<KeylineList>? = null

    fun getKeylineListForScrollOffset(
        scrollOffset: Float,
        maxScrollOffset: Float,
        roundToNearestStep: Boolean = false,
    ): KeylineList {
        val positiveScrollOffset = max(0f, scrollOffset)
        val startShiftOffset = startShiftDistance
        val endShiftOffset = max(0f, maxScrollOffset - endShiftDistance)

        if (positiveScrollOffset in startShiftOffset..endShiftOffset) return defaultKeylines

        var interpolation = lerp(1f, 0f, 0f, startShiftOffset, positiveScrollOffset)
        var shiftPoints = startShiftPoints
        var steps = startKeylineSteps

        if (positiveScrollOffset > endShiftOffset) {
            interpolation = lerp(0f, 1f, endShiftOffset, maxScrollOffset, positiveScrollOffset)
            shiftPoints = endShiftPoints
            steps = endKeylineSteps
            if (endShiftOffset < 0.01f && startKeylineSteps.size == 2 && endKeylineSteps.size == 2) {
                if (lastStartAndEndKeylineListSteps == null) {
                    lastStartAndEndKeylineListSteps = listOf(startKeylineSteps.last(), endKeylineSteps.last())
                }
                steps = lastStartAndEndKeylineListSteps!!
            }
        }

        val range = getShiftPointRange(steps.size, shiftPoints, interpolation)
        if (roundToNearestStep) {
            val rounded = if (range.steppedInterpolation.roundToInt() == 0) range.fromStepIndex else range.toStepIndex
            return steps[rounded]
        }
        return lerp(steps[range.fromStepIndex], steps[range.toStepIndex], range.steppedInterpolation)
    }

    companion object {
        val Empty = Strategy(
            defaultKeylines = KeylineList.Empty,
            startKeylineSteps = emptyList(),
            endKeylineSteps = emptyList(),
            availableSpace = 0f,
            itemSpacing = 0f,
            beforeContentPadding = 0f,
            afterContentPadding = 0f
        )
    }
}

/* ================================================================================================
   KEYLINES + helpers
   ================================================================================================ */

internal data class Keyline(
    val size: Float,
    val offset: Float,
    val unadjustedOffset: Float,
    val isFocal: Boolean,
    val isAnchor: Boolean,
    val isPivot: Boolean,
    val cutoff: Float,
)

internal class KeylineList internal constructor(keylines: List<Keyline>) : List<Keyline> by keylines {
    val pivotIndex: Int = indexOfFirst { it.isPivot }
    val pivot: Keyline get() = get(pivotIndex)

    val firstNonAnchorIndex: Int = indexOfFirst { !it.isAnchor }
    val firstNonAnchor: Keyline get() = get(firstNonAnchorIndex)

    val lastNonAnchorIndex: Int = indexOfLast { !it.isAnchor }
    val lastNonAnchor: Keyline get() = get(lastNonAnchorIndex)

    val firstFocalIndex: Int = indexOfFirst { it.isFocal }
    val firstFocal: Keyline get() = get(firstFocalIndex)
    val lastFocalIndex: Int = indexOfLast { it.isFocal }
    val lastFocal: Keyline get() = get(lastFocalIndex)
    val focalCount: Int = lastFocalIndex - firstFocalIndex + 1

    fun isFirstFocalItemAtStartOfContainer(): Boolean {
        val firstFocalLeft = firstFocal.offset - (firstFocal.size / 2)
        return firstFocalLeft >= 0 && firstFocal == firstNonAnchor
    }

    fun isLastFocalItemAtEndOfContainer(carouselMainAxisSize: Float): Boolean {
        val lastFocalRight = lastFocal.offset + (lastFocal.size / 2)
        return lastFocalRight <= carouselMainAxisSize && lastFocal == lastNonAnchor
    }

    fun firstIndexAfterFocalRangeWithSize(size: Float): Int {
        val from = lastFocalIndex
        val to = lastIndex
        return (from..to).firstOrNull { i -> this[i].size == size } ?: lastIndex
    }

    fun lastIndexBeforeFocalRangeWithSize(size: Float): Int {
        val from = firstFocalIndex - 1
        val to = 0
        return (from downTo to).firstOrNull { i -> this[i].size == size } ?: to
    }

    fun getKeylineBefore(unadjustedOffset: Float): Keyline {
        for (i in indices.reversed()) {
            val k = get(i)
            if (k.unadjustedOffset < unadjustedOffset) return k
        }
        return first()
    }

    fun getKeylineAfter(unadjustedOffset: Float): Keyline {
        return firstOrNull { it.unadjustedOffset >= unadjustedOffset } ?: last()
    }

    companion object { val Empty = KeylineList(emptyList()) }
}

private fun emptyKeylineList() = KeylineList.Empty

@JvmInline
internal value class CarouselAlignment private constructor(val value: Int) {
    companion object { val Start = CarouselAlignment(-1); val Center = CarouselAlignment(0); val End = CarouselAlignment(1) }
}

// Overload A: alineación (Start/Center/End)
internal fun keylineListOf(
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    carouselAlignment: CarouselAlignment,
    keylines: KeylineListScope.() -> Unit,
): KeylineList {
    val scope = KeylineListScopeImpl()
    keylines(scope)
    return scope.createWithAlignment(
        carouselMainAxisSize = carouselMainAxisSize,
        itemSpacing = itemSpacing,
        carouselAlignment = carouselAlignment
    )
}

// Overload B: pivote explícito (índice y offset)
internal fun keylineListOf(
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    pivotIndex: Int,
    pivotOffset: Float,
    keylines: KeylineListScope.() -> Unit,
): KeylineList {
    val scope = KeylineListScopeImpl()
    keylines(scope)
    return scope.createWithPivot(
        carouselMainAxisSize = carouselMainAxisSize,
        itemSpacing = itemSpacing,
        pivotIndex = pivotIndex,
        pivotOffset = pivotOffset
    )
}

/* Interpolaciones */
internal fun lerp(start: Keyline, end: Keyline, fraction: Float) = Keyline(
    size = androidx.compose.ui.util.lerp(start.size, end.size, fraction),
    offset = androidx.compose.ui.util.lerp(start.offset, end.offset, fraction),
    unadjustedOffset = androidx.compose.ui.util.lerp(start.unadjustedOffset, end.unadjustedOffset, fraction),
    isFocal = if (fraction < .5f) start.isFocal else end.isFocal,
    isAnchor = if (fraction < .5f) start.isAnchor else end.isAnchor,
    isPivot = if (fraction < .5f) start.isPivot else end.isPivot,
    cutoff = androidx.compose.ui.util.lerp(start.cutoff, end.cutoff, fraction),
)

internal fun lerp(from: KeylineList, to: KeylineList, fraction: Float): KeylineList {
    val list = from.fastMapIndexed { i, k -> lerp(k, to[i], fraction) }
    return KeylineList(list)
}

/* ================================================================================================
   MULTI-BROWSE keyline list + Arrangement (versión compacta)
   ================================================================================================ */

/** Arreglo de tamaños para small/medium/large y sus cantidades. */
private data class Arrangement(
    val smallCount: Int,
    val smallSize: Float,
    val mediumCount: Int,
    val mediumSize: Float,
    val largeCount: Int,
    val largeSize: Float,
) {
    fun itemCount() = smallCount + mediumCount + largeCount

    companion object {
        /** Búsqueda simple: prueba combinaciones y escoge la que mejor llena el espacio con menor “coste”. */
        fun findLowestCostArrangement(
            availableSpace: Float,
            itemSpacing: Float,
            targetSmallSize: Float,
            minSmallSize: Float,
            maxSmallSize: Float,
            smallCounts: IntArray,
            targetMediumSize: Float,
            mediumCounts: IntArray,
            targetLargeSize: Float,
            largeCounts: IntArray,
        ): Arrangement? {
            var best: Arrangement? = null
            var bestCost = Float.MAX_VALUE

            fun cost(sz: Float, target: Float) = abs(sz - target) / (target.takeIf { it > 0f } ?: 1f)

            for (lc in largeCounts) {
                for (mc in mediumCounts) {
                    for (sc in smallCounts) {
                        // fijar tamaños (small acotado, medium entre small y large, large <= target)
                        val large = min(targetLargeSize, availableSpace)
                        val small = targetSmallSize.coerceIn(minSmallSize, maxSmallSize)
                        val medium = if (targetMediumSize > 0f) {
                            targetMediumSize.coerceIn(small, large)
                        } else (large + small) / 2f

                        val items = lc + mc + sc
                        if (items == 0) continue
                        val totalSpacing = itemSpacing * (items - 1)
                        val total =
                            (lc * large) + (mc * medium) + (sc * small) + totalSpacing

                        // debe caber (o quedar muy cerca). Permitimos leve sobre/under-fill y penalizamos.
                        val over = max(0f, total - availableSpace)
                        val under = max(0f, availableSpace - total)

                        // coste por desviación de targets + espacio mal usado
                        val c =
                            cost(large, targetLargeSize) * lc +
                                    cost(medium, (large + small) / 2f) * mc +
                                    cost(small, targetSmallSize) * sc +
                                    (over * 3f + under) / (availableSpace + 1f)

                        if (c < bestCost) {
                            bestCost = c
                            best = Arrangement(sc, small, mc, medium, lc, large)
                        }
                    }
                }
            }
            return best
        }
    }
}

internal fun multiBrowseKeylineList(
    density: Density,
    carouselMainAxisSize: Float,
    preferredItemSize: Float,
    itemSpacing: Float,
    itemCount: Int,
    minSmallItemSize: Float = with(density) { 40.dp.toPx() },
    maxSmallItemSize: Float = with(density) { 56.dp.toPx() },
    largeCounts: IntArray? = null,
    mediumCounts: IntArray = intArrayOf(1, 0),
    smallCounts: IntArray = intArrayOf(1),
    alignment: CarouselAlignment = CarouselAlignment.Start
): KeylineList {
    if (carouselMainAxisSize == 0f || preferredItemSize == 0f) return emptyKeylineList()

    var resolvedSmallCounts = smallCounts
    val targetLargeSize = min(preferredItemSize, carouselMainAxisSize)
    val targetSmallSize = (targetLargeSize / 3f).coerceIn(minSmallItemSize, maxSmallItemSize)
    val targetMediumSize = (targetLargeSize + targetSmallSize) / 2f

    if (carouselMainAxisSize < minSmallItemSize * 2) resolvedSmallCounts = intArrayOf(0)

    val resolvedLargeCounts = largeCounts ?: run {
        val minAvailableLargeSpace =
            carouselMainAxisSize - targetMediumSize * mediumCounts.max() - maxSmallItemSize * resolvedSmallCounts.max()
        val minLargeCount = max(1, floor(minAvailableLargeSpace / targetLargeSize).toInt())
        val maxLargeCount = ceil(carouselMainAxisSize / targetLargeSize).toInt()
        IntArray(maxLargeCount - minLargeCount + 1) { maxLargeCount - it }
    }
    val anchorSize = with(density) { 10.dp.toPx() }

    var arrangement =
        Arrangement.findLowestCostArrangement(
            availableSpace = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            targetSmallSize = targetSmallSize,
            minSmallSize = minSmallItemSize,
            maxSmallSize = maxSmallItemSize,
            smallCounts = resolvedSmallCounts,
            targetMediumSize = targetMediumSize,
            mediumCounts = mediumCounts,
            targetLargeSize = targetLargeSize,
            largeCounts = resolvedLargeCounts,
        ) ?: return emptyKeylineList()

    if (arrangement.itemCount() > itemCount) {
        var surplus = arrangement.itemCount() - itemCount
        var sc = arrangement.smallCount
        var mc = arrangement.mediumCount
        while (surplus > 0) {
            if (sc > 0) sc -= 1 else if (mc > 1) mc -= 1
            surplus -= 1
        }
        arrangement =
            Arrangement.findLowestCostArrangement(
                availableSpace = carouselMainAxisSize,
                itemSpacing = itemSpacing,
                targetSmallSize = targetSmallSize,
                minSmallSize = minSmallItemSize,
                maxSmallSize = maxSmallItemSize,
                smallCounts = intArrayOf(sc),
                targetMediumSize = targetMediumSize,
                mediumCounts = intArrayOf(mc),
                targetLargeSize = targetLargeSize,
                largeCounts = resolvedLargeCounts,
            ) ?: arrangement
    }

    return createKeylineListFromArrangement(
        carouselMainAxisSize = carouselMainAxisSize,
        itemSpacing = itemSpacing,
        leftAnchorSize = anchorSize,
        rightAnchorSize = anchorSize,
        arrangement = arrangement,
        alignment = alignment
    )
}

private fun createKeylineListFromArrangement(
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    leftAnchorSize: Float,
    rightAnchorSize: Float,
    arrangement: Arrangement,
    alignment: CarouselAlignment
): KeylineList {
    return keylineListOf(carouselMainAxisSize, itemSpacing, alignment) {
        add(leftAnchorSize, isAnchor = true)
        repeat(arrangement.largeCount) { add(arrangement.largeSize) }
        repeat(arrangement.mediumCount) { add(arrangement.mediumSize) }
        repeat(arrangement.smallCount) { add(arrangement.smallSize) }
        add(rightAnchorSize, isAnchor = true)
    }
}

/* ================================================================================================
   SNAP + cálculos de scroll
   ================================================================================================ */

private fun getSnapPositionOffset(strategy: Strategy, itemIndex: Int, itemCount: Int): Int {
    if (!strategy.isValid) return 0
    var offset =
        (strategy.defaultKeylines.firstFocal.unadjustedOffset - strategy.itemMainAxisSize / 2f).roundToInt()

    if (itemIndex <= strategy.startKeylineSteps.lastIndex) {
        val stepIndex = (strategy.startKeylineSteps.lastIndex - itemIndex).coerceIn(0, strategy.startKeylineSteps.lastIndex)
        val startKeylines = strategy.startKeylineSteps[stepIndex]
        offset = (startKeylines.firstFocal.unadjustedOffset - strategy.itemMainAxisSize / 2f).roundToInt()
    }

    val lastItemIndex = itemCount - 1
    if (itemIndex >= lastItemIndex - strategy.endKeylineSteps.lastIndex &&
        itemCount > strategy.defaultKeylines.focalCount
    ) {
        val stepIndex =
            (strategy.endKeylineSteps.lastIndex - (lastItemIndex - itemIndex)).coerceIn(0, strategy.endKeylineSteps.lastIndex)
        val endKeylines = strategy.endKeylineSteps[stepIndex]
        offset = (endKeylines.firstFocal.unadjustedOffset - strategy.itemMainAxisSize / 2f).roundToInt()
    }
    return offset
}

internal fun KeylineSnapPosition(pageSize: CarouselPageSize): SnapPosition =
    object : SnapPosition {
        override fun position(
            layoutSize: Int,
            itemSize: Int,
            beforeContentPadding: Int,
            afterContentPadding: Int,
            itemIndex: Int,
            itemCount: Int,
        ): Int = getSnapPositionOffset(pageSize.strategy, itemIndex, itemCount)
    }

@OptIn(ExperimentalMaterial3Api::class)
internal fun calculateCurrentScrollOffset(state: CarouselState, strategy: Strategy): Float {
    val w = strategy.itemMainAxisSize + strategy.itemSpacing
    val curr = (state.pagerState.currentPage * w) + (state.pagerState.currentPageOffsetFraction * w)
    return curr - getSnapPositionOffset(strategy, state.pagerState.currentPage, state.pagerState.pageCount)
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun calculateMaxScrollOffset(state: CarouselState, strategy: Strategy): Float {
    val itemCount = state.pagerState.pageCount.toFloat()
    val maxScroll = (strategy.itemMainAxisSize * itemCount) + (strategy.itemSpacing * (itemCount - 1))
    return (maxScroll - strategy.availableSpace).coerceAtLeast(0f)
}

internal fun getProgress(before: Keyline, after: Keyline, unadjustedOffset: Float): Float {
    if (before == after) return 1f
    val total = after.unadjustedOffset - before.unadjustedOffset
    return (unadjustedOffset - before.unadjustedOffset) / total
}
