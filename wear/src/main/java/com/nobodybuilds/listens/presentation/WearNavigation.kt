package com.nobodybuilds.listens.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.nobodybuilds.listens.presentation.screens.BrowseScreen
import com.nobodybuilds.listens.presentation.screens.DownloadsScreen
import com.nobodybuilds.listens.presentation.screens.LibraryListScreen
import com.nobodybuilds.listens.presentation.screens.MoreScreen
import com.nobodybuilds.listens.presentation.screens.OutputScreen
import com.nobodybuilds.listens.presentation.screens.PlayerScreen
import com.nobodybuilds.listens.presentation.screens.QueueScreen
import com.nobodybuilds.listens.presentation.screens.SongListScreen
import com.nobodybuilds.listens.presentation.screens.TimerScreen
import com.nobodybuilds.listens.presentation.screens.VolumeScreen
import com.nobodybuilds.listens.presentation.screens.WatchPlaylistScreen
import com.nobodybuilds.listens.presentation.screens.WatchPlaylistsScreen
import com.nobodybuilds.listens.presentation.screens.WatchSearchScreen
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Navigation host for the Wear OS app.
 * Routes:
 * - PLAYER: Main player controls (start destination)
 * - VOLUME: Volume control
 * - BROWSE: Library category picker
 * - LIBRARY_LIST: List of albums/artists/playlists
 * - SONG_LIST: Songs within a context (album/artist/playlist/favorites/all)
 */
object WearScreens {
    const val PLAYER = "player"
    const val VOLUME = "volume"
    const val OUTPUT = "output"
    const val MORE = "more"
    const val QUEUE = "queue"
    const val TIMER = "timer"
    const val BROWSE = "browse"
    const val DOWNLOADS = "downloads"
    const val SEARCH = "search"
    const val WATCH_PLAYLISTS = "watch_playlists"
    const val WATCH_PLAYLIST = "watch_playlist/{playlistId}/{title}"

    fun watchPlaylistRoute(playlistId: String, title: String): String {
        return "watch_playlist/${URLEncoder.encode(playlistId, "UTF-8")}/${URLEncoder.encode(title, "UTF-8")}"
    }
    const val LIBRARY_LIST = "library_list/{browseType}/{title}"
    const val SONG_LIST = "song_list/{browseType}/{contextId}/{title}"

    fun libraryListRoute(browseType: String, title: String): String {
        return "library_list/$browseType/${URLEncoder.encode(title, "UTF-8")}"
    }

    fun songListRoute(browseType: String, contextId: String, title: String): String {
        return "song_list/$browseType/$contextId/${URLEncoder.encode(title, "UTF-8")}"
    }
}

@Composable
fun WearNavigation() {
    val navController = rememberSwipeDismissableNavController()
    val navigateToBrowseCategory: (browseType: String, title: String) -> Unit = { browseType, title ->
        when (browseType) {
            "downloads" -> {
                navController.navigate(WearScreens.DOWNLOADS)
            }
            "search" -> {
                navController.navigate(WearScreens.SEARCH)
            }
            "watch_playlists" -> {
                navController.navigate(WearScreens.WATCH_PLAYLISTS)
            }
            "favorites", "all_songs" -> {
                navController.navigate(
                    WearScreens.songListRoute(browseType, "none", title)
                )
            }
            else -> {
                navController.navigate(
                    WearScreens.libraryListRoute(browseType, title)
                )
            }
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearScreens.PLAYER,
    ) {
        composable(WearScreens.PLAYER) {
            PlayerScreen(
                onBrowseCategoryClick = navigateToBrowseCategory,
                onVolumeClick = {
                    navController.navigate(WearScreens.VOLUME) {
                        launchSingleTop = true
                    }
                },
                onOutputClick = {
                    navController.navigate(WearScreens.OUTPUT) {
                        launchSingleTop = true
                    }
                },
                onMoreClick = {
                    navController.navigate(WearScreens.MORE) {
                        launchSingleTop = true
                    }
                },
                onQueueClick = {
                    navController.navigate(WearScreens.QUEUE) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(WearScreens.VOLUME) {
            VolumeScreen()
        }

        composable(WearScreens.OUTPUT) {
            OutputScreen()
        }

        composable(WearScreens.MORE) {
            MoreScreen(
                onQueueClick = {
                    navController.navigate(WearScreens.QUEUE) {
                        launchSingleTop = true
                    }
                },
                onSettingsClick = {
                    navController.navigate(WearScreens.OUTPUT) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(WearScreens.QUEUE) {
            QueueScreen(
                onTimerClick = {
                    navController.navigate(WearScreens.TIMER) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(WearScreens.TIMER) {
            TimerScreen()
        }

        composable(WearScreens.DOWNLOADS) {
            DownloadsScreen()
        }

        composable(WearScreens.SEARCH) {
            WatchSearchScreen()
        }

        composable(WearScreens.WATCH_PLAYLISTS) {
            WatchPlaylistsScreen(
                onPlaylistClick = { playlistId, name ->
                    navController.navigate(WearScreens.watchPlaylistRoute(playlistId, name))
                },
            )
        }

        composable(
            route = WearScreens.WATCH_PLAYLIST,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            WatchPlaylistScreen(
                playlistId = URLDecoder.decode(backStackEntry.arguments?.getString("playlistId").orEmpty(), "UTF-8"),
                name = URLDecoder.decode(backStackEntry.arguments?.getString("title").orEmpty(), "UTF-8"),
                onRemoved = { navController.popBackStack() },
            )
        }

        composable(WearScreens.BROWSE) {
            BrowseScreen(
                onCategoryClick = navigateToBrowseCategory,
            )
        }

        composable(
            route = WearScreens.LIBRARY_LIST,
            arguments = listOf(
                navArgument("browseType") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val browseType = backStackEntry.arguments?.getString("browseType") ?: ""
            val title = URLDecoder.decode(
                backStackEntry.arguments?.getString("title") ?: "", "UTF-8"
            )
            LibraryListScreen(
                browseType = browseType,
                title = title,
                onItemClick = { item, subBrowseType, itemTitle ->
                    navController.navigate(
                        WearScreens.songListRoute(subBrowseType, item.id, itemTitle)
                    )
                },
            )
        }

        composable(
            route = WearScreens.SONG_LIST,
            arguments = listOf(
                navArgument("browseType") { type = NavType.StringType },
                navArgument("contextId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val browseType = backStackEntry.arguments?.getString("browseType") ?: ""
            val contextId = backStackEntry.arguments?.getString("contextId")
            val title = URLDecoder.decode(
                backStackEntry.arguments?.getString("title") ?: "", "UTF-8"
            )
            SongListScreen(
                browseType = browseType,
                contextId = contextId,
                title = title,
            )
        }
    }
}
