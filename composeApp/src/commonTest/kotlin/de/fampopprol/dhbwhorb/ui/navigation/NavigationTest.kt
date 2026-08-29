/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A lifecycle that is actually resumed.
 *
 * `NavHost` moves its back-stack entries along the host's lifecycle, and the default owner in a
 * Compose test never leaves INITIALIZED — which makes disposing the composition throw
 * "State must be at least 'CREATED'". Giving it a real one is closer to the app anyway.
 */
private class ResumedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
}

/**
 * The graph itself: switching tabs goes where it says, and the back stack stays shallow.
 *
 * The old `when (currentScreen)` had no stack at all, so the Android back gesture closed the app
 * from any screen.
 *
 * Navigation is driven through `runOnUiThread`, not through a tap on the navigation bar:
 * `NavBackStackEntry` moves its own `LifecycleRegistry`, and that refuses to run off the main
 * thread. A click dispatched from the Compose test's thread trips exactly that check. What a tap
 * adds over this is that the bar is wired to `switchTab` at all, which `RoutesTest` covers from
 * the other side.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationTest {

    @Test
    fun tappingATab_navigatesToIt() = runComposeUiTest {
        lateinit var navController: NavHostController

        setContent {
            val lifecycleOwner = remember { ResumedLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                WithTestKoin {
                    navController = rememberNavController()
                    DhbwNavHost(navController = navController, onLogout = {})
                }
            }
        }
        waitForIdle()

        runOnUiThread { navController.switchTab(BottomNavItem.SETTINGS) }
        waitForIdle()

        assertTrue(
            navController.currentBackStackEntry?.destination?.hasRoute(Route.Settings::class) == true,
            "Expected Settings, got ${navController.currentBackStackEntry?.destination?.route}"
        )
    }

    @Test
    fun walkingTheTabs_doesNotPileUpTheBackStack() = runComposeUiTest {
        lateinit var navController: NavHostController

        setContent {
            val lifecycleOwner = remember { ResumedLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                WithTestKoin {
                    navController = rememberNavController()
                    DhbwNavHost(navController = navController, onLogout = {})
                }
            }
        }
        waitForIdle()

        // Without popUpTo + launchSingleTop this would leave nine entries behind, and pressing
        // back would walk the whole history instead of leaving the app.
        repeat(3) {
            runOnUiThread { navController.switchTab(BottomNavItem.GRADES) }
            waitForIdle()
            runOnUiThread { navController.switchTab(BottomNavItem.SETTINGS) }
            waitForIdle()
            runOnUiThread { navController.switchTab(BottomNavItem.TIMETABLE) }
            waitForIdle()
        }

        assertTrue(
            navController.currentBackStackEntry?.destination?.hasRoute(Route.Timetable::class) == true
        )
        assertTrue(
            navController.previousBackStackEntry == null,
            "Back from the start destination has to leave the app, not replay the tab history"
        )
    }

    @Test
    fun backFromAnotherTab_returnsToTheStartDestination() = runComposeUiTest {
        lateinit var navController: NavHostController

        setContent {
            val lifecycleOwner = remember { ResumedLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                WithTestKoin {
                    navController = rememberNavController()
                    DhbwNavHost(navController = navController, onLogout = {})
                }
            }
        }
        waitForIdle()

        runOnUiThread { navController.switchTab(BottomNavItem.DOCUMENTS) }
        waitForIdle()

        runOnUiThread { navController.popBackStack() }
        waitForIdle()

        assertTrue(
            navController.currentBackStackEntry?.destination?.hasRoute(Route.Timetable::class) == true,
            "Back from a tab returns to the timetable"
        )
    }
}
