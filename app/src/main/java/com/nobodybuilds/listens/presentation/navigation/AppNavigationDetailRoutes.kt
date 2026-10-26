package com.nobodybuilds.listens.presentation.navigation

import androidx.annotation.OptIn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nobodybuilds.listens.presentation.screens.AlbumDetailScreen
import com.nobodybuilds.listens.presentation.screens.ArtistDetailScreen
import com.nobodybuilds.listens.presentation.screens.GenreDetailScreen
import com.nobodybuilds.listens.presentation.screens.MashupScreen
import com.nobodybuilds.listens.presentation.screens.PlaylistDetailScreen
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.presentation.components.ScreenWrapper
import androidx.navigation.NavGraphBuilder

/** Playlist, DJ space, genre, album and artist detail screens. */
@OptIn(UnstableApi::class)
internal fun NavGraphBuilder.libraryDetailDestinations(
    navController: NavHostController,
    playerViewModel: PlayerViewModel
) {
    composable(
        route = Screen.PlaylistDetail.route,
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) { backStackEntry ->
        val playlistId = backStackEntry.arguments?.getString("playlistId")
        val playlistViewModel: PlaylistViewModel = hiltViewModel()
        if (playlistId != null) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    playerViewModel = playerViewModel,
                    playlistViewModel = playlistViewModel,
                    onBackClick = { navController.popBackStack() },
                    onDeletePlayListClick = { navController.popBackStack() },
                    navController = navController
                )
            }
        }
    }

    composable(
        Screen.DJSpace.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            MashupScreen()
        }
    }
    composable(
        route = Screen.GenreDetail.route,
        arguments = listOf(navArgument("genreId") { type = NavType.StringType }),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) { backStackEntry ->
        val genreId = backStackEntry.arguments?.getString("genreId")
        if (genreId != null) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                GenreDetailScreen(
                    navController = navController,
                    genreId = genreId,
                    playerViewModel = playerViewModel
                )
            }
        } else {
            Text(stringResource(R.string.nav_error_genre_id_missing), modifier = Modifier)
        }
    }
    composable(
        route = Screen.AlbumDetail.route,
        arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) { backStackEntry ->
        val albumId = backStackEntry.arguments?.getString("albumId")
        if (albumId != null) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                AlbumDetailScreen(
                    albumId = albumId,
                    navController = navController,
                    playerViewModel = playerViewModel
                )
            }
        }
    }
    composable(
        route = Screen.ArtistDetail.route,
        arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) { backStackEntry ->
        val artistId = backStackEntry.arguments?.getString("artistId")
        if (artistId != null) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                ArtistDetailScreen(
                    artistId = artistId,
                    navController = navController,
                    playerViewModel = playerViewModel
                )
            }
        }
    }
}

/** Dashboards for the connected streaming services. */
@OptIn(UnstableApi::class)
internal fun NavGraphBuilder.serviceDashboardDestinations(
    navController: NavHostController,
    playerViewModel: PlayerViewModel
) {
    composable(
        Screen.NeteaseDashboard.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.netease.dashboard.NeteaseDashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
    composable(
        Screen.QqMusicDashboard.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.qqmusic.dashboard.QqMusicDashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
    composable(
        Screen.YouTubeMusicDashboard.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.ytmusic.dashboard.YouTubeMusicDashboardScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel,
            )
        }
    }
    composable(
        Screen.NavidromeDashboard.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.navidrome.dashboard.NavidromeDashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
    composable(
        Screen.JellyfinDashboard.route,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() },
    ) {
        ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
            com.nobodybuilds.listens.presentation.jellyfin.dashboard.JellyfinDashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
