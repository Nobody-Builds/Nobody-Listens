package com.nobodybuilds.listens.presentation.navigation

import DelimiterConfigScreen
import com.nobodybuilds.listens.presentation.screens.WordDelimiterConfigScreen
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nobodybuilds.listens.presentation.screens.AccountsScreen
import com.nobodybuilds.listens.presentation.screens.ArtistSettingsScreen
import com.nobodybuilds.listens.presentation.screens.DailyMixScreen
import com.nobodybuilds.listens.presentation.screens.EditTransitionScreen
import com.nobodybuilds.listens.presentation.screens.EasterEggScreen
import com.nobodybuilds.listens.presentation.screens.ExperimentalSettingsScreen
import com.nobodybuilds.listens.presentation.screens.NavBarCornerRadiusScreen
import com.nobodybuilds.listens.presentation.screens.PaletteStyleSettingsScreen
import com.nobodybuilds.listens.presentation.screens.RecentlyPlayedScreen
import com.nobodybuilds.listens.presentation.screens.AboutScreen
import com.nobodybuilds.listens.presentation.screens.SettingsScreen
import com.nobodybuilds.listens.presentation.screens.SettingsCategoryScreen
import com.nobodybuilds.listens.presentation.screens.EqualizerScreen
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.components.ScreenWrapper
import androidx.navigation.NavGraphBuilder

/** Settings, Accounts, settings categories and the screens reached from them. */
@OptIn(UnstableApi::class)
internal fun NavGraphBuilder.settingsDestinations(
    navController: NavHostController,
    playerViewModel: PlayerViewModel
) {
    composable(
        Screen.Settings.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            SettingsScreen(
                navController = navController,
                playerViewModel = playerViewModel,
                onNavigationIconClick = {
                    navController.popBackStack()
                }
            )
        }
    }
    composable(
        Screen.Accounts.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            AccountsScreen(
                onBackClick = { navController.popBackStack() },
                onOpenNeteaseDashboard = {
                    navController.navigateSafely(Screen.NeteaseDashboard.route)
                },
                onOpenQqMusicDashboard = {
                    navController.navigateSafely(Screen.QqMusicDashboard.route)
                },
                onOpenNavidromeDashboard = {
                    navController.navigateSafely(Screen.NavidromeDashboard.route)
                },
                onOpenJellyfinDashboard = {
                    navController.navigateSafely(Screen.JellyfinDashboard.route)
                },
                onOpenYouTubeMusicDashboard = {
                    navController.navigateSafely(Screen.YouTubeMusicDashboard.route)
                },
                onOpenJioSaavnDashboard = {
                    navController.navigateSafely(Screen.JioSaavnDashboard.route)
                }
            )
        }
    }
    composable(
        route = Screen.SettingsCategory.route,
        arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) { backStackEntry ->
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            val categoryId = backStackEntry.arguments?.getString("categoryId")
            if (categoryId != null) {
                SettingsCategoryScreen(
                    categoryId = categoryId,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
    composable(
        Screen.PaletteStyle.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            PaletteStyleSettingsScreen(
                playerViewModel = playerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
    composable(
        Screen.Experimental.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            ExperimentalSettingsScreen(
                navController = navController,
                playerViewModel = playerViewModel,
                onNavigationIconClick = { navController.popBackStack() }
            )
        }
    }
    composable(
        Screen.SmartFeatures.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.smart.SmartSettingsScreen(
                onNavigationIconClick = { navController.popBackStack() }
            )
        }
    }
    composable(
        Screen.DailyMixScreen.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            DailyMixScreen(
                playerViewModel = playerViewModel,
                navController = navController
            )
        }
    }
    composable(
        Screen.RecentlyPlayed.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            RecentlyPlayedScreen(
                playerViewModel = playerViewModel,
                navController = navController
            )
        }
    }
}

/** Settings sub-screens: layout, transitions, About, delimiters, equalizer, device info. */
@OptIn(UnstableApi::class)
internal fun NavGraphBuilder.settingsToolDestinations(
    navController: NavHostController,
    playerViewModel: PlayerViewModel
) {
    composable(
        "nav_bar_corner_radius",
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            NavBarCornerRadiusScreen(navController)
        }
    }
    composable(
        route = Screen.EditTransition.route,
        arguments = listOf(navArgument("playlistId") {
            type = NavType.StringType
            nullable = true
        }),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            EditTransitionScreen(navController = navController)
        }
    }
    composable(
        Screen.About.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            AboutScreen(
                navController = navController,
                viewModel = playerViewModel,
                onNavigationIconClick = { navController.popBackStack() }
            )
        }
    }
    composable(
        Screen.EasterEgg.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            EasterEggScreen(
                viewModel = playerViewModel,
                onNavigationIconClick = { navController.popBackStack() },
            )
        }
    }
    composable(
        Screen.ArtistSettings.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            ArtistSettingsScreen(navController = navController)
        }
    }
    composable(
        Screen.DelimiterConfig.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            DelimiterConfigScreen(navController = navController)
        }
    }
    composable(
        Screen.WordDelimiterConfig.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            WordDelimiterConfigScreen(navController = navController)
        }
    }
    composable(
        Screen.Equalizer.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            EqualizerScreen(
                navController = navController,
                playerViewModel = playerViewModel
            )
        }
    }
    composable(
        Screen.DeviceCapabilities.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.screens.DeviceCapabilitiesScreen(
                navController = navController,
                playerViewModel = playerViewModel
            )
        }
    }
}
