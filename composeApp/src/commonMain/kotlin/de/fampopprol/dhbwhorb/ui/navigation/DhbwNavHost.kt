/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import de.fampopprol.dhbwhorb.ui.pages.DocumentsPage
import de.fampopprol.dhbwhorb.ui.pages.GradesPage
import de.fampopprol.dhbwhorb.ui.pages.SettingsPage
import de.fampopprol.dhbwhorb.ui.pages.TimetablePage

/**
 * The logged-in part of the app.
 *
 * Replaces the `when (currentScreen)` in the root composable. What that could not do, and this
 * does: a real back stack, so the Android back gesture returns to the previous tab instead of
 * closing the app; arguments in the route; and deep links that work on a cold start, because the
 * graph — not a composable that has already run — decides where the app opens.
 */
@Composable
fun DhbwNavHost(
    navController: NavHostController = rememberNavController(),
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Route.Timetable(),
        modifier = modifier
    ) {
        composable<Route.Timetable>(
            deepLinks = listOf(navDeepLink<Route.Timetable>(basePath = "$DEEP_LINK_SCHEME://timetable"))
        ) { entry ->
            TimetablePage(
                initialWeek = entry.toRoute<Route.Timetable>().week,
                onNavigate = navController::switchTab,
                modifier = modifier
            )
        }

        composable<Route.Grades>(
            deepLinks = listOf(navDeepLink<Route.Grades>(basePath = "$DEEP_LINK_SCHEME://grades"))
        ) {
            GradesPage(onNavigate = navController::switchTab, modifier = modifier)
        }

        composable<Route.Documents>(
            deepLinks = listOf(navDeepLink<Route.Documents>(basePath = "$DEEP_LINK_SCHEME://documents"))
        ) {
            DocumentsPage(onNavigate = navController::switchTab, modifier = modifier)
        }

        composable<Route.Settings>(
            deepLinks = listOf(navDeepLink<Route.Settings>(basePath = "$DEEP_LINK_SCHEME://settings"))
        ) {
            SettingsPage(
                onNavigate = navController::switchTab,
                onLogout = onLogout,
                modifier = modifier
            )
        }
    }
}

/**
 * Move to another tab.
 *
 * `launchSingleTop` plus popping back to the start keeps the stack from growing one entry per tab
 * tap — otherwise pressing back after a few taps walks the whole history instead of leaving.
 * `saveState`/`restoreState` keep each tab's scroll position and pager page across the switch.
 */
fun NavController.switchTab(item: BottomNavItem) {
    navigate(item.toRoute()) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Which tab is currently showing, for the navigation bar's highlight. */
@Composable
fun NavController.currentNavItem(): BottomNavItem {
    val entry by currentBackStackEntryAsState()
    val destination = entry?.destination ?: return BottomNavItem.TIMETABLE

    return BottomNavItem.entries.firstOrNull { item ->
        when (item) {
            BottomNavItem.TIMETABLE -> destination.hasRoute(Route.Timetable::class)
            BottomNavItem.GRADES -> destination.hasRoute(Route.Grades::class)
            BottomNavItem.DOCUMENTS -> destination.hasRoute(Route.Documents::class)
            BottomNavItem.SETTINGS -> destination.hasRoute(Route.Settings::class)
        }
    } ?: BottomNavItem.TIMETABLE
}
