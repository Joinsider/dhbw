/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClassReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ClassReminderAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Class reminder alarm triggered")

        val reminderText = intent.getStringExtra("reminder_text")
        val notificationId = intent.getIntExtra("notification_id", 0)
        val eventTitle = intent.getStringExtra("event_title")
        val classStartTime = intent.getStringExtra("class_start_time")

        if (reminderText == null) {
            Log.e(TAG, "No reminder text provided in alarm intent")
            return
        }

        Log.d(TAG, "Processing alarm for event: $eventTitle at $classStartTime")

        // Use goAsync to handle the notification in a coroutine
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if class reminder notifications are still enabled
                val preferencesManager = NotificationPreferencesManager(context)
                val notificationsEnabled = preferencesManager.getNotificationsEnabledBlocking()
                val classRemindersEnabled = preferencesManager.getClassReminderNotificationsEnabledBlocking()

                Log.d(TAG, "Notifications enabled: $notificationsEnabled, Class reminders enabled: $classRemindersEnabled")

                if (!notificationsEnabled || !classRemindersEnabled) {
                    Log.d(TAG, "Class reminder notifications are disabled, skipping alarm notification")
                    return@launch
                }

                // Show the notification
                val notificationManager = DHBWNotificationManager(context)
                notificationManager.showClassReminderNotification(reminderText, notificationId)

                Log.d(TAG, "Class reminder notification shown successfully from alarm: $reminderText")

            } catch (e: Exception) {
                Log.e(TAG, "Error showing class reminder notification from alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
