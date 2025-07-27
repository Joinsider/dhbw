/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.timetableScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.ui.components.CalendarViewMode
import de.fampopprol.dhbwhorb.ui.components.calendar.daily.DailyCalendar
import de.fampopprol.dhbwhorb.ui.components.navigationBars.dayNavigationBar.DayNavigationBar
import de.fampopprol.dhbwhorb.ui.components.navigationBars.weekNavigationBar.WeekNavigationBar
import de.fampopprol.dhbwhorb.ui.components.calendar.weekly.WeeklyCalendar

class TimetableScreenComposer {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TimetableContent(
        timetable: List<TimetableDay>?,
        isFetchingFromApi: Boolean,
        errorMessage: String?,
        isRefreshing: Boolean,
        lastUpdated: String?,
        currentViewMode: CalendarViewMode,
        navigationManager: TimetableNavigationManager,
        pullRefreshState: PullToRefreshState,
        onRefresh: () -> Unit,
        onLogout: () -> Unit,
        onViewModeChanged: (CalendarViewMode) -> Unit,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            PullToRefreshBox(
                state = pullRefreshState,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavigationBarSection(
                        currentViewMode = currentViewMode,
                        navigationManager = navigationManager,
                        isFetchingFromApi = isFetchingFromApi,
                        lastUpdated = lastUpdated
                    )

                    LoadingIndicatorSection(isFetchingFromApi)

                    ErrorSection(errorMessage, onLogout)

                    CalendarSection(
                        timetable = timetable,
                        currentViewMode = currentViewMode,
                        navigationManager = navigationManager,
                        isFetchingFromApi = isFetchingFromApi,
                        errorMessage = errorMessage
                    )
                }
            }
        }
    }

    @Composable
    private fun NavigationBarSection(
        currentViewMode: CalendarViewMode,
        navigationManager: TimetableNavigationManager,
        isFetchingFromApi: Boolean,
        lastUpdated: String?
    ) {
        if (currentViewMode == CalendarViewMode.WEEKLY) {
            WeekNavigationBar(
                modifier = Modifier.fillMaxWidth(),
                currentWeekStart = navigationManager.currentWeekStart,
                onPreviousWeek = { navigationManager.goToPreviousWeek() },
                onNextWeek = { navigationManager.goToNextWeek() },
                onCurrentWeek = { navigationManager.goToCurrentWeek() },
                isLoading = isFetchingFromApi,
                lastUpdated = lastUpdated
            )
        } else {
            DayNavigationBar(
                currentDate = navigationManager.currentDate,
                onPreviousDay = { navigationManager.goToPreviousDay() },
                onNextDay = { navigationManager.goToNextDay() },
                onCurrentDay = { navigationManager.goToCurrentDay() },
                isLoading = isFetchingFromApi,
                lastUpdated = lastUpdated,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun LoadingIndicatorSection(isFetchingFromApi: Boolean) {
        if (isFetchingFromApi) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    @Composable
    private fun ErrorSection(errorMessage: String?, onLogout: () -> Unit) {
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onLogout) {
                        Text("Go to Login")
                    }
                }
            }
        }
    }

    @Composable
    private fun CalendarSection(
        timetable: List<TimetableDay>?,
        currentViewMode: CalendarViewMode,
        navigationManager: TimetableNavigationManager,
        isFetchingFromApi: Boolean,
        errorMessage: String?
    ) {
        timetable?.let { scheduleData ->
            if (currentViewMode == CalendarViewMode.WEEKLY) {
                WeeklyCalendarView(scheduleData, navigationManager)
            } else {
                DailyCalendarView(scheduleData, navigationManager)
            }
        } ?: run {
            EmptyStateView(isFetchingFromApi, errorMessage)
        }
    }

    @Composable
    private fun WeeklyCalendarView(
        timetable: List<TimetableDay>,
        navigationManager: TimetableNavigationManager
    ) {
        WeeklyCalendar(
            modifier = Modifier.fillMaxSize(),
            timetable = timetable,
            startOfWeek = navigationManager.currentWeekStart,
            onPreviousWeek = { navigationManager.goToPreviousWeek() },
            onNextWeek = { navigationManager.goToNextWeek() }
        )
    }

    @Composable
    private fun DailyCalendarView(
        timetable: List<TimetableDay>,
        navigationManager: TimetableNavigationManager
    ) {
        DailyCalendar(
            timetable = timetable,
            currentDate = navigationManager.currentDate,
            onPreviousDay = { navigationManager.goToPreviousDay() },
            onNextDay = { navigationManager.goToNextDay() },
            modifier = Modifier.fillMaxSize()
        )
    }

    @Composable
    private fun EmptyStateView(isFetchingFromApi: Boolean, errorMessage: String?) {
        if (!isFetchingFromApi && errorMessage == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No timetable data available",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
