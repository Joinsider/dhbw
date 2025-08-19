/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Manages the schedule change monitoring lifecycle based on user preferences and permissions
 */
class ScheduleChangeManager(private val context: Context) {

    companion object {
        private const val TAG = "ScheduleChangeManager"
    }

    private val preferencesManager = NotificationPreferencesManager(context)
    private val scheduler = ScheduleChangeScheduler(context)
    private val permissionHelper = NotificationPermissionHelper(context)

    /**
     * Initializes the schedule change monitoring and sets up preference observation
     */
    fun initialize() {
        Log.d(TAG, "Initializing schedule change manager")

        // Check current state and start if appropriate
        evaluateAndUpdateScheduler()

        // Observe preference changes
        CoroutineScope(Dispatchers.IO).launch {
            combine(
                preferencesManager.notificationsEnabled,
                preferencesManager.timetableNotificationsEnabled
            ) { generalEnabled, timetableEnabled ->
                Pair(generalEnabled, timetableEnabled)
            }.collect { (generalEnabled, timetableEnabled) ->
                Log.d(TAG, "Preferences changed - General: $generalEnabled, Timetable: $timetableEnabled")
                evaluateAndUpdateScheduler()
            }
        }
    }

    /**
     * Evaluates current conditions and starts/stops the scheduler accordingly
     */
    private fun evaluateAndUpdateScheduler() {
        CoroutineScope(Dispatchers.IO).launch {
            val hasPermission = permissionHelper.hasNotificationPermission()
            val generalEnabled = preferencesManager.getNotificationsEnabledBlocking()
            val timetableEnabled = preferencesManager.getTimetableNotificationsEnabledBlocking()

            val shouldBeRunning = hasPermission && generalEnabled && timetableEnabled
            val isCurrentlyRunning = scheduler.isPeriodicCheckingEnabled()

            Log.d(TAG, "Evaluation - Permission: $hasPermission, General: $generalEnabled, Timetable: $timetableEnabled")
            Log.d(TAG, "Should be running: $shouldBeRunning, Currently running: $isCurrentlyRunning")

            when {
                shouldBeRunning && !isCurrentlyRunning -> {
                    Log.d(TAG, "Starting schedule change monitoring")
                    scheduler.startPeriodicChecking()
                }
                !shouldBeRunning && isCurrentlyRunning -> {
                    Log.d(TAG, "Stopping schedule change monitoring")
                    scheduler.stopPeriodicChecking()
                }
                else -> {
                    Log.d(TAG, "No action needed for schedule monitoring")
                }
            }
        }
    }

    /**
     * Called when notification permission is granted to re-evaluate scheduler state
     */
    fun onPermissionGranted() {
        Log.d(TAG, "Notification permission granted, re-evaluating scheduler")
        evaluateAndUpdateScheduler()
    }

    /**
     * Called when notification permission is denied to stop scheduler
     */
    fun onPermissionDenied() {
        Log.d(TAG, "Notification permission denied, stopping scheduler")
        scheduler.stopPeriodicChecking()
    }

    /**
     * Triggers an immediate check if conditions are met
     */
    fun triggerImmediateCheck() {
        CoroutineScope(Dispatchers.IO).launch {
            val hasPermission = permissionHelper.hasNotificationPermission()
            val generalEnabled = preferencesManager.getNotificationsEnabledBlocking()
            val timetableEnabled = preferencesManager.getTimetableNotificationsEnabledBlocking()

            if (hasPermission && generalEnabled && timetableEnabled) {
                Log.d(TAG, "Triggering immediate schedule check")
                scheduler.triggerImmediateCheck()
            } else {
                Log.d(TAG, "Cannot trigger immediate check - conditions not met")
            }
        }
    }

    /**
     * Gets the current status of the schedule monitoring
     */
    fun isMonitoringActive(): Boolean {
        return scheduler.isPeriodicCheckingEnabled()
    }
}
