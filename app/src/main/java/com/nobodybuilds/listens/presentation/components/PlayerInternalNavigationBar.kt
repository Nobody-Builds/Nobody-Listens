package com.nobodybuilds.listens.presentation.components

import com.nobodybuilds.listens.presentation.navigation.navigateToTopLevelSafely

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics
import com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nobodybuilds.listens.BottomNavItem
import com.nobodybuilds.listens.data.preferences.NavBarStyle
import com.nobodybuilds.listens.presentation.components.scoped.CustomNavigationBarItem
import com.nobodybuilds.listens.presentation.navigation.Screen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val NavBarContentHeight = 64.dp // Icon + small label
internal val NavBarCompactContentHeight = 56.dp // Icons only
internal val NavBarContentHeightFullWidth = NavBarContentHeight // Altura del contenido de la barra de navegación en modo completo
// Some OEM freeform/floating-window modes can report a bottom inset close to the whole window height.
internal val MaxNavigationBarBottomInset = 96.dp

internal fun sanitizeNavigationBarBottomInset(systemNavBarInset: Dp): Dp {
    if (!systemNavBarInset.value.isFinite()) return 0.dp
    return systemNavBarInset.coerceIn(0.dp, MaxNavigationBarBottomInset)
}

internal fun calculatePlayerSheetCollapsedTargetY(
    containerHeightPx: Float,
    collapsedContentHeightPx: Float,
    bottomMarginPx: Float,
    bottomSpacerPx: Float
): Float {
    val safeContainerHeightPx = containerHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeCollapsedContentHeightPx = collapsedContentHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomMarginPx = bottomMarginPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomSpacerPx = bottomSpacerPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxTargetY = (safeContainerHeightPx - safeCollapsedContentHeightPx).coerceAtLeast(0f)

    return (safeContainerHeightPx - safeCollapsedContentHeightPx - safeBottomMarginPx - safeBottomSpacerPx)
        .coerceIn(0f, maxTargetY)
}

internal fun resolveNavBarContentHeight(compactMode: Boolean): Dp =
    if (compactMode) NavBarCompactContentHeight else NavBarContentHeight

internal fun resolveNavBarSurfaceHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp {
    val contentHeight = resolveNavBarContentHeight(compactMode)
    return if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        contentHeight + systemNavBarInset
    } else {
        contentHeight
    }
}

internal fun resolveNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = resolveNavBarContentHeight(compactMode) + systemNavBarInset

/** Horizontal fling speed on the bar that steps one tab even without crossing into it. */
internal val NavBarFlingVelocityThreshold = 700.dp // per second

/**
 * Visual state of the bar's drag gesture. The pill position and alpha are [Animatable]s read in the
 * draw phase; [active] and [hoverIndex] are composition state that only change on start/end and when
 * the finger crosses into another tab.
 */
@Stable
private class NavBarDragController(private val scope: CoroutineScope) {
    var active by mutableStateOf(false)
        private set
    var hoverIndex by mutableIntStateOf(-1)
        private set
    var startIndex: Int = -1
        private set
    val pillX = Animatable(0f)
    val pillAlpha = Animatable(0f)

    // Not state: only read in the draw phase, which always runs after layout.
    var containerCoordinates: LayoutCoordinates? = null
    var indicatorCoordinates: LayoutCoordinates? = null

    private var dragging = false
    private var followJob: Job? = null
    private var settleJob: Job? = null
    private var alphaJob: Job? = null

    fun indicatorCenterY(): Float? {
        val container = containerCoordinates ?: return null
        val indicator = indicatorCoordinates ?: return null
        if (!container.isAttached || !indicator.isAttached) return null
        return runCatching {
            container.localPositionOf(indicator, Offset(0f, indicator.size.height / 2f)).y
        }.getOrNull()
    }

    fun start(startIndex: Int, startX: Float, fadeIn: Boolean) {
        dragging = true
        this.startIndex = startIndex
        hoverIndex = startIndex
        active = true
        settleJob?.cancel()
        followJob?.cancel()
        alphaJob?.cancel()
        followJob = scope.launch { pillX.snapTo(startX) }
        alphaJob = scope.launch {
            if (fadeIn) pillAlpha.animateTo(1f, tween(120)) else pillAlpha.snapTo(1f)
        }
    }

    fun move(trackX: Float, index: Int, haptics: NobodyListensHaptics) {
        if (!dragging) return
        if (index >= 0 && index != hoverIndex) {
            hoverIndex = index
            haptics.selection()
        }
        // animateTo cancels the previous follow animation, keeping one spring in flight.
        followJob = scope.launch { pillX.animateTo(trackX, FollowSpring) }
    }

    fun release(target: Int, targetX: Float) {
        dragging = false
        hoverIndex = target
        settle(targetX)
    }

    fun cancel(backX: Float?) {
        dragging = false
        hoverIndex = startIndex
        if (backX == null) {
            followJob?.cancel()
            finish()
        } else {
            settle(backX)
        }
    }

    private fun settle(targetX: Float) {
        followJob?.cancel()
        settleJob?.cancel()
        settleJob = scope.launch {
            try {
                pillX.animateTo(targetX, SettleSpring)
            } finally {
                if (!dragging) finish()
            }
        }
    }

