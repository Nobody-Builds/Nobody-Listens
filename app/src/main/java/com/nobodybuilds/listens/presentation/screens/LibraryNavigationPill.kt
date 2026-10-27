@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.R
import androidx.compose.ui.res.stringResource
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.rotate
import kotlin.math.abs

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LibraryNavigationPill(
    modifier: Modifier = Modifier,
    title: String,
    isExpanded: Boolean,
    iconRes: Int,
    showIcon: Boolean = true,
    pageIndex: Int,
    compressForWatchTransfer: Boolean,
    onClick: () -> Unit,
    onArrowClick: () -> Unit
) {
    data class PillState(val pageIndex: Int, val iconRes: Int, val title: String)

    val pillRadius = 50.dp//26.dp
    val innerRadius = 4.dp
    val titleHorizontalPadding = 14.dp
    val titleVerticalPadding = 10.dp
    val titleIconSize = 22.dp
    val titleIconSpacing = 10.dp
    val pillHeight = 52.dp
    val arrowContentWidth = 36.dp
    val pillGap = 4.dp
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var availableWidthPx by remember { mutableStateOf(0) }

    val animatedArrowCorner by animateDpAsState(
        targetValue = if (isExpanded) pillRadius else innerRadius,
        label = "ArrowCornerAnimation"
    )
    val compressionProgress by animateFloatAsState(
        targetValue = if (compressForWatchTransfer) 1f else 0f,
        label = "LibraryPillCompression"
    )

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ArrowRotation"
    )
    val targetArrowHorizontalPadding =
        LibraryNavigationPillArrowPaddingExpanded -
                (LibraryNavigationPillArrowPaddingExpanded - LibraryNavigationPillArrowPaddingCompressed) *
                compressionProgress
    val animatedArrowHorizontalPadding by animateDpAsState(
        targetValue = targetArrowHorizontalPadding,
        label = "LibraryPillArrowPadding"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
            .onSizeChanged { availableWidthPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        val baseTitleStyle = rememberLibraryNavigationPillTitleStyle(
            widthAxis = LibraryNavigationPillTitleWidthMax
        )
        val idealTextWidth = with(density) {
            textMeasurer.measure(
                text = AnnotatedString(title),
                style = baseTitleStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toDp()
        }
        val targetArrowWidth = arrowContentWidth + (targetArrowHorizontalPadding * 2)
        val availableWidth = if (availableWidthPx > 0) {
            with(density) { availableWidthPx.toDp() }
        } else {
            // High fallback value for initial composition
            1000.dp
        }
        val maxTitleWidth = (availableWidth - targetArrowWidth - pillGap - 40.dp).coerceAtLeast(0.dp)
        val idealTitleWidth = idealTextWidth +
                titleHorizontalPadding * 2 +
                (if (showIcon) (titleIconSize + titleIconSpacing) else 0.dp) +
                4.dp // Tiny safety buffer
        val naturalTitleWidth = minOf(idealTitleWidth, maxTitleWidth)
        val minCompressedTitleWidth = (
                titleHorizontalPadding * 2 +
                        titleIconSize +
                        titleIconSpacing +
                        LibraryNavigationPillMinimumTextWidth
                ).coerceAtMost(maxTitleWidth)
        val forcedCompressionWidth = minOf(
            LibraryNavigationPillForcedCompressionWidth,
            (naturalTitleWidth - minCompressedTitleWidth).coerceAtLeast(0.dp),
        )
        val targetTitleWidth = (
                naturalTitleWidth - (forcedCompressionWidth * compressionProgress)
                ).coerceAtLeast(minCompressedTitleWidth)
        val widthCompressionRatio = if (idealTitleWidth.value > 0f) {
            (targetTitleWidth.value / idealTitleWidth.value).coerceIn(0f, 1f)
        } else {
            1f
        }
        val widthAxisBySpace = LibraryNavigationPillTitleWidthMin +
                (LibraryNavigationPillTitleWidthMax - LibraryNavigationPillTitleWidthMin) *
                widthCompressionRatio.coerceIn(0f, 1f)
        val forcedWidthAxis = LibraryNavigationPillTitleWidthMax -
                (LibraryNavigationPillTitleWidthMax - LibraryNavigationPillCompressedWidthAxis) *
                compressionProgress
        val targetWidthAxis = minOf(widthAxisBySpace, forcedWidthAxis)
        val animatedTitleWidth by animateDpAsState(
            targetValue = targetTitleWidth,
            label = "LibraryPillTitleWidth"
        )
        val animatedWidthAxis by animateFloatAsState(
            targetValue = targetWidthAxis,
            label = "LibraryPillTitleAxis"
        )
        val titleStyle = rememberLibraryNavigationPillTitleStyle(widthAxis = animatedWidthAxis)

        Row(
            modifier = Modifier.height(pillHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(pillGap)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = pillRadius,
                    bottomStart = pillRadius,
                    topEnd = innerRadius,
                    bottomEnd = innerRadius
                ),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .width(animatedTitleWidth)
                    .height(pillHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = pillRadius,
                            bottomStart = pillRadius,
                            topEnd = innerRadius,
                            bottomEnd = innerRadius
                        )
                    )
                    .clickable(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = titleHorizontalPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedContent(
                        targetState = PillState(pageIndex = pageIndex, iconRes = iconRes, title = title),
                        transitionSpec = {
                            // Calculate direction based on shortest path for potentially infinite/large page indices
                            val diff = targetState.pageIndex - initialState.pageIndex
                            val direction = when {
                                diff == 0 -> 0
                                // If the absolute difference is very large, it's likely a wrap-around or a direct jump
                                // We treat jumps as "forward" if positive, but we could also check a threshold
                                abs(diff) > 1 -> diff.coerceIn(-1, 1)
                                else -> diff
                            }

                            val slideIn = slideInHorizontally { fullWidth ->
                                if (direction >= 0) fullWidth else -fullWidth
                            } + fadeIn(animationSpec = tween(220))

                            val slideOut = slideOutHorizontally { fullWidth ->
                                if (direction >= 0) -fullWidth else fullWidth
                            } + fadeOut(animationSpec = tween(220))

                            slideIn.togetherWith(slideOut)
                        },
                        label = "LibraryPillTitle"
                    ) { targetState ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = titleVerticalPadding)
                                .animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = showIcon,
                                enter = expandHorizontally(
                                    animationSpec = tween(durationMillis = 220),
                                    expandFrom = Alignment.Start
                                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                                exit = shrinkHorizontally(
                                    animationSpec = tween(durationMillis = 220),
                                    shrinkTowards = Alignment.Start
                                ) + fadeOut(animationSpec = tween(durationMillis = 160))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = targetState.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(titleIconSize),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(titleIconSpacing))
                                }
                            }
                            Text(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 4.dp), // Add slight end padding for safety
                                text = targetState.title,
                                style = titleStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible, // Change to Visible to prevent early ellipsis
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = animatedArrowCorner,
                    bottomStart = animatedArrowCorner,
                    topEnd = pillRadius,
                    bottomEnd = pillRadius
                ),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .height(pillHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = animatedArrowCorner,
                            bottomStart = animatedArrowCorner,
                            topEnd = pillRadius,
                            bottomEnd = pillRadius
                        )
                    )
                    .clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onArrowClick
                    )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = animatedArrowHorizontalPadding)
                        .width(arrowContentWidth),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.rotate(arrowRotation),
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.presentation_batch_d_expand_tab_menu_cd),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private const val LibraryNavigationPillTitleWidthMin = 18f
private const val LibraryNavigationPillTitleWidthMax = 100f
private const val LibraryNavigationPillCompressedWidthAxis = 74f
private val LibraryNavigationPillForcedCompressionWidth = 12.dp
private val LibraryNavigationPillMinimumTextWidth = 56.dp
private val LibraryNavigationPillArrowPaddingExpanded = 10.dp
private val LibraryNavigationPillArrowPaddingCompressed = 4.dp

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberLibraryNavigationPillTitleStyle(widthAxis: Float): TextStyle {
    return remember(widthAxis) {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(400),
                        FontVariation.width(widthAxis.coerceIn(
                            LibraryNavigationPillTitleWidthMin,
                            LibraryNavigationPillTitleWidthMax
                        )),
                        FontVariation.Setting("ROND", 100f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp
        )
    }
}
