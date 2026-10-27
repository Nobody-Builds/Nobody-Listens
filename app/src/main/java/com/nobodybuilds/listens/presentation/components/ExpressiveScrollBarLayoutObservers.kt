package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.runtime.*

internal data class ScrollMetrics(
    val progress: Float,
    val totalItemsCount: Int,
    val maxScrollIndex: Int,
    val scrollableHeight: Float
)

internal data class VisibleGridLineMetrics(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int
)

internal fun estimateListFallbackStridePx(
    visibleItems: List<LazyListItemInfo>,
    spacingPx: Int
): Float {
    val strideSamples = visibleItems
        .zipWithNext()
        .mapNotNull { (current, next) ->
            (next.offset - current.offset)
                .takeIf { next.index == current.index + 1 && it > 0 }
                ?.toFloat()
        }

    return medianOrNull(strideSamples)
        ?: medianOrNull(visibleItems.map { it.size.toFloat() + spacingPx })
        ?: 1f
}

internal fun observeListLayoutMetrics(
    layoutInfo: LazyListLayoutInfo,
    tracker: AxisObservationTracker
) {
    tracker.resetIfNeeded(
        totalItemsCount = layoutInfo.totalItemsCount,
        spacingPx = layoutInfo.mainAxisItemSpacing
    )

    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return

    tracker.observeRepresentativeSample(
        strideSamplePx = estimateListFallbackStridePx(
            visibleItems = visibleItems,
            spacingPx = layoutInfo.mainAxisItemSpacing
        ),
        itemSizeSamplePx = medianOrNull(visibleItems.map { it.size.toFloat() })
    )

    visibleItems.forEach { item ->
        tracker.observeItemSize(index = item.index, sizePx = item.size.toFloat())
    }

    visibleItems
        .zipWithNext()
        .forEach { (current, next) ->
            if (next.index == current.index + 1) {
                tracker.observeStride(
                    index = current.index,
                    stridePx = (next.offset - current.offset).toFloat()
                )
            }
        }

    val lastVisibleItem = visibleItems.last()
    if (lastVisibleItem.index < layoutInfo.totalItemsCount - 1) {
        tracker.observeStride(
            index = lastVisibleItem.index,
            stridePx = (lastVisibleItem.size + layoutInfo.mainAxisItemSpacing).toFloat()
        )
    }
}

private fun buildVisibleGridLines(layoutInfo: LazyGridLayoutInfo): List<VisibleGridLineMetrics> {
    val isVertical =
        layoutInfo.orientation == androidx.compose.foundation.gestures.Orientation.Vertical
    val groupedLines = linkedMapOf<Int, MutableList<LazyGridItemInfo>>()

    layoutInfo.visibleItemsInfo.forEach { item ->
        val lineIndex = if (isVertical) item.row else item.column
        if (lineIndex >= 0) {
            groupedLines.getOrPut(lineIndex) { mutableListOf() }.add(item)
        }
    }

    return groupedLines
        .entries
        .map { (lineIndex, itemsInLine) ->
            VisibleGridLineMetrics(
                index = lineIndex,
                offsetPx = itemsInLine.minOf { if (isVertical) it.offset.y else it.offset.x },
                sizePx = itemsInLine.maxOf { if (isVertical) it.size.height else it.size.width }
            )
        }
        .sortedBy { it.index }
}

internal fun estimateGridFallbackStridePx(
    visibleLines: List<VisibleGridLineMetrics>,
    spacingPx: Int
): Float {
    val strideSamples = visibleLines
        .zipWithNext()
        .mapNotNull { (current, next) ->
            (next.offsetPx - current.offsetPx)
                .takeIf { next.index == current.index + 1 && it > 0 }
                ?.toFloat()
        }

    return medianOrNull(strideSamples)
        ?: medianOrNull(visibleLines.map { it.sizePx.toFloat() + spacingPx })
        ?: 1f
}

internal fun observeGridLayoutMetrics(
    layoutInfo: LazyGridLayoutInfo,
    tracker: AxisObservationTracker
): List<VisibleGridLineMetrics> {
    tracker.resetIfNeeded(
        totalItemsCount = layoutInfo.totalItemsCount,
        spacingPx = layoutInfo.mainAxisItemSpacing
    )

    val visibleLines = buildVisibleGridLines(layoutInfo)
    if (visibleLines.isEmpty()) return visibleLines

    tracker.observeRepresentativeSample(
        strideSamplePx = estimateGridFallbackStridePx(
            visibleLines = visibleLines,
            spacingPx = layoutInfo.mainAxisItemSpacing
        ),
        itemSizeSamplePx = medianOrNull(visibleLines.map { it.sizePx.toFloat() })
    )

    visibleLines.forEach { line ->
        tracker.observeItemSize(index = line.index, sizePx = line.sizePx.toFloat())
    }

    visibleLines
        .zipWithNext()
        .forEach { (current, next) ->
            if (next.index == current.index + 1) {
                tracker.observeStride(
                    index = current.index,
                    stridePx = (next.offsetPx - current.offsetPx).toFloat()
                )
            }
        }

    val totalLines = ((layoutInfo.totalItemsCount + layoutInfo.maxSpan - 1) / layoutInfo.maxSpan)
        .coerceAtLeast(1)
    val lastVisibleLine = visibleLines.last()
    if (lastVisibleLine.index < totalLines - 1) {
        tracker.observeStride(
            index = lastVisibleLine.index,
            stridePx = (lastVisibleLine.sizePx + layoutInfo.mainAxisItemSpacing).toFloat()
        )
    }

    return visibleLines
}
