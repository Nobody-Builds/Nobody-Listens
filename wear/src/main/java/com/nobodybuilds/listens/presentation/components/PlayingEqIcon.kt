package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** Steady bars while playing, dots while paused; no perpetual frame loop on a watch. */
@Composable
fun PlayingEqIcon(
    modifier: Modifier = Modifier,
    color: Color,
    isPlaying: Boolean = true,
    bars: Int = 3,
    minHeightFraction: Float = 0.28f,
    maxHeightFraction: Float = 1.0f,
    gapFraction: Float = 0.30f,
) {
    val activity = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "wearPlayingEqActivity",
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val tentativeBarWidth = width / (bars + (bars - 1) * (1f + gapFraction))
        val gap = tentativeBarWidth * gapFraction
        val barWidth = tentativeBarWidth
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

        repeat(bars) { i ->
            val level = when (i % 3) {
                0 -> 0.35f
                1 -> 1f
                else -> 0.65f
            }
            val barsHeightFraction = minHeightFraction + (maxHeightFraction - minHeightFraction) * level
            val barHeight = height * barsHeightFraction
            val dotHeight = barWidth
            val blendedHeight = dotHeight + (barHeight - dotHeight) * activity.value

            val top = (height - blendedHeight) / 2f
            val left = i * (barWidth + gap)

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, blendedHeight),
                cornerRadius = corner,
            )
        }
    }
}
