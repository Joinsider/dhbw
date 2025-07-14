/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import kotlinx.coroutines.launch

// Navigation destinations
sealed class NavigationDestination(
    val route: String,
    val titleResource: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Timetable :
        NavigationDestination("timetable", R.string.timetable, Icons.Default.DateRange)

    object Grades : NavigationDestination("grades", R.string.grades, Icons.Default.Star)

    object NotificationSettings :
        NavigationDestination("notification_settings", R.string.settings, Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    dualisService: DualisService,
    credentialManager: CredentialManager,
    timetableCacheManager: TimetableCacheManager,
    gradesCacheManager: GradesCacheManager,
    notificationScheduler: NotificationScheduler,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    // Use NavigationRail for wider screens (tablets/desktop)
    val useNavigationRail = configuration.screenWidthDp >= 600

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val destinations = listOf(
        NavigationDestination.Timetable,
        NavigationDestination.Grades,
        NavigationDestination.NotificationSettings
    )

    // Handle back gesture for navigation drawer (only when drawer is open)
    BackHandler(enabled = !useNavigationRail && drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Get localized title based on current destination
    val currentTitle = when (currentDestination?.route) {
        NavigationDestination.Timetable.route -> stringResource(R.string.timetable)
        NavigationDestination.Grades.route -> stringResource(R.string.grades)
        NavigationDestination.NotificationSettings.route -> stringResource(R.string.settings)
        else -> stringResource(R.string.timetable)
    }

    if (useNavigationRail) {
        // Use NavigationRail for medium and large screens
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Spacer(modifier = Modifier.weight(1f))

                destinations.forEach { destination ->
                    NavigationRailItem(
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.titleResource)
                            )
                        },
                        label = { Text(stringResource(destination.titleResource)) },
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Logout rail item
                NavigationRailItem(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.logout),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.logout),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    selected = false,
                    onClick = {
                        scope.launch {
                            credentialManager.logout()
                            onLogout()
                        }
                    }
                )
            }

            // Main content area
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentTitle) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                contentWindowInsets = WindowInsets(0.dp)
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = NavigationDestination.Timetable.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(NavigationDestination.Timetable.route) {
                        TimetableScreen(
                            dualisService = dualisService,
                            credentialManager = credentialManager,
                            timetableCacheManager = timetableCacheManager,
                            onLogout = onLogout
                        )
                    }

                    composable(NavigationDestination.Grades.route) {
                        GradesScreen(
                            dualisService = dualisService,
                            credentialManager = credentialManager,
                            gradesCacheManager = gradesCacheManager
                        )
                    }

                    composable(NavigationDestination.NotificationSettings.route) {
                        val context = LocalContext.current
                        val notificationPreferencesManager = remember { NotificationPreferencesManager(context) }
                        NotificationSettingsScreen(
                            dualisService = dualisService,
                            notificationScheduler = notificationScheduler,
                            notificationPreferencesManager = notificationPreferencesManager
                        )
                    }
                }
            }
        }
    } else {
        // Use Modal Navigation Drawer for compact screens
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 16.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Navigation items
                    destinations.forEach { destination ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.titleResource)
                                )
                            },
                            label = { Text(stringResource(destination.titleResource)) },
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch {
                                    drawerState.close()
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Logout item at bottom
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline
                    )

                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = stringResource(R.string.logout)
                            )
                        },
                        label = { Text(stringResource(R.string.logout)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                credentialManager.logout()
                                onLogout()
                                drawerState.close()
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.error,
                            unselectedTextColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        ) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text(currentTitle) },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        drawerState.open()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.open_menu)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                contentWindowInsets = WindowInsets(0.dp)
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = NavigationDestination.Timetable.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(NavigationDestination.Timetable.route) {
                        TimetableScreen(
                            dualisService = dualisService,
                            credentialManager = credentialManager,
                            timetableCacheManager = timetableCacheManager,
                            onLogout = onLogout
                        )
                    }

                    composable(NavigationDestination.Grades.route) {
                        GradesScreen(
                            dualisService = dualisService,
                            credentialManager = credentialManager,
                            gradesCacheManager = gradesCacheManager
                        )
                    }

                    composable(NavigationDestination.NotificationSettings.route) {
                        val context = LocalContext.current
                        val notificationPreferencesManager = remember { NotificationPreferencesManager(context) }
                        NotificationSettingsScreen(
                            dualisService = dualisService,
                            notificationScheduler = notificationScheduler,
                            notificationPreferencesManager = notificationPreferencesManager
                        )
                    }
                }
            }
        }
    }
}
