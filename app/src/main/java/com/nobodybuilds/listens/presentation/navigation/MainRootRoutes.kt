package com.nobodybuilds.listens.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = mainRootRouteIndex(route) != null

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Library.route -> 1
    Screen.Search.route -> 2
    Screen.Stats.route -> 3
    else -> null
}
