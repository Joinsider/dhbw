/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.timetableScreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.fampopprol.dhbwhorb.ui.components.CalendarViewMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class TimetableNavigationManager(
    private val viewModel: TimetableViewModel
) {
    var currentDate by mutableStateOf(LocalDate.now())
        private set

    var currentWeekStart by mutableStateOf(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
        private set

    // Function to handle week change (navigate to a different week)
    fun changeWeek(weekStart: LocalDate) {
        currentWeekStart = weekStart

        // First try to load from cache immediately
        val foundInCache = viewModel.loadCachedTimetable(weekStart)

        // Then fetch from API to ensure data is fresh
        viewModel.fetchTimetableFromApi(weekStart, isForced = !foundInCache)
    }

    // Week navigation functions
    fun goToPreviousWeek() {
        changeWeek(currentWeekStart.minusWeeks(1))
    }

    fun goToNextWeek() {
        changeWeek(currentWeekStart.plusWeeks(1))
    }

    fun goToCurrentWeek() {
        changeWeek(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }

    // Daily view navigation functions
    fun goToPreviousDay() {
        val newDate = currentDate.minusDays(1)
        // Skip weekends - only show Monday to Friday
        currentDate = if (newDate.dayOfWeek == DayOfWeek.SATURDAY) {
            newDate.minusDays(1) // Go to Friday
        } else if (newDate.dayOfWeek == DayOfWeek.SUNDAY) {
            newDate.minusDays(2) // Go to Friday
        } else {
            newDate
        }

        // Ensure we have data for the week containing this day
        val weekStart = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (weekStart != currentWeekStart) {
            changeWeek(weekStart)
        }
    }

    fun goToNextDay() {
        val newDate = currentDate.plusDays(1)
        // Skip weekends - only show Monday to Friday
        currentDate = if (newDate.dayOfWeek == DayOfWeek.SATURDAY) {
            newDate.plusDays(2) // Go to Monday
        } else if (newDate.dayOfWeek == DayOfWeek.SUNDAY) {
            newDate.plusDays(1) // Go to Monday
        } else {
            newDate
        }

        // Ensure we have data for the week containing this day
        val weekStart = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (weekStart != currentWeekStart) {
            changeWeek(weekStart)
        }
    }

    fun goToCurrentDay() {
        currentDate = LocalDate.now()
        val weekStart = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (weekStart != currentWeekStart) {
            changeWeek(weekStart)
        }
    }

    // Function to refresh current data based on view mode
    fun refreshCurrentData(currentViewMode: CalendarViewMode) {
        android.util.Log.d("TimetableNavigationManager", "Pull-to-refresh triggered")
        viewModel.setRefreshing(true)
        if (currentViewMode == CalendarViewMode.WEEKLY) {
            viewModel.fetchTimetableFromApi(currentWeekStart, isForced = true)
        } else {
            val weekStart = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            viewModel.fetchTimetableFromApi(weekStart, isForced = true)
        }
    }
}
