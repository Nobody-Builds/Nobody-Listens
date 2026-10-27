package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.rememberWearScreen
import com.nobodybuilds.listens.presentation.theme.surfaceContainerColor
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CurvedVolumeIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    startAngle: Float = 132f,
    sweepAngle: Float = 96f,
    strokeWidth: Dp = 6.dp,
    inset: Dp = 9.dp,
    trackColor: Color = LocalWearPalette.current.surfaceContainerColor().copy(alpha = 0.58f),
    progressColor: Color = LocalWearPalette.current.controlContainer,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "curvedVolumeIndicator",
    )

    val isRound = rememberWearScreen().isRound

    Canvas(modifier = modifier) {
        val strokeWidthPx = strokeWidth.toPx()
        val insetPx = strokeWidthPx / 2f + inset.toPx()
        val diameter = size.minDimension - (insetPx * 2f)
        if (!isRound) {
            // Square screen: the same span as the arc, drawn straight along the side the arc sits on.
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            fun pointAt(degrees: Float) = Math.toRadians(degrees.toDouble()).let {
                Offset(center.x + radius * cos(it).toFloat(), center.y + radius * sin(it).toFloat())
            }
            val midX = pointAt(startAngle + sweepAngle / 2f).x
            val x = if (midX < center.x) insetPx else size.width - insetPx
            val start = Offset(x, pointAt(startAngle).y)
            val end = Offset(x, pointAt(startAngle + sweepAngle).y)
            drawLine(trackColor, start, end, strokeWidthPx, StrokeCap.Round)
            drawLine(progressColor, start, start + (end - start) * animatedProgress, strokeWidthPx, StrokeCap.Round)
            return@Canvas
        }
        val arcSize = Size(diameter, diameter)
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f,
        )

        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
        drawArc(
            color = progressColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle * animatedProgress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
    }
}
