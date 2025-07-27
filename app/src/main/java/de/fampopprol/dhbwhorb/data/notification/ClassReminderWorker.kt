/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClassReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ClassReminderWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val reminderText = inputData.getString("reminder_text")
            val notificationId = inputData.getInt("notification_id", 0)
            val eventTitle = inputData.getString("event_title")
            val classStartTime = inputData.getString("class_start_time")
            val isFallback = inputData.getBoolean("is_fallback", false)

            Log.d(TAG, "ClassReminderWorker triggered for event: $eventTitle at $classStartTime (fallback: $isFallback)")

            if (reminderText == null) {
                Log.e(TAG, "No reminder text provided")
                return@withContext Result.failure()
            }

            // Check if class reminder notifications are still enabled
            val preferencesManager = NotificationPreferencesManager(applicationContext)
            val notificationsEnabled = preferencesManager.getNotificationsEnabledBlocking()
            val classRemindersEnabled = preferencesManager.getClassReminderNotificationsEnabledBlocking()

            Log.d(TAG, "Notifications enabled: $notificationsEnabled, Class reminders enabled: $classRemindersEnabled")

            if (!notificationsEnabled || !classRemindersEnabled) {
                Log.d(TAG, "Class reminder notifications are disabled, skipping")
                return@withContext Result.success()
            }

            // If this is a fallback notification, add a small delay to prevent race conditions with alarm
            if (isFallback) {
                kotlinx.coroutines.delay(2000) // 2 second delay for fallback
                Log.d(TAG, "Fallback notification executing after delay")
            }

            // Show the notification
            val notificationManager = DHBWNotificationManager(applicationContext)
            notificationManager.showClassReminderNotification(reminderText, notificationId)

            Log.d(TAG, "Class reminder notification shown successfully: $reminderText (fallback: $isFallback)")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing class reminder notification", e)
            Result.failure()
        }
    }
}
