package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
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
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class ClassReminderScheduler(private val context: Context) {

    companion object {
        private const val TAG = "ClassReminderScheduler"
        private const val WORK_TAG_PREFIX = "class_reminder_"
    }

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

        val workManager = WorkManager.getInstance(context)
        val now = LocalDateTime.now()
        var scheduledCount = 0

        timetableData.forEach { (weekStart, weekDays) ->
            weekDays.forEach { day ->
                val dayDate = LocalDate.parse(day.date, DateTimeFormatter.ISO_LOCAL_DATE)

                day.events.forEach { event ->
                    try {
                        val classStartTime = parseEventDateTime(dayDate, event.startTime)
                        val reminderTime = classStartTime.minusMinutes(reminderMinutes.toLong())

                        // Only schedule if the reminder time is in the future
                        if (reminderTime.isAfter(now)) {
                            scheduleClassReminder(
                                workManager = workManager,
                                event = event,
                                reminderTime = reminderTime,
                                classStartTime = classStartTime,
                                reminderMinutes = reminderMinutes
                            )
                            scheduledCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error scheduling reminder for event: ${event.title}", e)
                    }
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
        workManager.cancelAllWorkByTag(WORK_TAG_PREFIX)
        Log.d(TAG, "Cancelled all class reminders")
    }

    private fun scheduleClassReminder(
        workManager: WorkManager,
        event: TimetableEvent,
        reminderTime: LocalDateTime,
        classStartTime: LocalDateTime,
        reminderMinutes: Int
    ) {
        val now = LocalDateTime.now()
        val delayInMinutes = java.time.Duration.between(now, reminderTime).toMinutes()

        if (delayInMinutes < 0) {
            return // Don't schedule past reminders
        }

        val reminderText = formatReminderText(event, classStartTime, reminderMinutes)
        val workId = generateWorkId(event, classStartTime)

        val inputData = workDataOf(
            "reminder_text" to reminderText,
            "notification_id" to workId.hashCode()
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val reminderWork = OneTimeWorkRequestBuilder<ClassReminderWorker>()
            .setInitialDelay(delayInMinutes, TimeUnit.MINUTES)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("$WORK_TAG_PREFIX$workId")
            .build()

        workManager.enqueueUniqueWork(
            workId,
            ExistingWorkPolicy.REPLACE,
            reminderWork
        )

        Log.d(TAG, "Scheduled reminder for ${event.title} at $reminderTime (in $delayInMinutes minutes)")
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
}
