package com.nobodybuilds.listens.presentation.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.data.model.SyncedLine
import com.nobodybuilds.listens.utils.BubblesLine
import com.nobodybuilds.listens.presentation.components.snapping.ExperimentalSnapperApi
import com.nobodybuilds.listens.presentation.components.snapping.rememberLazyListSnapperLayoutInfo
import com.nobodybuilds.listens.presentation.components.snapping.rememberSnapperFlingBehavior
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

@OptIn(ExperimentalSnapperApi::class)
@Composable
fun SyncedLyricsList(
    lines: List<SyncedLine>,
    listState: LazyListState,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long? = null,
    accentColor: Color,
    textStyle: TextStyle,
    onLineClick: (SyncedLine) -> Unit,
    highlightZoneFraction: Float,
    highlightOffsetDp: Dp,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    footer: LazyListScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()
    val position = remember(playbackPosition, lyricsSyncOffset, positionOverrideMs) {
        positionOverrideMs ?: (playbackPosition + lyricsSyncOffset).coerceAtLeast(0L)
    }
    val isPreviewSeeking = positionOverrideMs != null
    val currentLineIndex by remember(position, lines) {
        derivedStateOf {
            resolveCurrentLineIndex(lines = lines, position = position)
        }
    }
    var hasAlignedInitialLine by remember(lines) { mutableStateOf(false) }
    var lastAutoScrolledLineIndex by remember(lines) { mutableIntStateOf(-1) }

    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(maxHeight, highlightZoneFraction, highlightOffsetDp) {
            calculateHighlightMetrics(maxHeight, highlightZoneFraction, highlightOffsetDp)
        }
        val highlightOffsetPx = remember(highlightOffsetDp, density) { with(density) { highlightOffsetDp.toPx() } }

        val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(
            lazyListState = listState,
            snapOffsetForItem = { layoutInfo, item ->
                val viewportHeight = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
                highlightSnapOffsetPx(viewportHeight, item.size, highlightOffsetPx)
            }
        )
        val flingBehavior = rememberSnapperFlingBehavior(layoutInfo = snapperLayoutInfo)

        LaunchedEffect(currentLineIndex, lines.size, metrics, isPreviewSeeking) {
            if (lines.isEmpty()) return@LaunchedEffect
            if (currentLineIndex !in lines.indices) return@LaunchedEffect
            if (listState.layoutInfo.totalItemsCount == 0) return@LaunchedEffect

            if (!hasAlignedInitialLine) {
                listState.scrollToItem(currentLineIndex)
                snapToSnapIndex(
                    listState = listState,
                    layoutInfo = snapperLayoutInfo,
                    targetIndex = currentLineIndex
                )
                hasAlignedInitialLine = true
                lastAutoScrolledLineIndex = currentLineIndex
                return@LaunchedEffect
            }

            if (listState.isScrollInProgress && !isPreviewSeeking) return@LaunchedEffect

            val lineJumpDistance = if (lastAutoScrolledLineIndex >= 0) {
                abs(currentLineIndex - lastAutoScrolledLineIndex)
            } else {
                0
            }

            if (isPreviewSeeking) {
                if (lineJumpDistance > 2) {
                    listState.scrollToItem(currentLineIndex)
                    snapToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = currentLineIndex
                    )
                } else {
                    animateToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = currentLineIndex,
                        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                    )
                }
                lastAutoScrolledLineIndex = currentLineIndex
                return@LaunchedEffect
            }

            // Music Style Dynamic Velocity
            val dynamicAnimationSpec = if (useAnimatedLyrics) {
                val currentLineTime = lines.getOrNull(currentLineIndex)?.time ?: 0
                val nextLineTime = lines.getOrNull(currentLineIndex + 1)?.time ?: (currentLineTime + 1000)
                val timeDiff = (nextLineTime - currentLineTime).coerceIn(250, 2000) // Bound the duration
                
                tween<Float>(
                    durationMillis = timeDiff,
                    easing = FastOutSlowInEasing
                )
            } else {
                autoscrollAnimationSpec
            }

            animateToSnapIndex(
                listState = listState,
                layoutInfo = snapperLayoutInfo,
                targetIndex = currentLineIndex,
                animationSpec = dynamicAnimationSpec
            )
            lastAutoScrolledLineIndex = currentLineIndex
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = contentPadding
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, item -> "${item.time}_$index" }
                ) { index, line ->
                    val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
                    val distanceFromCurrent = if (currentLineIndex != -1) abs(currentLineIndex - index) else 100
                    
                    val parallaxModifier = if (useAnimatedLyrics) {
                        Modifier.graphicsLayer {
                            // Calculate translation dynamically inside graphicsLayer to avoid recomposing the row during scroll
                            val currentLayoutInfo = listState.layoutInfo
                            val lineItemInfo = currentLayoutInfo.visibleItemsInfo.find { it.index == index }
                            val itemCenter = lineItemInfo?.let { it.offset + (it.size / 2f) }
                            val viewportCenter = currentLayoutInfo.viewportEndOffset / 2f
                            
                            val distanceFromCenter = itemCenter?.let { it - viewportCenter } ?: 0f
                            
                            val maxTranslation = 40f 
                            val distanceRatio = (distanceFromCenter / viewportCenter).coerceIn(-1f, 1f)
                            translationY = distanceRatio * distanceRatio * distanceRatio * maxTranslation 
                        }
                    } else Modifier

                    if (line.line.isNotBlank()) {
                        LyricLineRow(
                            line = line,
                            nextTime = nextTime,
                            position = position,
                            distanceFromCurrent = distanceFromCurrent,
                            useAnimatedLyrics = useAnimatedLyrics,
                            animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                            animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                            immersiveMode = immersiveMode,
                            lyricsAlignment = lyricsAlignment,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            accentColor = accentColor,
                            style = textStyle,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .testTag("synced_line_${line.time}"),
                            onClick = { onLineClick(line) }
                        )
                    } else {
                        BubblesLine(
                            positionFlow = playbackPositionFlow,
                            time = line.time,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                            nextTime = nextTime,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
// 16 dp Spacer removed to allow dynamic padding in LyricLineRow
                }
                footer()
            }

//            if (metrics.zoneHeight > 0.dp) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .offset(y = metrics.topPadding)
//                        .height(metrics.zoneHeight)
//                        .align(Alignment.TopCenter)
//                        .clip(RoundedCornerShape(18.dp))
//                        .background(accentColor.copy(alpha = 0.12f))
//                        .testTag("synced_highlight_zone")
//                )
//            }
        }
    }
}
