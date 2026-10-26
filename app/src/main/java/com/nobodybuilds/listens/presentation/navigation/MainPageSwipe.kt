package com.nobodybuilds.listens.presentation.navigation

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Velocity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics
import com.nobodybuilds.listens.presentation.screens.SwitchSettingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

// ---------------------------------------------------------------- Preference

private val Context.navGesturesDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_nav_gestures")
private val SWIPE_BETWEEN_PAGES = booleanPreferencesKey("swipe_between_pages")

internal fun Context.swipeBetweenPagesFlow(): Flow<Boolean> =
    applicationContext.navGesturesDataStore.data
        .map { it[SWIPE_BETWEEN_PAGES] ?: true }
        .distinctUntilChanged()

internal suspend fun Context.setSwipeBetweenPages(enabled: Boolean) {
    applicationContext.navGesturesDataStore.edit { it[SWIPE_BETWEEN_PAGES] = enabled }
}

@Composable
internal fun rememberSwipeBetweenPagesEnabled(): Boolean {
    val context = LocalContext.current
    val flow = remember(context) { context.swipeBetweenPagesFlow() }
    val enabled by flow.collectAsStateWithLifecycle(initialValue = true)
    return enabled
}

/** Settings › Behavior row. */
@Composable
fun SwipeBetweenPagesSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled = rememberSwipeBetweenPagesEnabled()
    SwitchSettingItem(
        title = "Swipe between pages",
        subtitle = "Swipe left or right to move between Home, Library, Search and Stats",
        checked = enabled,
        onCheckedChange = { value -> scope.launch { context.setSwipeBetweenPages(value) } },
        leadingIcon = {
            Icon(
                painterResource(R.drawable.rounded_touch_app_24),
                null,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    )
}

// ---------------------------------------------------------------- Routing

private val mainRootRoutesInOrder: List<String>
    get() = listOf(Screen.Home.route, Screen.Library.route, Screen.Search.route, Screen.Stats.route)

/** direction < 0 = swipe left = next page, > 0 = swipe right = previous page. */
/** One page change per gesture: the pointer and nested-scroll paths can both fire for one swipe. */
private object PageSwipeCooldown {
    @Volatile var lastNavigateAt = 0L
    const val WINDOW_MS = 450L
}

private fun neighbourRoute(route: String?, direction: Float): String? {
    val index = mainRootRouteIndex(route) ?: return null
    val target = if (direction < 0f) index + 1 else index - 1
    return mainRootRoutesInOrder.getOrNull(target)
}

// ---------------------------------------------------------------- Gesture

/**
 * Horizontal page switching for the main root screens (Home ↔ Library ↔ Search ↔ Stats).
 *
 * Two sources, both only use what children did NOT consume:
 * 1. Raw pointer drags that no child consumed (plain content, vertical lists) — observed on the
 *    Main pass after children; once a horizontal intent is clear, the gesture is claimed.
 * 2. Nested scroll leftovers from horizontal scrollables (Library's tab pager, carousels): only
 *    user-drag overscroll past their first/last item counts, flings running out do not.
 */
internal fun Modifier.mainPageSwipe(
    enabled: Boolean,
    route: String?,
    navController: NavController,
): Modifier = if (!enabled || mainRootRouteIndex(route) == null) this else composed {
    val haptics = LocalNobodyListensHaptics.current
    val densityScale = LocalDensity.current.density
    val latestRoute by rememberUpdatedState(route)

    val navigateFrom: (String, Float) -> Boolean = remember(navController) {
        nav@{ source, direction ->
            val current = navController.currentBackStackEntry?.destination?.route
            if (com.nobodybuilds.listens.BuildConfig.SMOKE_TEST) {
                android.util.Log.w("NobodyListensSwipe", "source=$source dir=$direction screen=$latestRoute current=$current")
            }
            // Only the screen the user is looking at may switch (not one that is animating out).
            if (current != latestRoute) return@nav false
            val now = android.os.SystemClock.uptimeMillis()
            if (now - PageSwipeCooldown.lastNavigateAt < PageSwipeCooldown.WINDOW_MS) return@nav false
            val target = neighbourRoute(current, direction) ?: return@nav false
            PageSwipeCooldown.lastNavigateAt = now
            if (com.nobodybuilds.listens.BuildConfig.SMOKE_TEST) {
                android.util.Log.w("NobodyListensSwipe", "navigate $current -> $target")
            }
            haptics.selection()
            navController.navigateToTopLevelSafely(target)
        }
    }

    val navigate: (Float) -> Boolean = remember(navigateFrom) { { d -> navigateFrom("pointer", d) } }
    val navigateNested: (Float) -> Boolean = remember(navigateFrom) { { d -> navigateFrom("nested", d) } }

    val nestedConnection = remember(navigateNested, densityScale) {
        object : NestedScrollConnection {
            var overscrollX = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.x != 0f) {
                    if (overscrollX != 0f && sign(overscrollX) != sign(available.x)) overscrollX = 0f
                    overscrollX += available.x
                } else if (source == NestedScrollSource.UserInput && consumed.x != 0f) {
                    // The child scrolled again, so this is not an edge swipe any more.
                    overscrollX = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val distance = overscrollX
                overscrollX = 0f
                val strongDistance = NESTED_COMMIT_DISTANCE_DP * densityScale
                val minDistance = NESTED_MIN_DISTANCE_DP * densityScale
                val flingVelocity = FLING_VELOCITY_DP * densityScale
                val flingMatches = abs(available.x) > flingVelocity && sign(available.x) == sign(distance)
                if (abs(distance) >= strongDistance || (abs(distance) >= minDistance && flingMatches)) {
                    navigateNested(sign(distance))
                }
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(nestedConnection)
        .pointerInput(navigate) {
            val touchSlop = viewConfiguration.touchSlop
            val flingVelocity = FLING_VELOCITY_DP * densityScale
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                val pointerId = down.id
                var total = Offset.Zero
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)

                // Phase 1: wait for an unconsumed, clearly horizontal movement.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                    if (!change.pressed) return@awaitEachGesture
                    if (event.changes.size > 1) return@awaitEachGesture
                    if (change.isConsumed) return@awaitEachGesture // a child (list, pager, slider) owns it
                    total += change.positionChange()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (total.getDistance() > touchSlop) {
                        if (abs(total.x) > abs(total.y) * 1.6f) {
                            change.consume()
                            break
                        }
                        return@awaitEachGesture // vertical or diagonal: leave it alone
                    }
                }

                // Phase 2: claimed. Consume on the Initial pass so children don't start scrolling.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        total += change.positionChange()
                        change.consume()
                        break
                    }
                    total += change.positionChange()
                    change.consume()
                }

                val commitDistance = size.width * COMMIT_FRACTION
                val velocityX = tracker.calculateVelocity().x
                val isFling = abs(velocityX) > flingVelocity && sign(velocityX) == sign(total.x)
                if (abs(total.x) >= commitDistance || (isFling && abs(total.x) > touchSlop * 2)) {
                    navigate(sign(total.x))
                }
            }
        }
}

private const val COMMIT_FRACTION = 0.3f
private const val FLING_VELOCITY_DP = 900f
private const val NESTED_COMMIT_DISTANCE_DP = 72f
private const val NESTED_MIN_DISTANCE_DP = 24f
