package com.nobodybuilds.listens.presentation.components.brickbreaker

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** One frame of the game: bricks, particles, the pill paddle and the ball. */
internal fun DrawScope.drawBrickBreakerScene(
    bricks: List<BrickState>,
    particles: List<Particle>,
    paddleRect: Rect,
    paddleColor: Color,
    ballColor: Color,
    ballRadius: Float,
    ballPosition: Offset,
) {
    // Draw Bricks
    bricks.forEach { brick ->
        if (brick.hitsRemaining > 0 || brick.type == BrickType.Solid) {
            drawBrick(brick)
        }
    }
    
    // Draw Particles
    particles.forEach { p ->
        drawCircle(
            color = p.color.copy(alpha = p.alpha * p.life),
            radius = p.radius * p.life,
            center = p.position
        )
    }

    // Draw Paddle (Flat, Pill Shape)
    drawRoundRect(
        color = paddleColor,
        topLeft = paddleRect.topLeft,
        size = paddleRect.size,
        cornerRadius = CornerRadius(paddleRect.height / 2, paddleRect.height / 2)
    )

    // Draw Ball
    drawCircle(
        color = ballColor,
        radius = ballRadius,
        center = ballPosition
    )
}

private fun DrawScope.drawBrick(brick: BrickState) {
    val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

    // Solid Flat Color
    drawRoundRect(
        color = brick.color,
        topLeft = brick.rect.topLeft,
        size = brick.rect.size,
        cornerRadius = cornerRadius
    )

    if (brick.type == BrickType.Solid) {
        // Simple subtle logic for solid bricks to distinguish them
         drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = 4.dp.toPx(),
            center = brick.rect.center
        )
    }
}
