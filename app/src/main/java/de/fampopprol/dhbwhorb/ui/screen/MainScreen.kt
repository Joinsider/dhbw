/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.components.CalendarViewMode
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
    currentTimetableScreenViewMode: CalendarViewMode,
    onTimetableScreenViewModeChanged: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
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

    // Get localized title based on current destination
    val currentTitle = when (currentDestination?.route) {
        NavigationDestination.Timetable.route -> stringResource(R.string.timetable)
        NavigationDestination.Grades.route -> stringResource(R.string.grades)
        NavigationDestination.NotificationSettings.route -> stringResource(R.string.settings)
        else -> stringResource(R.string.timetable)
    }

    var currentTimetableScreenViewMode by remember { mutableStateOf(CalendarViewMode.WEEKLY) }

    val onTimetableScreenViewModeChanged: (CalendarViewMode) -> Unit = { newMode ->
        currentTimetableScreenViewMode = newMode
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
                        actions = {
                            if (currentDestination?.route == NavigationDestination.Timetable.route) {
                                IconButton(onClick = {
                                    currentTimetableScreenViewMode = if (currentTimetableScreenViewMode == CalendarViewMode.WEEKLY) {
                                        CalendarViewMode.DAILY
                                    } else {
                                        CalendarViewMode.WEEKLY
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (currentTimetableScreenViewMode == CalendarViewMode.WEEKLY) Icons.Default.CalendarViewDay else Icons.Default.CalendarViewWeek,
                                        contentDescription = if (currentTimetableScreenViewMode == CalendarViewMode.WEEKLY) stringResource(R.string.weekly_view) else stringResource(R.string.daily_view)
                                    )
                                }
                            }
                        },
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
                            onLogout = onLogout,
                            currentViewMode = currentTimetableScreenViewMode,
                            onViewModeChanged = onTimetableScreenViewModeChanged
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
                        val notificationPreferencesManager =
                            remember { NotificationPreferencesManager(context) }
                        NotificationSettingsScreen(
                            dualisService = dualisService,
                            notificationScheduler = notificationScheduler,
                            notificationPreferencesManager = notificationPreferencesManager,
                            credentialManager = credentialManager,
                            onLogout = onLogout
                        )
                    }
                }
            }
        }
    } else {
        // Use Bottom Navigation Bar for compact screens
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(currentTitle) },
                    actions = {
                        if (currentDestination?.route == NavigationDestination.Timetable.route) {
                            IconButton(onClick = {
                                currentTimetableScreenViewMode = if (currentTimetableScreenViewMode == CalendarViewMode.WEEKLY) {
                                    CalendarViewMode.DAILY
                                } else {
                                    CalendarViewMode.WEEKLY
                                }
                            }) {
                                Icon(
                                    imageVector = if (currentTimetableScreenViewMode == CalendarViewMode.WEEKLY) Icons.Default.CalendarViewDay else Icons.Default.CalendarViewWeek,
                                    contentDescription = if (currentTimetableScreenViewMode == CalendarViewMode.WEEKLY) stringResource(R.string.weekly_view) else stringResource(R.string.daily_view)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
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
                }
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
                        onLogout = onLogout,
                        currentViewMode = currentTimetableScreenViewMode,
                        onViewModeChanged = onTimetableScreenViewModeChanged
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
                    val notificationPreferencesManager =
                        remember { NotificationPreferencesManager(context) }
                    NotificationSettingsScreen(
                        dualisService = dualisService,
                        notificationScheduler = notificationScheduler,
                        notificationPreferencesManager = notificationPreferencesManager,
                        credentialManager = credentialManager,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}