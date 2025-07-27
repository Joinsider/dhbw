/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class ClassReminderScheduler(private val context: Context) {

    companion object {
        private const val TAG = "ClassReminderScheduler"
        private const val ALARM_REQUEST_CODE_BASE = 10000
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule reminders for all classes in the provided timetable data
     */
    fun scheduleClassReminders(
        timetableData: Map<LocalDate, List<TimetableDay>>,
        reminderMinutes: Int
    ) {
        if (reminderMinutes <= 0) {
            Log.d(TAG, "Reminder time is 0 or negative, skipping scheduling")
            return
        }

        // First cancel all existing reminders to avoid duplicates
        cancelAllClassReminders()

        val workManager = WorkManager.getInstance(context)
        val now = LocalDateTime.now()
        var scheduledCount = 0

        Log.d(TAG, "Starting to schedule class reminders for ${timetableData.size} weeks with $reminderMinutes minutes notice")

        // Define the German date format used in the timetable data
        val germanDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        timetableData.forEach { (_, weekDays) ->
            weekDays.forEach { day ->
                try {
                    val dayDate = LocalDate.parse(day.date, germanDateFormatter)
                    Log.d(TAG, "Processing day: $dayDate with ${day.events.size} events")

                    day.events.forEach { event ->
                        try {
                            val classStartTime = parseEventDateTime(dayDate, event.startTime)
                            val reminderTime = classStartTime.minusMinutes(reminderMinutes.toLong())

                            Log.d(TAG, "Event: ${event.title} at $classStartTime, reminder at $reminderTime")

                            // Only schedule if the reminder time is in the future
                            if (reminderTime.isAfter(now)) {
                                // Use AlarmManager for precise timing (primary method)
                                scheduleClassReminderWithAlarm(
                                    event = event,
                                    reminderTime = reminderTime,
                                    classStartTime = classStartTime,
                                    reminderMinutes = reminderMinutes
                                )

                                // Also schedule with WorkManager as fallback
                                scheduleClassReminderWithWorkManager(
                                    workManager = workManager,
                                    event = event,
                                    reminderTime = reminderTime,
                                    classStartTime = classStartTime,
                                    reminderMinutes = reminderMinutes
                                )
                                scheduledCount++
                            } else {
                                Log.d(TAG, "Skipping past reminder for ${event.title} (reminder time was $reminderTime)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error scheduling reminder for event: ${event.title}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing day: ${day.date}", e)
                }
            }
        }

        Log.d(TAG, "Scheduled $scheduledCount class reminders")
    }

    /**
     * Cancel all existing class reminder notifications
     */
    fun cancelAllClassReminders() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("class_reminder")

        // Cancel all alarms (we'll use a range of request codes)
        for (i in 0..999) {
            val requestCode = ALARM_REQUEST_CODE_BASE + i
            val intent = Intent(context, ClassReminderAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        Log.d(TAG, "Cancelled all class reminders")
    }

    private fun scheduleClassReminderWithAlarm(
        event: TimetableEvent,
        reminderTime: LocalDateTime,
        classStartTime: LocalDateTime,
        reminderMinutes: Int
    ) {
        val reminderText = formatReminderText(event, classStartTime, reminderMinutes)
        val requestCode = generateAlarmRequestCode(event, classStartTime)

        val intent = Intent(context, ClassReminderAlarmReceiver::class.java).apply {
            putExtra("reminder_text", reminderText)
            putExtra("notification_id", requestCode)
            putExtra("event_title", event.title)
            putExtra("class_start_time", classStartTime.toString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        try {
            // Use setExactAndAllowWhileIdle for precise timing even in Doze mode
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )

            Log.d(TAG, "Scheduled alarm for ${event.title} at $reminderTime with requestCode: $requestCode")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule exact alarm - permission denied", e)
            // Fallback to regular alarm
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun scheduleClassReminderWithWorkManager(
        workManager: WorkManager,
        event: TimetableEvent,
        reminderTime: LocalDateTime,
        classStartTime: LocalDateTime,
        reminderMinutes: Int
    ) {
        val now = LocalDateTime.now()
        val delayInMillis = java.time.Duration.between(now, reminderTime).toMillis()

        if (delayInMillis < 0) {
            Log.d(TAG, "Skipping past WorkManager reminder for ${event.title}")
            return // Don't schedule past reminders
        }

        val reminderText = formatReminderText(event, classStartTime, reminderMinutes)
        val workId = generateWorkId(event, classStartTime)

        val inputData = workDataOf(
            "reminder_text" to reminderText,
            "notification_id" to workId.hashCode(),
            "event_title" to event.title,
            "class_start_time" to classStartTime.toString(),
            "is_fallback" to true // Mark as fallback notification
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresDeviceIdle(false)
            .setRequiresCharging(false)
            .setRequiresStorageNotLow(false)
            .build()

        val reminderWork = OneTimeWorkRequestBuilder<ClassReminderWorker>()
            .setInitialDelay(delayInMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("class_reminder")
            .addTag("class_reminder_fallback")
            .addTag(workId)
            .build()

        workManager.enqueueUniqueWork(
            workId,
            ExistingWorkPolicy.REPLACE,
            reminderWork
        )

        Log.d(TAG, "Scheduled WorkManager fallback for ${event.title} at $reminderTime (in ${delayInMillis}ms) with workId: $workId")
    }

    private fun parseEventDateTime(date: LocalDate, timeString: String): LocalDateTime {
        // Parse time strings like "08:00" or "14:30"
        val time = LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        return LocalDateTime.of(date, time)
    }

    private fun formatReminderText(
        event: TimetableEvent,
        classStartTime: LocalDateTime,
        reminderMinutes: Int
    ): String {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val timeStr = classStartTime.format(timeFormatter)

        return if (reminderMinutes == 1) {
            "${event.title} starts in 1 minute at $timeStr"
        } else {
            "${event.title} starts in $reminderMinutes minutes at $timeStr"
        }
    }

    private fun generateWorkId(event: TimetableEvent, classStartTime: LocalDateTime): String {
        val dateTimeStr = classStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
        return "class_reminder_${event.title.replace(" ", "_")}_$dateTimeStr"
    }

    private fun generateAlarmRequestCode(event: TimetableEvent, classStartTime: LocalDateTime): Int {
        // Generate a unique but deterministic request code
        val dateTimeStr = classStartTime.format(DateTimeFormatter.ofPattern("MMddHHmm"))
        val eventHash = event.title.hashCode() and 0x7FFFFFFF // Ensure positive
        val timeValue = dateTimeStr.toIntOrNull() ?: 0
        return ALARM_REQUEST_CODE_BASE + ((eventHash + timeValue) % 1000)
    }
}
