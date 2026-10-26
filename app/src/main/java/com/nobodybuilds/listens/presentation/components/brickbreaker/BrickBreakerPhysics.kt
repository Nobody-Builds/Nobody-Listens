package com.nobodybuilds.listens.presentation.components.brickbreaker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.unit.Density

// --- Data Models ---

enum class BrickType {
    Normal,
    Hard,
    Solid // Unbreakable
}

internal data class BrickState(
    val rect: Rect,
    val hitsRemaining: Int,
    val type: BrickType,
    val maxHits: Int,
    val color: Color
)

internal data class Particle(
    val position: Offset,
    val velocity: Offset,
    val color: Color,
    val radius: Float,
    val alpha: Float = 1f,
    val life: Float = 1.0f // 1.0 to 0.0
)

// --- Level and particle generation ---

/** Bricks for level [lvl]: a mirrored grid that gets denser and harder as levels go up. */
internal fun generateLevelBricks(
    lvl: Int,
    areaSize: IntSize,
    density: Density,
    colorScheme: ColorScheme,
): List<BrickState> {
    val bricks = mutableListOf<BrickState>()
    val padding = 8.dp.value * density.density
    val topOffset = 20.dp.value * density.density
    val rows = min(5 + lvl, 10)
    val cols = 8
    val brickHeight = 24.dp.value * density.density
    val totalPaddingX = padding * (cols + 1)
    val brickWidth = max((areaSize.width - totalPaddingX) / cols, 10f)

    // Generate grid
    for (row in 0 until rows) {
        for (col in 0 until cols / 2) {
            val shouldSkip = Random.nextFloat() < (0.1f * (lvl * 0.5f)).coerceAtMost(0.3f)
            val isHard = Random.nextFloat() < (0.1f * lvl).coerceAtMost(0.4f)
            val isSolid = lvl > 2 && Random.nextFloat() < 0.05f && row > 1

            if (!shouldSkip) {
                val type = when {
                    isSolid -> BrickType.Solid
                    isHard -> BrickType.Hard
                    else -> BrickType.Normal
                }

                val hits = if (type == BrickType.Hard) 2 else 1

                // Distinct Material 3 Colors
                val baseColor = when(type) {
                    BrickType.Solid -> colorScheme.outlineVariant
                    BrickType.Hard -> colorScheme.secondary
                    else -> colorScheme.primaryContainer
                }

                // Left Side Brick
                val leftX = padding + col * (brickWidth + padding)
                val topY = topOffset + row * (brickHeight + padding)

                bricks.add(BrickState(
                    rect = Rect(leftX, topY, leftX + brickWidth, topY + brickHeight),
                    hitsRemaining = hits,
                    maxHits = hits,
                    type = type,
                    color = baseColor
                ))

                // Right Side Mirror Brick
                val mirrorCol = cols - 1 - col
                val mirrorLeftX = padding + mirrorCol * (brickWidth + padding)

                bricks.add(BrickState(
                    rect = Rect(mirrorLeftX, topY, mirrorLeftX + brickWidth, topY + brickHeight),
                    hitsRemaining = hits,
                    maxHits = hits,
                    type = type,
                    color = baseColor
                ))
            }
        }
    }
    return bricks
}

/** A burst of [count] particles from the centre of a hit brick. */
internal fun burstParticles(rect: Rect, color: Color, count: Int = 8): List<Particle> {
    val particles = mutableListOf<Particle>()
    val centerX = rect.center.x
    val centerY = rect.center.y
    for (i in 0 until count) {
        val angle = Random.nextFloat() * 360f
        val speed = Random.nextFloat() * 300f + 100f
        val rad = Math.toRadians(angle.toDouble())
        val vx = (Math.cos(rad) * speed).toFloat()
        val vy = (Math.sin(rad) * speed).toFloat()
        
        particles.add(
            Particle(
                position = Offset(
                    centerX + Random.nextFloat() * rect.width * 0.5f - rect.width * 0.25f,
                    centerY + Random.nextFloat() * rect.height * 0.5f - rect.height * 0.25f
                ),
                velocity = Offset(vx, vy),
                color = color,
                radius = Random.nextFloat() * 8f + 4f
            )
        )
    }
    return particles
}

