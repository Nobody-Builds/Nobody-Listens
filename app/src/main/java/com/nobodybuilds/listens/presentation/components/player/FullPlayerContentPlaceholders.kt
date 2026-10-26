package com.nobodybuilds.listens.presentation.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nobodybuilds.listens.R
import kotlinx.coroutines.flow.first
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlinx.coroutines.flow.collect

@Composable
internal fun DelayedContent(
    shouldDelay: Boolean,
    showPlaceholders: Boolean,
    applyPlaceholderDelayOnClose: Boolean,
    switchOnDragRelease: Boolean,
    isSheetDragGestureActive: Boolean,
    sharedBoundsModifier: Modifier = Modifier,
    expansionFractionProvider: () -> Float,
    isExpandedOverride: Boolean = false,
    normalStartThreshold: Float,
    delayAppearThreshold: Float,
    delayCloseThreshold: Float,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val appearThreshold = delayAppearThreshold.coerceIn(0f, 1f)
    val closeThreshold = delayCloseThreshold.coerceIn(0f, 1f)
    var isDelayGateOpen by remember(shouldDelay) { mutableStateOf(!shouldDelay) }

    LaunchedEffect(
        shouldDelay,
        appearThreshold,
        closeThreshold,
        applyPlaceholderDelayOnClose,
        switchOnDragRelease,
        isSheetDragGestureActive,
        isExpandedOverride,
        expansionFractionProvider
    ) {
        if (!shouldDelay) {
            isDelayGateOpen = true
            return@LaunchedEffect
        }

        if (switchOnDragRelease) {
            if (isSheetDragGestureActive) {
                return@LaunchedEffect
            }

            if (isExpandedOverride) {
                isDelayGateOpen = true
            } else {
                snapshotFlow { expansionFractionProvider().coerceIn(0f, 1f) }
                    .first { fraction -> fraction <= 0.001f }
                isDelayGateOpen = false
            }
            return@LaunchedEffect
        }

        var previousExpansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
        var previousExpandedOverride = isExpandedOverride

        snapshotFlow {
            val rawExpansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
            val effectiveExpansionFraction =
                if (isExpandedOverride && rawExpansionFraction >= 0.985f) 1f else rawExpansionFraction
            DelayedContentFrame(
                rawExpansionFraction = rawExpansionFraction,
                effectiveExpansionFraction = effectiveExpansionFraction,
                isExpandedOverride = isExpandedOverride
            )
        }.collect { frame ->
            val isCollapsingByFraction =
                frame.rawExpansionFraction < previousExpansionFraction - 0.001f
            val isExpandingByFraction =
                frame.rawExpansionFraction > previousExpansionFraction + 0.001f
            val justStartedCollapsing =
                previousExpandedOverride && !frame.isExpandedOverride
            val justStartedExpanding =
                !previousExpandedOverride && frame.isExpandedOverride
            val isCollapsing = isCollapsingByFraction || justStartedCollapsing
            val isExpanding = isExpandingByFraction || justStartedExpanding
            val isFullyExpanded =
                frame.isExpandedOverride && frame.effectiveExpansionFraction >= 0.985f

            if (frame.effectiveExpansionFraction <= 0.001f && !frame.isExpandedOverride) {
                isDelayGateOpen = false
            } else if (isFullyExpanded) {
                isDelayGateOpen = true
            } else if (isDelayGateOpen) {
                if (applyPlaceholderDelayOnClose &&
                    isCollapsing &&
                    frame.effectiveExpansionFraction <= closeThreshold
                ) {
                    isDelayGateOpen = false
                }
            } else if (
                frame.effectiveExpansionFraction >= appearThreshold &&
                    (!applyPlaceholderDelayOnClose || isExpanding || frame.isExpandedOverride)
            ) {
                isDelayGateOpen = true
            }

            previousExpansionFraction = frame.rawExpansionFraction
            previousExpandedOverride = frame.isExpandedOverride
        }
    }

    val baseAlphaProvider = remember(normalStartThreshold, expansionFractionProvider) {
        {
            ((expansionFractionProvider().coerceIn(0f, 1f) - normalStartThreshold) /
                (1f - normalStartThreshold).coerceAtLeast(0.001f))
                .coerceIn(0f, 1f)
        }
    }
    val contentBlendAlpha by animateFloatAsState(
        targetValue = if (isDelayGateOpen) 1f else 0f,
        animationSpec = if (isDelayGateOpen) {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "DelayedContentBlendAlpha"
    )
    val placeholderBlendAlpha by animateFloatAsState(
        targetValue = if (isDelayGateOpen) 0f else 1f,
        animationSpec = if (isDelayGateOpen) {
            tween(durationMillis = 360, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "DelayedPlaceholderBlendAlpha"
    )

    if (shouldDelay) {
        Box(modifier = sharedBoundsModifier) {
            val shouldComposeContent = isDelayGateOpen

            if (shouldComposeContent) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = contentBlendAlpha * baseAlphaProvider()
                    }
                ) {
                    content()
                }
            }
            if (showPlaceholders && placeholderBlendAlpha > 0.001f) {
                Box(
                    modifier = Modifier.graphicsLayer { alpha = placeholderBlendAlpha }
                ) {
                    placeholder()
                }
            }
        }
    } else {
        Box(
            modifier = sharedBoundsModifier.graphicsLayer { alpha = baseAlphaProvider() }
        ) {
            content()
        }
    }
}

