/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Schedules periodic checks for schedule change notifications
 */
class ScheduleChangeScheduler(private val context: Context) {

    companion object {
        private const val TAG = "ScheduleChangeScheduler"
        private const val WORK_NAME = "schedule_change_check"
        private const val CHECK_INTERVAL_HOURS = 1L
    }

    private val workManager = WorkManager.getInstance(context)
    private val preferencesManager = NotificationPreferencesManager(context)
    private val permissionHelper = NotificationPermissionHelper(context)

    /**
     * Starts periodic checking for schedule changes every hour
     * Only starts if timetable notifications are enabled and permissions are granted
     */
    fun startPeriodicChecking() {
        Log.d(TAG, "Checking if periodic schedule change checking should be started")

        // Check permissions first
        if (!permissionHelper.hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted, cannot start schedule checking")
            return
        }

        // Check if notifications and timetable notifications are enabled
        runBlocking {
            val notificationsEnabled = preferencesManager.getNotificationsEnabledBlocking()
            val timetableNotificationsEnabled = preferencesManager.getTimetableNotificationsEnabledBlocking()

            if (!notificationsEnabled) {
                Log.d(TAG, "General notifications are disabled, not starting schedule checking")
                return@runBlocking
            }

            if (!timetableNotificationsEnabled) {
                Log.d(TAG, "Timetable notifications are disabled, not starting schedule checking")
                return@runBlocking
            }

            Log.d(TAG, "Starting periodic schedule change checking (every $CHECK_INTERVAL_HOURS hour(s))")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<ScheduleChangeWorker>(
                CHECK_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    15, TimeUnit.MINUTES
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )

            Log.d(TAG, "Periodic schedule change checking scheduled successfully")
        }
    }

    /**
     * Stops periodic checking for schedule changes
     */
    fun stopPeriodicChecking() {
        Log.d(TAG, "Stopping periodic schedule change checking")
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /**
     * Triggers an immediate check for schedule changes
     */
    fun triggerImmediateCheck() {
        Log.d(TAG, "Triggering immediate schedule change check")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateWorkRequest = OneTimeWorkRequestBuilder<ScheduleChangeWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(immediateWorkRequest)
    }

    /**
     * Checks if periodic checking is currently enabled
     */
    fun isPeriodicCheckingEnabled(): Boolean {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
            workInfos.any { workInfo ->
                workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking periodic work status", e)
            false
        }
    }

    /**
     * Gets the status of the last schedule change check
     */
    fun getLastCheckStatus(): WorkInfo.State? {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
            workInfos.lastOrNull()?.state
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last check status", e)
            null
        }
    }
}