// --- Collision helpers ---

internal fun reflectVelocity(velocity: Offset, normal: Offset): Offset {
    val normalizedNormal = normal.normalizedOrNull() ?: return velocity
    val dot = velocity.x * normalizedNormal.x + velocity.y * normalizedNormal.y
    if (dot >= 0f) return velocity
    return velocity - normalizedNormal * (2f * dot)
}

internal fun collisionNormalForRect(
    previousCenter: Offset,
    currentCenter: Offset,
    rect: Rect,
): Offset {
    val closestX = currentCenter.x.coerceIn(rect.left, rect.right)
    val closestY = currentCenter.y.coerceIn(rect.top, rect.bottom)
    val dx = currentCenter.x - closestX
    val dy = currentCenter.y - closestY
    val outsideNormal = Offset(dx, dy).normalizedOrNull()
    if (outsideNormal != null) return outsideNormal

    val leftDistance = abs(currentCenter.x - rect.left)
    val rightDistance = abs(rect.right - currentCenter.x)
    val topDistance = abs(currentCenter.y - rect.top)
    val bottomDistance = abs(rect.bottom - currentCenter.y)
    val minDistance = min(min(leftDistance, rightDistance), min(topDistance, bottomDistance))

    return when (minDistance) {
        leftDistance -> Offset(-1f, 0f)
        rightDistance -> Offset(1f, 0f)
        topDistance -> Offset(0f, -1f)
        else -> Offset(0f, 1f)
    }.let { axisNormal ->
        val toCurrent = currentCenter - previousCenter
        if (toCurrent.x * axisNormal.x + toCurrent.y * axisNormal.y < 0f) axisNormal else axisNormal * -1f
    }
}

internal fun resolveCircleFromRect(
    center: Offset,
    radius: Float,
    rect: Rect,
    normal: Offset,
): Offset {
    val normalizedNormal = normal.normalizedOrNull() ?: return center
    val closestX = center.x.coerceIn(rect.left, rect.right)
    val closestY = center.y.coerceIn(rect.top, rect.bottom)
    val fromClosest = center - Offset(closestX, closestY)
    val distance = fromClosest.getDistance()

    if (distance > 0.0001f) {
        val pushOut = radius - distance
        if (pushOut > 0f) return center + normalizedNormal * (pushOut + 0.5f)
        return center
    }

    return center + normalizedNormal * (radius + 0.5f)
}

internal fun enforceMinimumVerticalSpeed(velocity: Offset, ratio: Float = 0.22f): Offset {
    val speed = velocity.getDistance()
    if (speed <= 0f) return velocity

    val minVy = speed * ratio
    val signY = if (velocity.y == 0f) -1f else sign(velocity.y)
    val clampedVy = if (abs(velocity.y) < minVy) signY * minVy else velocity.y
    val vxSign = if (velocity.x == 0f) 1f else sign(velocity.x)
    val adjustedVx = vxSign * sqrt(max(0f, speed * speed - clampedVy * clampedVy))
    return Offset(adjustedVx, clampedVy)
}

private fun Offset.normalizedOrNull(): Offset? {
    val length = getDistance()
    if (length <= 0.0001f) return null
    return Offset(x / length, y / length)
}

internal fun circleIntersectsRect(center: Offset, radius: Float, rect: Rect): Boolean {
    val closestX = center.x.coerceIn(rect.left, rect.right)
    val closestY = center.y.coerceIn(rect.top, rect.bottom)
    val dx = center.x - closestX
    val dy = center.y - closestY
    return dx * dx + dy * dy <= radius * radius
}
