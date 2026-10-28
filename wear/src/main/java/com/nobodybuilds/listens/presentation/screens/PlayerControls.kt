package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import com.nobodybuilds.listens.presentation.shapes.RoundedStarShape
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.WearScreen
import com.nobodybuilds.listens.presentation.theme.rememberWearScreen
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun MainControlsRow(
    isPlaying: Boolean,
    isEmpty: Boolean,
    enabled: Boolean,
    trackProgress: () -> Float,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val screen = rememberWearScreen()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlattenedControlButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous",
            enabled = enabled,
            onClick = onPrevious,
            width = screen.scaled(44.dp),
            height = screen.scaled(54.dp),
            iconSize = screen.scaled(28.dp),
        )

        Spacer(modifier = Modifier.width(screen.scaled(10.dp)))

        CenterPlayButton(
            isPlaying = isPlaying,
            enabled = enabled && !isEmpty,
            trackProgress = trackProgress,
            onClick = onTogglePlayPause,
            screen = screen,
        )

        Spacer(modifier = Modifier.width(screen.scaled(10.dp)))

        FlattenedControlButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next",
            enabled = enabled,
            onClick = onNext,
            width = screen.scaled(44.dp),
            height = screen.scaled(54.dp),
            iconSize = screen.scaled(28.dp),
        )
    }
}

@Composable
private fun FlattenedControlButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    iconSize: Dp,
) {
    val palette = LocalWearPalette.current
    val container = if (enabled) palette.transportContainer else palette.controlDisabledContainer
    val tint = if (enabled) palette.transportContent else palette.controlDisabledContent

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun CenterPlayButton(
    isPlaying: Boolean,
    enabled: Boolean,
    trackProgress: () -> Float,
    onClick: () -> Unit,
    screen: WearScreen,
) {
    val palette = LocalWearPalette.current

    val animatedCurve by animateFloatAsState(
        targetValue = if (isPlaying) 0.08f else 0.00f,
        animationSpec = spring(),
        label = "playStarCurve",
    )
    // Keep the star, but do not run an endless animation during audio playback.
    // Shape/size/color still animate briefly when play or pause changes.
    val animatedSize by animateDpAsState(
        targetValue = screen.scaled(if (isPlaying) 60.dp else 56.dp),
        animationSpec = spring(),
        label = "playButtonSize",
    )
    val container by animateColorAsState(
        targetValue = if (enabled) palette.playContainer else palette.controlDisabledContainer,
        animationSpec = spring(),
        label = "playContainer",
    )
    val tint by animateColorAsState(
        targetValue = if (enabled) palette.playContent else palette.controlDisabledContent,
        animationSpec = spring(),
        label = "playTint",
    )

    val playShape = remember(animatedCurve) {
        RoundedStarShape(sides = 8, curve = animatedCurve.toDouble())
    }

    Box(
        modifier = Modifier.size(animatedSize + 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxSize().drawWithCache {
            // Cache geometry until size or shape changes. Progress is read only
            // during drawing, so its ticks cannot recompose or lay out controls.
            val strokeWidth = 4.dp.toPx()
            val ringInset = (strokeWidth / 2f) + 1.5.dp.toPx()
            val ringRadius = ((size.minDimension - (ringInset * 2f)) / 2f).coerceAtLeast(0f)
            val ringPath = buildPlayButtonRingPath(
                centerX = size.width / 2f,
                centerY = size.height / 2f,
                radius = ringRadius,
                curve = animatedCurve,
            )
            val ringStroke = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            val pathMeasure = PathMeasure().apply {
                setPath(ringPath, forceClosed = true)
            }
            val progressPath = Path()
            onDrawBehind {
                drawPath(
                    path = ringPath,
                    color = palette.surfaceContainerLowest,
                    style = ringStroke,
                )
                val progressLength = pathMeasure.length * trackProgress().coerceIn(0f, 1f)
                if (progressLength > 0f) {
                    progressPath.reset()
                    pathMeasure.getSegment(
                        startDistance = 0f,
                        stopDistance = progressLength,
                        destination = progressPath,
                        startWithMoveTo = true,
                    )
                    drawPath(
                        path = progressPath,
                        color = palette.controlContainer.copy(alpha = if (enabled) 1f else 0.95f),
                        style = ringStroke,
                    )
                }
            }
        })

        Box(
            modifier = Modifier
                .size(animatedSize)
                .clip(playShape)
                .background(container)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = tint,
                modifier = Modifier.size(screen.scaled(30.dp)),
            )
        }
    }
}

private fun buildPlayButtonRingPath(
    centerX: Float,
    centerY: Float,
    radius: Float,
    curve: Float,
    sides: Int = 8,
    steps: Int = 320,
): Path {
    val twoPi = Math.PI * 2.0
    val startAngle = -Math.PI / 2.0
    val angleStep = twoPi / steps.toDouble()
    val ringPath = Path()
    val boundedCurve = curve.coerceIn(0f, 0.12f)
    val boundedRadius = radius.coerceAtLeast(0f)

    fun pointAt(t: Double): Offset {
        val angle = startAngle + t
        val wave = 1f + (boundedCurve * cos((sides * angle)).toFloat())
        val distance = boundedRadius * wave
        val x = centerX + (distance * cos(angle).toFloat())
        val y = centerY + (distance * sin(angle).toFloat())
        return Offset(x, y)
    }

    val firstPoint = pointAt(0.0)
    ringPath.moveTo(firstPoint.x, firstPoint.y)
    var t = angleStep
    while (t <= twoPi) {
        val point = pointAt(t)
        ringPath.lineTo(point.x, point.y)
        t += angleStep
    }
    ringPath.close()
    return ringPath
}