    private fun finish() {
        // Bring the per-item pill back under the shared one first, then fade the shared pill out,
        // so the hand-off is invisible.
        active = false
        alphaJob?.cancel()
        alphaJob = scope.launch { pillAlpha.animateTo(0f, tween(durationMillis = 180, delayMillis = 60)) }
    }

    private companion object {
        val FollowSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
        val SettleSpring = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
    }
}

/**
 * Waits for a horizontal drag on the bar. Taps and vertical movement are left untouched (nothing is
 * consumed until horizontal touch slop is passed), so item clicks, the Search double tap and any
 * vertical gesture above us keep working. Once horizontal, the moves are consumed, which cancels the
 * pending tap on the item under the finger.
 */
private suspend fun PointerInputScope.detectNavBarHorizontalDrag(
    enabled: () -> Boolean,
    onStart: (downX: Float, x: Float) -> Unit,
    onMove: (x: Float) -> Unit,
    onEnd: (velocityX: Float) -> Unit,
    onCancel: () -> Unit
) {
    awaitEachGesture {
        // Items consume the down for their click, so don't require it to be unconsumed.
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!enabled()) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        val pointerId = down.id
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var totalX = 0f
        var totalY = 0f

        // Slop phase: decide between tap (up), vertical (leave alone) and horizontal drag.
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
            if (!change.pressed || change.isConsumed) return@awaitEachGesture
            tracker.addPosition(change.uptimeMillis, change.position)
            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y
            if (abs(totalY) > slop && abs(totalY) >= abs(totalX)) return@awaitEachGesture
            if (abs(totalX) > slop) {
                change.consume()
                onStart(down.position.x, change.position.x)
                break
            }
        }

        // Drag phase.
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null) {
                onCancel()
                return@awaitEachGesture
            }
            if (!change.pressed) {
                tracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
                val velocityX = runCatching { tracker.calculateVelocity().x }.getOrDefault(0f)
                onEnd(velocityX)
                return@awaitEachGesture
            }
            if (change.isConsumed) {
                onCancel()
                return@awaitEachGesture
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
            onMove(change.position.x)
        }
    }
}

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp,
    onSearchIconDoubleTap: () -> Unit
) {
    val navBarInsetPadding = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    // Maintain invariant: bottomBarPadding + innerRowPadding = the sanitized system nav bar inset.
    // This prevents nav items from appearing behind the gesture bar during style transitions,
    // e.g. FULL_WIDTH→DEFAULT where bottomBarPadding starts at 0 and animates to systemNavBarInset.
    val innerRowPadding = (navBarInsetPadding - bottomBarPadding).coerceAtLeast(0.dp)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)

    val haptics = LocalNobodyListensHaptics.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dragScope = rememberCoroutineScope()
    val drag = remember(dragScope) { NavBarDragController(dragScope) }
    val selectedIndex = navItems.indexOfFirst { currentRoute != null && it.screen.route == currentRoute }
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestNavItems by rememberUpdatedState(navItems)
    val latestNavController by rememberUpdatedState(navController)
    var lastSearchTapTimestamp by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val indicatorCornerPx = with(density) { 16.dp.toPx() }
    val pillWidthPx = with(density) { (if (compactMode) 56.dp else 48.dp).toPx() }
    val pillHeightPx = with(density) { (if (compactMode) 32.dp else 28.dp).toPx() }
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val rowModifier = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = innerRowPadding, start = 12.dp, end = 12.dp)
    } else {
        modifier
            .padding(start = 10.dp, end = 10.dp, bottom = innerRowPadding)
            .fillMaxWidth()
    }
    Row(
        modifier = rowModifier
            .onGloballyPositioned { drag.containerCoordinates = it }
            // Shared pill drawn behind the items while dragging. Reads Animatables in the draw phase
            // only, so dragging never recomposes the bar (only crossing into a new tab does).
            .drawBehind {
                val alpha = drag.pillAlpha.value
                if (alpha <= 0f) return@drawBehind
                val centerY = drag.indicatorCenterY() ?: (size.height / 2f)
                val left = drag.pillX.value - pillWidthPx / 2f
                val top = centerY - pillHeightPx / 2f
                val radius = minOf(indicatorCornerPx, pillHeightPx / 2f)
                drawRoundRect(
                    color = pillColor,
                    topLeft = Offset(left, top),
                    size = Size(pillWidthPx, pillHeightPx),
                    cornerRadius = CornerRadius(radius, radius),
                    alpha = alpha
                )
            }
            .pointerInput(navItems.size, isRtl) {
                val count = navItems.size
                val flingThresholdPx = NavBarFlingVelocityThreshold.toPx()
                detectNavBarHorizontalDrag(
                    enabled = { latestNavigationEnabled && count > 0 },
                    onStart = { downX, x ->
                        val width = size.width.toFloat()
                        val start = latestSelectedIndex.takeIf { it >= 0 }
                            ?: navBarIndexAt(downX, width, count, isRtl)
                        val startX = if (latestSelectedIndex >= 0) {
                            navBarSlotCenterX(start, width, count, isRtl)
                        } else {
                            navBarPillTrackX(downX, width, count)
                        }
                        drag.start(startIndex = start, startX = startX, fadeIn = latestSelectedIndex < 0)
                        lastSearchTapTimestamp = 0L
                        drag.move(
                            navBarPillTrackX(x, width, count),
                            navBarIndexAt(x, width, count, isRtl),
                            haptics
                        )
                    },
                    onMove = { x ->
                        val width = size.width.toFloat()
                        drag.move(
                            navBarPillTrackX(x, width, count),
                            navBarIndexAt(x, width, count, isRtl),
                            haptics
                        )
                    },
                    onEnd = { velocityX ->
                        val width = size.width.toFloat()
                        val target = resolveNavBarDragTarget(
                            startIndex = drag.startIndex,
                            hoverIndex = drag.hoverIndex,
                            velocityX = velocityX,
                            flingThresholdPx = flingThresholdPx,
                            count = count,
                            isRtl = isRtl
                        )
                        if (target < 0) {
                            drag.cancel(navBarSlotCenterX(latestSelectedIndex.coerceAtLeast(0), width, count, isRtl))
                            return@detectNavBarHorizontalDrag
                        }
                        if (target != drag.hoverIndex) {
                            // A fling stepped to a tab the finger never reached: tick for it too.
                            haptics.selection()
                        }
                        drag.release(target, navBarSlotCenterX(target, width, count, isRtl))
                        lastSearchTapTimestamp = 0L
                        val items = latestNavItems
                        if (target != latestSelectedIndex && target in items.indices) {
                            latestNavController.navigateToTopLevelSafely(items[target].screen.route)
                        }
                    },
                    onCancel = {
                        val width = size.width.toFloat()
                        val back = latestSelectedIndex
                        if (back >= 0) {
                            drag.cancel(navBarSlotCenterX(back, width, count, isRtl))
                        } else {
                            drag.cancel(null)
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scope = rememberCoroutineScope()
        val dragActive = drag.active
        val dragHover = drag.hoverIndex
        navItems.forEachIndexed { index, item ->
            val isSelected = currentRoute != null && currentRoute == item.screen.route
            // While dragging, the tab under the finger takes the selected look (changes per tab only).
            val isHighlighted = if (dragActive) index == dragHover else isSelected
            // Daylight: Sky indicator (secondaryContainer) with ink icon, as in Material 3.
            val selectedColor = MaterialTheme.colorScheme.onSecondaryContainer
            val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val indicatorColorFromTheme = MaterialTheme.colorScheme.secondaryContainer

            val iconPainterResId = if (isHighlighted && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId
            } else {
                item.iconResId
            }
            val iconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = item.label
                    )
                }
            }
            val selectedIconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = item.label
                    )
                }
            }
            val labelLambda: (@Composable () -> Unit)? = if (compactMode) {
                null
            } else {
                remember(item.label) {
                    { Text(item.label) }
                }
            }
            val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
                click@{
                    if (!latestNavigationEnabled) {
                        lastSearchTapTimestamp = 0L
                        return@click
                    }

                    val itemRoute = item.screen.route
                    val isSearchTab = itemRoute == Screen.Search.route
                    val isAlreadySelected = latestCurrentRoute == itemRoute
                    if (!isAlreadySelected) {
                        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().selection()
                    }

                    if (isSearchTab) {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                        lastSearchTapTimestamp = now

                        if (!isAlreadySelected) {
                            if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                lastSearchTapTimestamp = 0L
                                return@click
                            }
                        }

                        if (isDoubleTap) {
                            lastSearchTapTimestamp = 0L
                            if (isAlreadySelected) {
                                latestOnSearchIconDoubleTap()
                            } else {
                                scope.launch {
                                    delay(160L)
                                    latestOnSearchIconDoubleTap()
                                }
                            }
                        }
                    } else if (!isAlreadySelected) {
                        lastSearchTapTimestamp = 0L
                        navController.navigateToTopLevelSafely(itemRoute)
                    } else {
                        lastSearchTapTimestamp = 0L
                    }
                }
            }
            CustomNavigationBarItem(
                modifier = Modifier.weight(1f),
                selected = isHighlighted,
                onClick = onClickLambda,
                enabled = currentRoute != null,
                compactMode = compactMode,
                icon = iconLambda,
                selectedIcon = selectedIconLambda,
                label = labelLambda,
                contentDescription = item.label,
                alwaysShowLabel = true,
                selectedIconColor = selectedColor,
                unselectedIconColor = unselectedColor,
                selectedTextColor = selectedColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = indicatorColorFromTheme,
                showIndicator = !dragActive,
                onIndicatorPositioned = if (index == 0) {
                    { coords -> drag.indicatorCoordinates = coords }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    PlayerInternalNavigationItemsRow(
        navController = navController,
        navItems = navItems,
        currentRoute = currentRoute,
        navBarStyle = navBarStyle,
        compactMode = compactMode,
        bottomBarPadding = bottomBarPadding,
        onSearchIconDoubleTap = onSearchIconDoubleTap,
        modifier = modifier
    )
}
