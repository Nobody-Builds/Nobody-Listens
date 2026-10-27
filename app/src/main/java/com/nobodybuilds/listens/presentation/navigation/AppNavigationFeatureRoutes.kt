package com.nobodybuilds.listens.presentation.navigation

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.components.ScreenWrapper
import androidx.navigation.NavGraphBuilder

/** Library health, covers, Lofi, Focus, Sleep, Recap, Party, Garden and Taste match. */
@OptIn(UnstableApi::class)
internal fun NavGraphBuilder.featureDestinations(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    paddingValues: PaddingValues
) {
    composable(
        Screen.LibraryHealth.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.health.LibraryHealthScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                bottomInset = paddingValues.calculateBottomPadding(),
                onMakeCovers = { navController.navigate(Screen.CoverStudio.createRoute(batch = true)) }
            )
        }
    }
    composable(
        Screen.CoverStudio.route,
        arguments = listOf(navArgument("mode") { type = NavType.StringType; nullable = true; defaultValue = null }),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) { backStackEntry ->
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.covers.CoverStudioScreen(
                onBack = { navController.popBackStack() },
                mode = backStackEntry.arguments?.getString("mode"),
                bottomInset = paddingValues.calculateBottomPadding()
            )
        }
    }
    composable(
        Screen.Lofi.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.lofi.LofiScreen(
                onBack = { navController.popBackStack() },
                bottomInset = paddingValues.calculateBottomPadding()
            )
        }
    }
    composable(
        Screen.Focus.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.focus.FocusScreen(
                onBack = { navController.popBackStack() },
                onPlaySongs = { songs ->
                    songs.firstOrNull()?.let { playerViewModel.playSongs(songs, it, "Focus") }
                },
                bottomInset = paddingValues.calculateBottomPadding()
            )
        }
    }
    composable(
        Screen.Sleep.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.sleep.SleepScreen(
                onBack = { navController.popBackStack() },
                onPlaySongs = { songs ->
                    songs.firstOrNull()?.let { playerViewModel.playSongs(songs, it, "Sleep") }
                },
                bottomInset = paddingValues.calculateBottomPadding()
            )
        }
    }
    composable(
        route = Screen.Recap.route,
        arguments = listOf(navArgument("period") { type = NavType.StringType }),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) { backStackEntry ->
        val period = backStackEntry.arguments?.getString("period").orEmpty()
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.recap.RecapScreen(
                period = com.nobodybuilds.listens.presentation.recap.RecapPeriod.fromRoute(period),
                onClose = { navController.popBackStack() }
            )
        }
    }
    composable(
        Screen.Party.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.party.PartyScreen(
                onBack = { navController.popBackStack() },
                bottomInset = paddingValues.calculateBottomPadding()
            )
        }
    }
    composable(
        Screen.Garden.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.garden.GardenScreen(
                onBack = { navController.popBackStack() },
                bottomInset = paddingValues.calculateBottomPadding()
            )
        }
    }
    composable(
        Screen.TasteMatch.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.taste.TasteMatchScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel,
                bottomInset = paddingValues.calculateBottomPadding()
            )
        }
    }
}