private data class DelayedContentFrame(
    val rawExpansionFraction: Float,
    val effectiveExpansionFraction: Float,
    val isExpandedOverride: Boolean
)

@Composable
private fun PlaceholderBox(
    modifier: Modifier,
    cornerRadius: Dp = 12.dp,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        tonalElevation = 0.dp
    ) {}
}

@Composable
internal fun AlbumPlaceholder(
    height: Dp,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(20.dp),
        color = color,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                modifier = Modifier.size(86.dp),
                painter = painterResource(com.nobodybuilds.listens.shared.R.drawable.nl_mark_mono),
                contentDescription = null,
                tint = onColor
            )
        }
    }
}

@Composable
internal fun MetadataPlaceholder(
    expansionFractionProvider: () -> Float,
    color: Color,
    onColor: Color,
    showQueueButtons: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .graphicsLayer {
                val expansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
                alpha = expansionFraction.coerceIn(0f, 1f)
                translationY = (1f - expansionFraction.coerceIn(0f, 1f)) * 24f
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(6.dp) //2.dp
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(27.dp), //30.dp
                cornerRadius = 8.dp,
                color = color
            )
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .height(17.dp), //20.dp
                cornerRadius = 8.dp,
                color = onColor
            )
        }

        if (showQueueButtons) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(height = 42.dp, width = 50.dp),
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        topEnd = 6.dp,
                        bottomStart = 50.dp,
                        bottomEnd = 6.dp
                    ),
                    color = onColor,
                    tonalElevation = 0.dp
                ) {}
                Surface(
                    modifier = Modifier.size(height = 42.dp, width = 50.dp),
                    shape = RoundedCornerShape(
                        topStart = 6.dp,
                        topEnd = 50.dp,
                        bottomStart = 6.dp,
                        bottomEnd = 50.dp
                    ),
                    color = onColor,
                    tonalElevation = 0.dp
                ) {}
            }
        } else {
            PlaceholderBox(
                modifier = Modifier.size(width = 48.dp, height = 48.dp),
                cornerRadius = 24.dp,
                color = onColor
            )
        }
    }
}

@Composable
internal fun ProgressPlaceholder(
    color: Color,
    onColor: Color,
    showAudioMetaChip: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                cornerRadius = 3.dp,
                color = onColor.copy(alpha = 0.15f)
            )
            // Keep active segment in the layout tree but invisible to avoid visual noise.
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .height(6.dp)
                    .graphicsLayer { alpha = 0f },
                cornerRadius = 3.dp,
                color = color
            )
            // Keep thumb slot aligned but fully transparent.
            PlaceholderBox(
                modifier = Modifier
                    .padding(start = 92.dp)
                    .size(14.dp)
                    .graphicsLayer { alpha = 0f },
                cornerRadius = 7.dp,
                color = onColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceholderBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp),
                    cornerRadius = 2.dp,
                    color = onColor
                )
                PlaceholderBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp),
                    cornerRadius = 2.dp,
                    color = onColor
                )
            }

            if (showAudioMetaChip) {
                PlaceholderBox(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(min = 96.dp, max = 180.dp)
                        .height(18.dp),
                    cornerRadius = 999.dp,
                    color = onColor.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
internal fun ControlsPlaceholder(color: Color, onColor: Color) {
    val rowCorners = 60.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceholderBox(
                    modifier = Modifier.size(48.dp),
                    cornerRadius = 60.dp,
                    color = onColor
                )
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBR = 60
                    ),
                    color = color,
                    tonalElevation = 0.dp
                ) {}
                PlaceholderBox(
                    modifier = Modifier.size(48.dp),
                    cornerRadius = 60.dp,
                    color = onColor
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOGGLE_ROW_HEIGHT)
                .padding(horizontal = 26.dp)
                .background(
                    color = onColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(rowCorners)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) {
                    PlaceholderBox(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = rowCorners,
                        color = onColor.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}
