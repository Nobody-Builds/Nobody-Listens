package com.nobodybuilds.listens.presentation.components.brickbreaker

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.presentation.utils.LocalAppHapticsConfig
import com.nobodybuilds.listens.presentation.utils.performAppHapticFeedback
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

private const val EasterEggPrefsName = "lmusic_easter_egg"
private const val EasterEggHighScoreKey = "brick_breaker_high_score"
private const val FixedPhysicsStepSeconds = 1f / 180f
private const val MaxPhysicsStepsPerFrame = 8

// --- Composable ---

@Composable
fun BrickBreakerOverlay(
    modifier: Modifier = Modifier,
    isMiniPlayerVisible: Boolean = false,
    onPlayRandom: () -> Unit,
    onClose: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val preferences = remember(context) {
        context.getSharedPreferences(EasterEggPrefsName, Context.MODE_PRIVATE)
    }

    // Game Physics Constants
    val baseBallVelocity = 800f

    // Game State
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var paddleX by remember { mutableFloatStateOf(0f) }

    // Dynamic Difficulty Props
    var level by remember { mutableIntStateOf(1) }
    var currentSpeedMult by remember { mutableFloatStateOf(1f) }
    var paddleWidthPx by remember { mutableFloatStateOf(0f) }

    val paddleHeightPx = with(density) { 16.dp.toPx() }
    val paddleBottomInset = with(density) { 32.dp.toPx() }
    val ballRadius = with(density) { 10.dp.toPx() }

    var ballPosition by remember { mutableStateOf(Offset.Zero) }
    var ballVelocity by remember { mutableStateOf(Offset(baseBallVelocity, -baseBallVelocity)) }
    var ballLaunched by remember { mutableStateOf(false) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember {
        mutableIntStateOf(preferences.getInt(EasterEggHighScoreKey, 0))
    }
    var hasGameStarted by remember { mutableStateOf(false) }
    var hasWon by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var physicsAccumulator by remember { mutableFloatStateOf(0f) }

    val bricks = remember { mutableStateListOf<BrickState>() }
    val particles = remember { mutableStateListOf<Particle>() }

    // Initialization of dimensions
    LaunchedEffect(areaSize) {
        if (areaSize != IntSize.Zero && paddleWidthPx == 0f) {
            paddleWidthPx = areaSize.width * 0.25f
        }
    }

    fun centerPaddle() {
        if (areaSize == IntSize.Zero) return
        paddleX = ((areaSize.width.toFloat() - paddleWidthPx) / 2f).coerceAtLeast(0f)
    }

    fun attachBallToPaddle() {
        if (areaSize == IntSize.Zero) return
        val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
        ballPosition = Offset(paddleX + paddleWidthPx / 2f, paddleTop - ballRadius - 4f)
        val speed = baseBallVelocity * currentSpeedMult
        val randomX = Random.nextDouble(-0.5, 0.5).toFloat() * speed
        ballVelocity = Offset(randomX, -speed)
    }

    fun spawnParticles(rect: Rect, color: Color, count: Int = 8) {
        particles.addAll(burstParticles(rect, color, count))
    }

    fun updateHighScoreIfNeeded(value: Int = score) {
        if (value <= highScore) return
        highScore = value
        preferences.edit().putInt(EasterEggHighScoreKey, value).apply()
    }

    fun generateLevel(lvl: Int) {
        if (areaSize == IntSize.Zero) return
        bricks.clear()
        particles.clear()

        // Difficulty scaling
        currentSpeedMult = 1f + (lvl - 1) * 0.1f
        val widthFactor = (0.25f - (lvl * 0.02f)).coerceAtLeast(0.10f)
        paddleWidthPx = areaSize.width * widthFactor

        bricks.addAll(generateLevelBricks(lvl, areaSize, density, colorScheme))
    }

    fun resetGame(fullReset: Boolean = false) {
        isGameOver = false
        hasWon = false
        ballLaunched = false
        physicsAccumulator = 0f
        particles.clear()

        if (fullReset) {
            score = 0
            lives = 3
            level = 1
            hasGameStarted = false
        }

        generateLevel(level)
        centerPaddle()
        attachBallToPaddle()
    }

    fun nextLevel() {
        level++
        ballLaunched = false
        hasWon = false
        physicsAccumulator = 0f
        generateLevel(level)
        centerPaddle()
        attachBallToPaddle()
    }

    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(areaSize) {
        if (areaSize != IntSize.Zero) {
            if (!isInitialized) {
                resetGame(true)
                isInitialized = true
            } else {
                // Resize logic: update paddle constraints without resetting game progress
                val widthFactor = (0.25f - (level * 0.02f)).coerceAtLeast(0.10f)
                paddleWidthPx = areaSize.width * widthFactor
                paddleX = paddleX.coerceIn(0f, areaSize.width - paddleWidthPx)
                
                if (!ballLaunched && !isGameOver && !hasWon) {
                    attachBallToPaddle()
                }
            }
        }
    }

    // Game Loop
    LaunchedEffect(ballLaunched, hasWon, isGameOver, areaSize) {
        if (areaSize == IntSize.Zero || isGameOver || hasWon) return@LaunchedEffect

        var lastFrameNanos = withFrameNanos { it }

        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val deltaTime = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = frameNanos

            val particleIterator = particles.listIterator()
            while (particleIterator.hasNext()) {
                val p = particleIterator.next()
                if (p.life <= 0) {
                    particleIterator.remove()
                } else {
                    val newLife = p.life - deltaTime * 1.5f
                    val grav = 800f
                    val newVy = p.velocity.y + grav * deltaTime
                    val newPos = p.position + Offset(p.velocity.x * deltaTime, newVy * deltaTime)
                    particleIterator.set(p.copy(position = newPos, velocity = Offset(p.velocity.x, newVy), life = newLife))
                }
            }

            if (!ballLaunched) {
                physicsAccumulator = 0f
                continue
            }

            physicsAccumulator = (physicsAccumulator + deltaTime).coerceAtMost(0.2f)

            var steps = 0
            while (physicsAccumulator >= FixedPhysicsStepSeconds && steps < MaxPhysicsStepsPerFrame) {
                physicsAccumulator -= FixedPhysicsStepSeconds
                steps++

                var nextPos = ballPosition
                var nextVelocity = ballVelocity
                var ballLostThisStep = false
                var levelClearedThisStep = false
                var brickResolvedThisStep = false

                val stepDistance = (nextVelocity * FixedPhysicsStepSeconds).getDistance()
                val minTravelPerSubStep = max(ballRadius * 0.45f, 1f)
                val subSteps = max(1, ceil(stepDistance / minTravelPerSubStep).toInt()).coerceAtMost(8)
                val subDelta = FixedPhysicsStepSeconds / subSteps

                repeat(subSteps) {
                    if (ballLostThisStep || levelClearedThisStep) return@repeat

                    val previousPos = nextPos
                    var candidatePos = previousPos + nextVelocity * subDelta

                    if (candidatePos.x - ballRadius <= 0f) {
                        candidatePos = candidatePos.copy(x = ballRadius)
                        nextVelocity = nextVelocity.copy(x = abs(nextVelocity.x))
                    }
                    if (candidatePos.x + ballRadius >= areaSize.width) {
                        candidatePos = candidatePos.copy(x = areaSize.width - ballRadius)
                        nextVelocity = nextVelocity.copy(x = -abs(nextVelocity.x))
                    }
                    if (candidatePos.y - ballRadius <= 0f) {
                        candidatePos = candidatePos.copy(y = ballRadius)
                        nextVelocity = nextVelocity.copy(y = abs(nextVelocity.y))
                    }

                    val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
                    val paddleRect = Rect(paddleX, paddleTop, paddleX + paddleWidthPx, paddleTop + paddleHeightPx)

                    if (nextVelocity.y > 0f && circleIntersectsRect(candidatePos, ballRadius, paddleRect)) {
                        val hitPoint =
                            ((candidatePos.x - paddleRect.center.x) / (paddleWidthPx / 2f)).coerceIn(-1f, 1f)
                        val currentSpeed = max(nextVelocity.getDistance(), baseBallVelocity * 0.85f)
                        val newSpeed = (currentSpeed * 1.015f).coerceAtMost(baseBallVelocity * 3f)
                        val newVx = newSpeed * hitPoint * 0.82f
                        val newVyMagnitude =
                            sqrt(max(0f, (newSpeed * newSpeed) - (newVx * newVx))).coerceAtLeast(newSpeed * 0.35f)

                        nextVelocity = Offset(newVx, -newVyMagnitude)
                        candidatePos = candidatePos.copy(y = paddleRect.top - ballRadius - 1f)

                        view.performAppHapticFeedback(
                            appHapticsConfig,
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP
                        )
                    }

                    val hitIndex = if (brickResolvedThisStep) {
                        null
                    } else {
                        bricks.indices.firstOrNull { index ->
                            val brick = bricks[index]
                            (brick.hitsRemaining > 0 || brick.type == BrickType.Solid) &&
                                circleIntersectsRect(candidatePos, ballRadius, brick.rect)
                        }
                    }

                    if (hitIndex != null) {
                        val brick = bricks[hitIndex]
                        brickResolvedThisStep = true
                        val normal = collisionNormalForRect(
                            previousCenter = previousPos,
                            currentCenter = candidatePos,
                            rect = brick.rect
                        )
                        nextVelocity = enforceMinimumVerticalSpeed(reflectVelocity(nextVelocity, normal))
                        candidatePos = resolveCircleFromRect(candidatePos, ballRadius, brick.rect, normal)

                        view.performAppHapticFeedback(
                            appHapticsConfig,
                            android.view.HapticFeedbackConstants.CLOCK_TICK
                        )

                        if (brick.type != BrickType.Solid) {
                            val newHits = brick.hitsRemaining - 1
                            spawnParticles(brick.rect, brick.color)

                            if (newHits > 0) {
                                val newColor = if (newHits == 1) colorScheme.tertiary else brick.color
                                bricks[hitIndex] = brick.copy(hitsRemaining = newHits, color = newColor)
                            } else {
                                bricks[hitIndex] = brick.copy(hitsRemaining = newHits)
                                score += if (brick.type == BrickType.Hard) 100 else 50
                                updateHighScoreIfNeeded(score)
                                view.performAppHapticFeedback(
                                    appHapticsConfig,
                                    android.view.HapticFeedbackConstants.LONG_PRESS
                                )
                            }

                            nextVelocity = enforceMinimumVerticalSpeed(nextVelocity * 1.01f)
                            if (bricks.none { it.type != BrickType.Solid && it.hitsRemaining > 0 }) {
                                hasWon = true
                                ballLaunched = false
                                levelClearedThisStep = true
                                updateHighScoreIfNeeded(score)
                            }
                        } else {
                            spawnParticles(brick.rect, Color.Gray, 3)
                        }
                    }

                    if (candidatePos.y - ballRadius > areaSize.height) {
                        lives -= 1
                        ballLaunched = false
                        physicsAccumulator = 0f
                        if (lives <= 0) {
                            isGameOver = true
                            updateHighScoreIfNeeded(score)
                        }
                        attachBallToPaddle()
                        ballLostThisStep = true
                    } else {
                        nextPos = candidatePos
                    }
                }

                if (ballLostThisStep || levelClearedThisStep) {
                    break
                }

                ballPosition = nextPos
                ballVelocity = nextVelocity
            }
        }
    }

    // --- UI Structure ---
    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ... (Header and Stats logic remains similar) ...
            // 1. Header
            BrickBreakerHeader(highScore = highScore, onClose = onClose)

            // 2. Stats Dashboard
            BrickBreakerStats(score = score, level = level, lives = lives)

            // 3. Game Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp)) // Uniform radius
                    .background(colorScheme.surfaceContainerLow)
                    .onSizeChanged { areaSize = it }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    if (!hasGameStarted) return@detectDragGestures
                                    if (!ballLaunched && !isGameOver && !hasWon) {
                                        ballLaunched = true
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (!hasGameStarted) return@detectDragGestures
                                    change.consume()
                                    if (areaSize != IntSize.Zero) {
                                        paddleX = (paddleX + dragAmount.x)
                                            .coerceIn(0f, areaSize.width - paddleWidthPx)

                                        if (!ballLaunched) {
                                            attachBallToPaddle()
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (!hasGameStarted) return@detectTapGestures
                                    if (!ballLaunched && !isGameOver && !hasWon) {
                                        ballLaunched = true
                                    }
                                }
                            )
                        }
                ) {
                    val paddleTop = areaSize.height - paddleBottomInset - paddleHeightPx
                    val paddleRect = Rect(paddleX, paddleTop, paddleX + paddleWidthPx, paddleTop + paddleHeightPx)

                    drawBrickBreakerScene(
                        bricks = bricks,
                        particles = particles,
                        paddleRect = paddleRect,
                        paddleColor = colorScheme.primary,
                        ballColor = colorScheme.onSurface,
                        ballRadius = ballRadius,
                        ballPosition = ballPosition
                    )
                }

                // ... (Overlays remain same) ...
                 if (!hasGameStarted) {
                    PreLaunchMenu(
                        highScore = highScore,
                        onPlayClick = {
                            resetGame(fullReset = true)
                            hasGameStarted = true
                            ballLaunched = true
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (isGameOver || hasWon) {
                    BrickBreakerResultOverlay(
                        hasWon = hasWon,
                        score = score,
                        onContinue = {
                            if (hasWon) {
                                nextLevel()
                            } else {
                                resetGame(fullReset = true)
                            }
                        }
                    )
                } else if (!ballLaunched) {
                    BrickBreakerRelaunchHint(modifier = Modifier.align(Alignment.Center))
                }
            }
            
            // Dynamic Bottom Section
            BrickBreakerBottomBar(isMiniPlayerVisible = isMiniPlayerVisible, onPlayRandom = onPlayRandom)
        }
    }
}
