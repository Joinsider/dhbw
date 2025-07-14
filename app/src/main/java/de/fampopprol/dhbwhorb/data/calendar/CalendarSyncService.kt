/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.calendar

import android.content.Context
import android.util.Log
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import kotlinx.coroutines.flow.first

/**
 * Service to handle automatic calendar synchronization
 */
class CalendarSyncService(private val context: Context) {

    private val calendarSyncManager = CalendarSyncManager(context)
    private val calendarSyncPreferencesManager = CalendarSyncPreferencesManager(context)
    private val timetableCacheManager = TimetableCacheManager(context)

    companion object {
        private const val TAG = "CalendarSyncService"
    }

    /**
     * Sync the current timetable with the selected calendar if sync is enabled
     */
    suspend fun syncTimetableIfEnabled() {
        try {
            // Check if calendar sync is enabled
            val syncEnabled = calendarSyncPreferencesManager.calendarSyncEnabled.first()
            if (!syncEnabled) {
                Log.d(TAG, "Calendar sync is disabled")
                return
            }

            // Check if we have calendar permissions
            if (!calendarSyncManager.hasCalendarPermissions()) {
                Log.w(TAG, "Calendar sync enabled but permissions not granted")
                return
            }

            // Get the selected calendar ID
            val selectedCalendarId = calendarSyncPreferencesManager.selectedCalendarId.first()
            if (selectedCalendarId == -1L) {
                Log.w(TAG, "Calendar sync enabled but no calendar selected")
                return
            }

            // Get cached timetable data for current week
            val today = java.time.LocalDate.now()
            val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val cachedTimetable = timetableCacheManager.loadTimetable(weekStart)

            if (cachedTimetable == null || cachedTimetable.isEmpty()) {
                Log.d(TAG, "No timetable data to sync for current week")
                return
            }

            // Perform the sync
            val success = calendarSyncManager.syncTimetableToCalendar(cachedTimetable, selectedCalendarId)
            if (success) {
                Log.i(TAG, "Timetable successfully synced to calendar")
            } else {
                Log.e(TAG, "Failed to sync timetable to calendar")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during calendar sync", e)
        }
    }

    /**
     * Sync all available timetable data to the calendar
     */
    suspend fun syncAllAvailableTimetableData() {
        try {
            // Check if calendar sync is enabled
            val syncEnabled = calendarSyncPreferencesManager.calendarSyncEnabled.first()
            if (!syncEnabled) {
                Log.d(TAG, "Calendar sync is disabled")
                return
            }

            // Check if we have calendar permissions
            if (!calendarSyncManager.hasCalendarPermissions()) {
                Log.w(TAG, "Calendar sync enabled but permissions not granted")
                return
            }

            // Get the selected calendar ID
            val selectedCalendarId = calendarSyncPreferencesManager.selectedCalendarId.first()
            if (selectedCalendarId == -1L) {
                Log.w(TAG, "Calendar sync enabled but no calendar selected")
                return
            }

            // Collect all available timetable data (current week and surrounding weeks)
            val allTimetableData = mutableListOf<TimetableDay>()
            val today = java.time.LocalDate.now()

            // Sync data for the past 2 weeks, current week, and next 4 weeks
            for (weekOffset in -2..4) {
                val weekStart = today
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .plusWeeks(weekOffset.toLong())

                val weekData = timetableCacheManager.loadTimetable(weekStart)
                if (weekData != null && weekData.isNotEmpty()) {
                    allTimetableData.addAll(weekData)
                    Log.d(TAG, "Added ${weekData.size} days of timetable data for week starting $weekStart")
                }
            }

            if (allTimetableData.isEmpty()) {
                Log.d(TAG, "No timetable data found to sync")
                return
            }

            // Perform the sync with all available data
            val success = calendarSyncManager.syncTimetableToCalendar(allTimetableData, selectedCalendarId)
            if (success) {
                Log.i(TAG, "All available timetable data successfully synced to calendar (${allTimetableData.size} days)")
            } else {
                Log.e(TAG, "Failed to sync all timetable data to calendar")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during full calendar sync", e)
        }
    }

    /**
     * Remove all DHBW events from the selected calendar
     */
    suspend fun removeEventsFromSelectedCalendar() {
        try {
            val selectedCalendarId = calendarSyncPreferencesManager.selectedCalendarId.first()
            if (selectedCalendarId != -1L) {
                val success = calendarSyncManager.removeDHBWEventsFromCalendar(selectedCalendarId)
                if (success) {
                    Log.i(TAG, "DHBW events removed from calendar")
                } else {
                    Log.e(TAG, "Failed to remove DHBW events from calendar")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing events from calendar", e)
        }
    }

    /**
     * Check if calendar sync is properly configured
     */
    suspend fun isCalendarSyncConfigured(): Boolean {
        val syncEnabled = calendarSyncPreferencesManager.calendarSyncEnabled.first()
        val hasPermissions = calendarSyncManager.hasCalendarPermissions()
        val selectedCalendarId = calendarSyncPreferencesManager.selectedCalendarId.first()

        return syncEnabled && hasPermissions && selectedCalendarId != -1L
    }
}
