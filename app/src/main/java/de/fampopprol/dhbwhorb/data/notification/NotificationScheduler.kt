/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.permissions.PermissionManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val context: Context) {

    companion object {
        private const val TAG = "NotificationScheduler"
    }

    private val classReminderScheduler = ClassReminderScheduler(context)
    private val timetableCacheManager = TimetableCacheManager(context)
    private val permissionManager = PermissionManager(context)

    /**
     * Schedule periodic notifications with optimized constraints for battery efficiency
     */
    fun schedulePeriodicNotifications() {
        if (!permissionManager.hasNotificationPermission()) {
            Log.w(TAG, "Cannot schedule notifications - notification permission not granted")
            return
        }

        // Use more battery-friendly constraints
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true) // Don't run when battery is low
            .setRequiresDeviceIdle(false) // Allow running when device is in use
            .build()

        // Use flexible timing that works with Doze mode
        val periodicWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            30, TimeUnit.MINUTES, // Repeat interval
            15, TimeUnit.MINUTES  // Flex interval - allows system to optimize timing
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )

        Log.d(TAG, "Scheduled battery-optimized periodic notification checks every 30 minutes (±15 min flex)")

        // Also schedule class reminders when starting notifications
        scheduleClassReminders()
    }

    /**
     * Cancel periodic notifications
     */
    fun cancelPeriodicNotifications() {
        WorkManager.getInstance(context).cancelUniqueWork(NotificationWorker.WORK_NAME)
        Log.d(TAG, "Cancelled periodic notification checks")

        // Also cancel class reminders
        cancelClassReminders()
    }

    /**
     * Schedule class reminder notifications based on current preferences and cached timetable
     */
    fun scheduleClassReminders() {
        // Use a background scope to handle the suspend functions
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val preferencesManager = NotificationPreferencesManager(context)
                val notificationsEnabled = preferencesManager.getNotificationsEnabledBlocking()
                val classRemindersEnabled = preferencesManager.getClassReminderNotificationsEnabledBlocking()

                if (!notificationsEnabled || !classRemindersEnabled) {
                    Log.d(TAG, "Class reminders are disabled")
                    cancelClassReminders()
                    return@launch
                }

                val reminderMinutes = preferencesManager.getClassReminderTimeMinutesBlocking()

                // Get cached timetable data for the next few weeks
                val timetableData = mutableMapOf<java.time.LocalDate, List<de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay>>()
                val today = java.time.LocalDate.now()
                val currentWeekStart = today.with(java.time.DayOfWeek.MONDAY)

                // Load timetable data for current and next 3 weeks
                for (weekOffset in 0..3) {
                    val weekStart = currentWeekStart.plusWeeks(weekOffset.toLong())
                    val weekData = timetableCacheManager.loadTimetable(weekStart)
                    if (weekData != null) {
                        timetableData[weekStart] = weekData
                    }
                }

                if (timetableData.isNotEmpty()) {
                    classReminderScheduler.scheduleClassReminders(timetableData, reminderMinutes)
                    Log.d(TAG, "Scheduled class reminders for ${timetableData.size} weeks with $reminderMinutes minutes notice")
                } else {
                    Log.d(TAG, "No timetable data available for class reminders")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling class reminders", e)
            }
        }
    }

    /**
     * Cancel all class reminder notifications
     */
    fun cancelClassReminders() {
        classReminderScheduler.cancelAllClassReminders()
        Log.d(TAG, "Cancelled all class reminders")
    }

    /**
     * Check if periodic notifications are scheduled
     */
    fun areNotificationsScheduled(): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(NotificationWorker.WORK_NAME)

        return try {
            val workInfoList = workInfos.get()
            workInfoList.any { !it.state.isFinished }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification schedule status", e)
            false
        }
    }
}
