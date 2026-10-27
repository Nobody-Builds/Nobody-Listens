package com.nobodybuilds.listens.presentation.navigation

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.nobodybuilds.listens.data.preferences.LaunchTab
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.first

@OptIn(UnstableApi::class)
@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    paddingValues: PaddingValues,
    userPreferencesRepository: UserPreferencesRepository,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit
) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = userPreferencesRepository.launchTabFlow
            .first()
            .toRoute()
    }

    startDestination?.let { initialRoute ->
        NavHost(
            navController = navController,
            startDestination = initialRoute
        ) {
            mainTabDestinations(
                navController = navController,
                playerViewModel = playerViewModel,
                paddingValues = paddingValues,
                onSearchBarActiveChange = onSearchBarActiveChange,
                onOpenSidebar = onOpenSidebar
            )
            settingsDestinations(navController = navController, playerViewModel = playerViewModel)
            statsDestination(navController = navController, playerViewModel = playerViewModel, paddingValues = paddingValues)
            libraryDetailDestinations(navController = navController, playerViewModel = playerViewModel)
            settingsToolDestinations(navController = navController, playerViewModel = playerViewModel)
            serviceDashboardDestinations(navController = navController, playerViewModel = playerViewModel)
            featureDestinations(navController = navController, playerViewModel = playerViewModel, paddingValues = paddingValues)
        }
    }
}

private fun String.toRoute(): String = when (this) {
    LaunchTab.SEARCH -> Screen.Search.route
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}
