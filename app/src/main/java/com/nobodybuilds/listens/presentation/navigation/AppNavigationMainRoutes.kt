package com.nobodybuilds.listens.presentation.navigation

import androidx.annotation.OptIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.IntOffset
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nobodybuilds.listens.presentation.screens.HomeScreen
import com.nobodybuilds.listens.presentation.screens.LibraryScreen
import com.nobodybuilds.listens.presentation.screens.SearchScreen
import com.nobodybuilds.listens.presentation.screens.StatsScreen
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.components.ScreenWrapper
import androidx.navigation.NavGraphBuilder

/** Home, Search and Library: the main tabs, with directional tab transitions. */
@OptIn(UnstableApi::class)
internal fun NavGraphBuilder.mainTabDestinations(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    paddingValues: PaddingValues,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit
) {
    composable(
        Screen.Home.route,
        enterTransition = {
            mainRootEnterTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = enterTransition()
            )
        },
        exitTransition = {
            mainRootExitTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = exitTransition()
            )
        },
        popEnterTransition = {
            mainRootEnterTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = popEnterTransition()
            )
        },
        popExitTransition = {
            mainRootExitTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = popExitTransition()
            )
        },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            HomeScreen(
                navController = navController, 
                paddingValuesParent = paddingValues, 
                playerViewModel = playerViewModel,
                onOpenSidebar = onOpenSidebar
            )
        }
    }
    composable(
        Screen.Search.route,
        enterTransition = {
            mainRootEnterTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = enterTransition()
            )
        },
        exitTransition = {
            mainRootExitTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = exitTransition()
            )
        },
        popEnterTransition = {
            mainRootEnterTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = popEnterTransition()
            )
        },
        popExitTransition = {
            mainRootExitTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = popExitTransition()
            )
        },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            SearchScreen(
                paddingValues = paddingValues,
                playerViewModel = playerViewModel,
                navController = navController,
                onSearchBarActiveChange = onSearchBarActiveChange
            )
        }
    }
    composable(
        Screen.Library.route,
        enterTransition = {
            mainRootEnterTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = enterTransition()
            )
        },
        exitTransition = {
            mainRootExitTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = exitTransition()
            )
        },
        popEnterTransition = {
            mainRootEnterTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = popEnterTransition()
            )
        },
        popExitTransition = {
            mainRootExitTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = popExitTransition()
            )
        },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            LibraryScreen(navController = navController, playerViewModel = playerViewModel)
        }
    }
}

/** Stats, which slides like a main tab. */
@OptIn(UnstableApi::class)
internal fun NavGraphBuilder.statsDestination(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    paddingValues: PaddingValues
) {
    composable(
        Screen.Stats.route,
        enterTransition = {
            mainRootEnterTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = enterTransition()
            )
        },
        exitTransition = {
            mainRootExitTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = exitTransition()
            )
        },
        popEnterTransition = {
            mainRootEnterTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = popEnterTransition()
            )
        },
        popExitTransition = {
            mainRootExitTransition(
                fromRoute = initialState.destination.route,
                toRoute = targetState.destination.route,
                fallback = popExitTransition()
            )
        },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            StatsScreen(
                navController = navController,
                bottomInset = paddingValues.calculateBottomPadding()
            )
        }
    }
}

private enum class MainRootDirection {
    FORWARD,
    BACKWARD
}

// Faster transitions for bottom navigation bar switching
private const val BOTTOM_NAV_TRANSITION_DURATION = 250

private val MAIN_ROOT_TRANSITION_SPEC =
    tween<IntOffset>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION, easing = FastOutSlowInEasing)

private val MAIN_ROOT_FADE_SPEC =
    tween<Float>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION, easing = FastOutSlowInEasing)

private fun mainRootDirection(
    fromRoute: String?,
    toRoute: String?
): MainRootDirection? {
    val fromIndex = mainRootRouteIndex(fromRoute) ?: return null
    val toIndex = mainRootRouteIndex(toRoute) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) MainRootDirection.FORWARD else MainRootDirection.BACKWARD
}

private fun mainRootEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition
): EnterTransition = when (mainRootDirection(fromRoute, toRoute)) {
    MainRootDirection.FORWARD -> {
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            initialOffsetX = { it }
        ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            initialOffsetX = { -it }
        ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    null -> fallback
}

private fun mainRootExitTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: ExitTransition
): ExitTransition = when (mainRootDirection(fromRoute, toRoute)) {
    MainRootDirection.FORWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { -it }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { it }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    null -> fallback
}
