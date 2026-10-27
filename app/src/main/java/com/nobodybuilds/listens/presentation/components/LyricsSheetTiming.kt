package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.data.model.SyncedLine
import com.nobodybuilds.listens.data.model.SyncedWord
import com.nobodybuilds.listens.presentation.components.snapping.SnapperLayoutInfo
import com.nobodybuilds.listens.utils.LyricsUtils
import kotlin.math.abs
import kotlin.math.roundToInt

private val LeadingTagRegex = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE)

internal fun sanitizeLyricLineText(raw: String): String =
    LyricsUtils.stripLrcTimestamps(raw).replace(LeadingTagRegex, "").trimStart()

internal fun sanitizeSyncedWords(words: List<SyncedWord>): List<SyncedWord> =
    buildList {
        words.forEachIndexed { index, word ->
            val sanitized = if (index == 0) LeadingTagRegex.replace(word.word, "") else word.word
            val normalized = sanitized.trim()
            if (normalized.isEmpty()) return@forEachIndexed

            add(
                word.copy(
                    word = normalized,
                    startsNewWord = if (isEmpty()) true else word.startsNewWord
                )
            )
        }
    }

internal data class SyncedWordCluster(
    val startIndex: Int,
    val words: List<SyncedWord>
)

internal fun clusterSyncedWords(words: List<SyncedWord>): List<SyncedWordCluster> {
    if (words.isEmpty()) return emptyList()

    val clusters = mutableListOf<SyncedWordCluster>()
    var currentWords = mutableListOf<SyncedWord>()
    var currentStartIndex = 0

    words.forEachIndexed { index, word ->
        if (word.startsNewWord && currentWords.isNotEmpty()) {
            clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
            currentWords = mutableListOf()
            currentStartIndex = index
        } else if (currentWords.isEmpty()) {
            currentStartIndex = index
        }

        currentWords += word
    }

    if (currentWords.isNotEmpty()) {
        clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
    }

    return clusters
}

internal fun normalizeWordEndTime(
    currentWordTimeMs: Long,
    nextWordTimeMs: Long,
    lineEndTimeMs: Long
): Long {
    val minEnd = currentWordTimeMs + 1L
    val boundedLineEnd = lineEndTimeMs.coerceAtLeast(minEnd)
    return nextWordTimeMs.coerceIn(minEnd, boundedLineEnd)
}

internal fun resolveLineEndTimeMs(line: SyncedLine, nextLineStartMs: Int): Long {
    val baseEnd = nextLineStartMs.toLong()
    val lastWordStart = line.words?.maxOfOrNull { it.time.toLong() } ?: line.time.toLong()
    return maxOf(baseEnd, lastWordStart + 1L)
}

internal fun resolveHighlightedWordIndex(
    words: List<SyncedWord>,
    positionMs: Long,
    lineStartTimeMs: Long,
    lineEndTimeMs: Long
): Int {
    if (positionMs < lineStartTimeMs || positionMs >= lineEndTimeMs) return -1
    return words.indexOfLast { it.time.toLong() <= positionMs }
}

internal fun resolveSeekPositionMs(
    lineTimeMs: Long,
    lyricsSyncOffsetMs: Int
): Long = (lineTimeMs - lyricsSyncOffsetMs.toLong()).coerceAtLeast(0L)

internal data class HighlightZoneMetrics(
    val topPadding: Dp,
    val bottomPadding: Dp,
    val zoneHeight: Dp,
    val centerFromTop: Dp
)

internal fun calculateHighlightMetrics(
    containerHeight: Dp,
    highlightZoneFraction: Float,
    highlightOffset: Dp
): HighlightZoneMetrics {
    val container = containerHeight.value
    val zoneHeight = (containerHeight * highlightZoneFraction).value.coerceAtLeast(0f)
    val offset = highlightOffset.value
    val minCenter = zoneHeight / 2f
    val maxCenter = (container - zoneHeight / 2f).coerceAtLeast(minCenter)
    val unclampedCenter = container / 2f - offset
    val center = unclampedCenter.coerceIn(minCenter, maxCenter)
    val topPadding = (center - zoneHeight / 2f).coerceAtLeast(0f)
    val bottomPadding = (container - center - zoneHeight / 2f).coerceAtLeast(0f)

    return HighlightZoneMetrics(
        topPadding = topPadding.dp,
        bottomPadding = bottomPadding.dp,
        zoneHeight = zoneHeight.dp,
        centerFromTop = center.dp
    )
}

internal fun highlightSnapOffsetPx(
    viewportHeight: Int,
    itemSize: Int,
    highlightOffsetPx: Float
): Int {
    if (viewportHeight <= 0 || itemSize <= 0) return 0
    if (itemSize >= viewportHeight) return 0
    val viewport = viewportHeight.toFloat()
    val halfItem = itemSize / 2f
    val targetCenter = (viewport / 2f) - highlightOffsetPx
    val clampedCenter = targetCenter.coerceIn(halfItem, viewport - halfItem)
    return (clampedCenter - halfItem).roundToInt()
}

internal suspend fun animateToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int,
    animationSpec: AnimationSpec<Float>
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        var previous = 0f
        AnimationState(initialValue = 0f).animateTo(
            targetValue = distance.toFloat(),
            animationSpec = animationSpec
        ) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}

internal suspend fun snapToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        scrollBy(distance.toFloat())
    }
}

internal fun resolveCurrentLineIndex(
    lines: List<SyncedLine>,
    position: Long
): Int {
    if (lines.isEmpty()) return -1

    return lines.withIndex().lastOrNull { (index, line) ->
        val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
        val lineEndTime = resolveLineEndTimeMs(line, nextTime)
        position in line.time.toLong()..<lineEndTime
    }?.index ?: -1
}
