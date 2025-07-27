/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.components.CalendarViewMode
import de.fampopprol.dhbwhorb.ui.screen.timetableScreen.TimetableNavigationManager
import de.fampopprol.dhbwhorb.ui.screen.timetableScreen.TimetableScreenComposer
import de.fampopprol.dhbwhorb.ui.screen.timetableScreen.TimetableViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    dualisService: DualisService,
    credentialManager: CredentialManager,
    timetableCacheManager: TimetableCacheManager,
    onLogout: () -> Unit,
    currentViewMode: CalendarViewMode,
    onViewModeChanged: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullToRefreshState()

    // Initialize the separated components
    val viewModel = remember {
        TimetableViewModel(dualisService, credentialManager, timetableCacheManager)
    }
    val navigationManager = remember { TimetableNavigationManager(viewModel) }
    val screenComposer = remember { TimetableScreenComposer() }

    // Load cached data immediately on screen initialization
    LaunchedEffect(Unit) {
        viewModel.loadCachedTimetable(navigationManager.currentWeekStart)
    }

    // Initial data loading and re-authentication logic
    LaunchedEffect(credentialManager, dualisService) {
        viewModel.authenticateAndFetch(navigationManager.currentWeekStart, onLogout)
    }

    screenComposer.TimetableContent(
        timetable = viewModel.timetable,
        isFetchingFromApi = viewModel.isFetchingFromApi,
        errorMessage = viewModel.errorMessage,
        isRefreshing = viewModel.isRefreshing,
        lastUpdated = viewModel.lastUpdated,
        currentViewMode = currentViewMode,
        navigationManager = navigationManager,
        pullRefreshState = pullRefreshState,
        onRefresh = {
            viewModel.setRefreshingState(true)
            navigationManager.refreshCurrentData(currentViewMode)
        },
        onLogout = onLogout,
        onViewModeChanged = onViewModeChanged,
        modifier = modifier
    )
}
