/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.fampopprol.dhbwhorb.data.dualis.models.NotificationType
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically checks for schedule change notifications from Dualis
 */
class ScheduleChangeWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "ScheduleChangeWorker"
        private const val PREFS_NAME = "schedule_notifications"
        private const val KEY_LAST_NOTIFICATION_IDS = "last_notification_ids"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val WORK_TIMEOUT_SECONDS = 30L
    }

    private val dualisService = DualisService()
    private val notificationManager = DHBWNotificationManager(applicationContext)
    private val preferences: SharedPreferences =
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val preferencesManager = NotificationPreferencesManager(applicationContext)
    private val permissionHelper = NotificationPermissionHelper(applicationContext)

    override fun doWork(): Result {
        Log.d(TAG, "=== STARTING SCHEDULE CHANGE CHECK ===")

        // Check permissions first
        if (!permissionHelper.hasNotificationPermission()) {
            Log.d(TAG, "Notification permission not granted, skipping check")
            return Result.success()
        }

        // Check if notifications are enabled
        return try {
            val notificationsEnabled = runBlocking { preferencesManager.getNotificationsEnabledBlocking() }
            val timetableNotificationsEnabled = runBlocking { preferencesManager.getTimetableNotificationsEnabledBlocking() }

            if (!notificationsEnabled || !timetableNotificationsEnabled) {
                Log.d(TAG, "Timetable notifications are disabled, skipping check")
                return Result.success()
            }

            // Check if user is authenticated
            if (!dualisService.isAuthenticated()) {
                Log.d(TAG, "User not authenticated, skipping schedule change check")
                return Result.success()
            }

            val result = checkForScheduleChangeNotifications()
            Log.d(TAG, "=== SCHEDULE CHANGE CHECK COMPLETED ===")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error during schedule change check", e)
            Result.retry()
        }
    }

    private fun checkForScheduleChangeNotifications(): Result {
        val latch = CountDownLatch(1)
        var workerResult = Result.success()

        dualisService.getUnreadNotifications { notificationList ->
            try {
                if (notificationList != null) {
                    Log.d(TAG, "Received ${notificationList.totalUnreadCount} unread notifications")

                    // Filter for schedule-related notifications
                    val scheduleNotifications = notificationList.unreadNotifications.filter { notification ->
                        notification.type == NotificationType.SCHEDULE_CHANGE ||
                        notification.type == NotificationType.SCHEDULE_SET
                    }

                    Log.d(TAG, "Found ${scheduleNotifications.size} schedule-related notifications")

                    if (scheduleNotifications.isNotEmpty()) {
                        // Check if these are new notifications we haven't seen before
                        val newNotifications = filterNewNotifications(scheduleNotifications)

                        if (newNotifications.isNotEmpty()) {
                            Log.d(TAG, "Found ${newNotifications.size} new schedule notifications")

                            // Show notification to user
                            showScheduleChangeNotification(newNotifications)

                            // Update our stored notification IDs
                            updateStoredNotificationIds(scheduleNotifications)

                            workerResult = Result.success()
                        } else {
                            Log.d(TAG, "No new schedule notifications since last check")
                            workerResult = Result.success()
                        }
                    } else {
                        Log.d(TAG, "No schedule-related notifications found")
                        workerResult = Result.success()
                    }
                } else {
                    Log.w(TAG, "Failed to fetch notifications from Dualis")
                    workerResult = Result.retry()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notifications", e)
                workerResult = Result.failure()
            } finally {
                // Update last check time
                preferences.edit()
                    .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                    .apply()

                latch.countDown()
            }
        }

        // Wait for the async operation to complete with timeout
        val completed = latch.await(WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        return if (completed) {
            workerResult
        } else {
            Log.w(TAG, "Notification fetch timed out")
            Result.retry()
        }
    }

    /**
     * Filters out notifications we've already seen in previous checks
     */
    private fun filterNewNotifications(
        notifications: List<de.fampopprol.dhbwhorb.data.dualis.models.Notification>
    ): List<de.fampopprol.dhbwhorb.data.dualis.models.Notification> {
        val lastNotificationIds = preferences.getStringSet(KEY_LAST_NOTIFICATION_IDS, emptySet()) ?: emptySet()

        return notifications.filter { notification ->
            !lastNotificationIds.contains(notification.id)
        }
    }

    /**
     * Updates the stored notification IDs for future comparisons
     */
    private fun updateStoredNotificationIds(
        notifications: List<de.fampopprol.dhbwhorb.data.dualis.models.Notification>
    ) {
        val notificationIds = notifications.map { it.id }.toSet()
        preferences.edit()
            .putStringSet(KEY_LAST_NOTIFICATION_IDS, notificationIds)
            .apply()
    }

    /**
     * Shows a notification to the user about schedule changes
     */
    private fun showScheduleChangeNotification(
        notifications: List<de.fampopprol.dhbwhorb.data.dualis.models.Notification>
    ) {
        val changes = notifications.map { notification ->
            when (notification.type) {
                NotificationType.SCHEDULE_CHANGE -> {
                    val courseName = extractCourseName(notification.subject)
                    "Schedule changed: $courseName"
                }
                NotificationType.SCHEDULE_SET -> {
                    val courseName = extractCourseName(notification.subject)
                    "New appointment: $courseName"
                }
                else -> notification.subject
            }
        }

        Log.d(TAG, "Showing notification for changes: $changes")
        notificationManager.showTimetableChangeNotification(changes)
    }

    /**
     * Extracts the course name from the notification subject
     * Example: "T4INF2904.2 / C# und .NET HOR-TINF2024": Termin geändert
     * Returns: "C# und .NET"
     */
    private fun extractCourseName(subject: String): String {
        // Try to extract course name from the subject
        val regex = Regex("\"([^\"]*?)\"")
        val match = regex.find(subject)

        if (match != null) {
            val fullCourseName = match.groupValues[1]
            // Extract just the course name part (after the course code and /)
            val parts = fullCourseName.split(" / ")
            if (parts.size >= 2) {
                // Remove the course code and location info
                val courseNamePart = parts[1].trim()
                // Remove trailing location info like "HOR-TINF2024"
                val cleanCourseName = courseNamePart.replace(Regex("\\s+[A-Z]+-[A-Z0-9]+$"), "")
                return cleanCourseName.ifEmpty { courseNamePart }
            }
        }

        // Fallback: return original subject
        return subject
    }
}
