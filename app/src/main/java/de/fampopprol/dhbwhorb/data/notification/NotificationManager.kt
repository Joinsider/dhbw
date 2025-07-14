/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.fampopprol.dhbwhorb.MainActivity
import de.fampopprol.dhbwhorb.R

class DHBWNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID_TIMETABLE = "timetable_changes"
        private const val CHANNEL_ID_GRADES = "grade_changes"
        private const val CHANNEL_ID_CLASS_REMINDER = "class_reminder"
        private const val NOTIFICATION_ID_TIMETABLE = 1001
        private const val NOTIFICATION_ID_GRADES = 1002
        private const val NOTIFICATION_ID_CLASS_REMINDER_BASE = 2000 // Base ID for class reminders
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Timetable changes channel
        val timetableChannel = NotificationChannel(
            CHANNEL_ID_TIMETABLE,
            "Timetable Changes",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for timetable changes"
        }

        // Grade changes channel
        val gradesChannel = NotificationChannel(
            CHANNEL_ID_GRADES,
            "Grade Changes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new grades"
        }

        // Class reminder channel
        val classReminderChannel = NotificationChannel(
            CHANNEL_ID_CLASS_REMINDER,
            "Class Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders for upcoming classes"
        }

        notificationManager.createNotificationChannel(timetableChannel)
        notificationManager.createNotificationChannel(gradesChannel)
        notificationManager.createNotificationChannel(classReminderChannel)
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showTimetableChangeNotification(changes: List<String>) {
        if (!hasNotificationPermission()) {
            return // Don't show notification if permission not granted
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val changesText = if (changes.size == 1) {
            changes.first()
        } else {
            "${changes.size} changes detected"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TIMETABLE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Timetable Updated")
            .setContentText(changesText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(changes.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TIMETABLE, notification)
        } catch (e: SecurityException) {
            // Handle the case where permission is revoked after check
            android.util.Log.w("DHBWNotificationManager", "Failed to show notification: permission denied", e)
        }
    }

    fun showGradeChangeNotification(changes: List<String>) {
        if (!hasNotificationPermission()) {
            return // Don't show notification if permission not granted
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val changesText = if (changes.size == 1) {
            changes.first()
        } else {
            "${changes.size} new grades available"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GRADES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Grades Available")
            .setContentText(changesText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(changes.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_GRADES, notification)
        } catch (e: SecurityException) {
            // Handle the case where permission is revoked after check
            android.util.Log.w("DHBWNotificationManager", "Failed to show notification: permission denied", e)
        }
    }

    fun showClassReminderNotification(reminder: String, notificationId: Int) {
        if (!hasNotificationPermission()) {
            return // Don't show notification if permission not granted
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CLASS_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Class Reminder")
            .setContentText(reminder)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CLASS_REMINDER_BASE + notificationId, notification)
        } catch (e: SecurityException) {
            // Handle the case where permission is revoked after check
            android.util.Log.w("DHBWNotificationManager", "Failed to show notification: permission denied", e)
        }
    }
}
