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
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.permissions.PermissionManager
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "NotificationWorker"
        const val WORK_NAME = "dhbw_change_detection"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting notification worker")

            // Check if notifications are enabled and permission is granted
            val permissionManager = PermissionManager(context)
            val notificationPreferencesManager = NotificationPreferencesManager(context)

            val notificationsEnabled = notificationPreferencesManager.getNotificationsEnabledBlocking()
            val hasPermission = permissionManager.hasNotificationPermission()

            if (!notificationsEnabled || !hasPermission) {
                Log.d(TAG, "Notifications disabled or permission not granted - skipping check")
                return@withContext Result.success()
            }

            val credentialManager = CredentialManager(context)
            if (!credentialManager.hasStoredCredentialsBlocking()) {
                Log.d(TAG, "No stored credentials - skipping notification check")
                return@withContext Result.success()
            }

            val username = credentialManager.getUsernameBlocking()
            val password = credentialManager.getPassword()

            if (username == null || password == null) {
                Log.d(TAG, "Invalid credentials - skipping notification check")
                return@withContext Result.success()
            }

            val dualisService = DualisService()
            val notificationManager = DHBWNotificationManager(context)
            val changeDetectionService = ChangeDetectionService(context)
            val timetableCacheManager = TimetableCacheManager(context)
            val gradesCacheManager = GradesCacheManager(context)

            // Login to Dualis using suspendCoroutine for proper async handling
            val isLoggedIn = suspendCoroutine<Boolean> { continuation ->
                dualisService.login(username, password) { result ->
                    continuation.resume(result != null)
                }
            }

            if (!isLoggedIn) {
                Log.e(TAG, "Failed to login to Dualis")
                return@withContext Result.retry()
            }

            Log.d(TAG, "Successfully logged in to Dualis")

            // Check individual notification preferences before fetching
            val timetableNotificationsEnabled = notificationPreferencesManager.getTimetableNotificationsEnabledBlocking()
            val gradeNotificationsEnabled = notificationPreferencesManager.getGradeNotificationsEnabledBlocking()

            // Check timetable changes only if enabled
            if (timetableNotificationsEnabled) {
                checkTimetableChanges(dualisService, changeDetectionService, timetableCacheManager, notificationManager)
            } else {
                Log.d(TAG, "Timetable notifications disabled - skipping timetable check")
            }

            // Check grade changes only if enabled
            if (gradeNotificationsEnabled) {
                checkGradeChanges(dualisService, changeDetectionService, gradesCacheManager, notificationManager)
            } else {
                Log.d(TAG, "Grade notifications disabled - skipping grade check")
            }

            Log.d(TAG, "Notification worker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in notification worker", e)
            Result.retry()
        }
    }

    private suspend fun checkTimetableChanges(
        dualisService: DualisService,
        changeDetectionService: ChangeDetectionService,
        timetableCacheManager: TimetableCacheManager,
        notificationManager: DHBWNotificationManager
    ) {
        try {
            val weekStarts = getNext4WeekStarts()
            val newTimetableData = mutableMapOf<LocalDate, List<de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay>>()

            // Fetch timetable data for each week using proper coroutine handling
            for (weekStart in weekStarts) {
                val timetable = suspendCoroutine<List<de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay>?> { continuation ->
                    dualisService.getWeeklySchedule(weekStart) { result ->
                        continuation.resume(result)
                    }
                }

                if (timetable != null) {
                    newTimetableData[weekStart] = timetable
                    Log.d(TAG, "Fetched timetable for week $weekStart")
                } else {
                    Log.w(TAG, "Failed to fetch timetable for week $weekStart")
                }
            }

            // Only proceed if we have any timetable data
            if (newTimetableData.isEmpty()) {
                Log.w(TAG, "No timetable data fetched - skipping change detection")
                return
            }

            // Detect changes
            val changes = changeDetectionService.detectTimetableChanges(newTimetableData)

            // Only send notification if changes are found
            if (changes.isNotEmpty()) {
                val changeDescriptions = changeDetectionService.formatTimetableChanges(changes)
                Log.d(TAG, "Found ${changes.size} timetable changes")

                try {
                    notificationManager.showTimetableChangeNotification(changeDescriptions)
                    Log.d(TAG, "Timetable change notification sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send timetable notification", e)
                    // Don't update cache if notification failed
                    return
                }
            } else {
                Log.d(TAG, "No timetable changes detected")
            }

            // Only save new data to cache after successful notification (if any)
            newTimetableData.forEach { (weekStart, timetable) ->
                timetableCacheManager.saveTimetable(weekStart, timetable)
            }
            Log.d(TAG, "Updated timetable cache for ${newTimetableData.size} weeks")

            // Reschedule class reminders with updated timetable data
            updateClassReminders(newTimetableData)

        } catch (e: Exception) {
            Log.e(TAG, "Error checking timetable changes", e)
        }
    }

    private suspend fun checkGradeChanges(
        dualisService: DualisService,
        changeDetectionService: ChangeDetectionService,
        gradesCacheManager: GradesCacheManager,
        notificationManager: DHBWNotificationManager
    ) {
        try {
            // Get available semesters with proper coroutine handling
            val semesters = suspendCoroutine<List<de.fampopprol.dhbwhorb.data.dualis.models.Semester>?> { continuation ->
                dualisService.getAvailableSemesters { result ->
                    continuation.resume(result)
                }
            }

            if (semesters.isNullOrEmpty()) {
                Log.w(TAG, "No semesters available - skipping grade check")
                return
            }

            val currentSemester = semesters.first() // Use the first (most recent) semester
            Log.d(TAG, "Checking grades for semester: ${currentSemester.displayName}")

            // Get grades for current semester with proper coroutine handling
            val grades = suspendCoroutine<de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades?> { continuation ->
                dualisService.getStudyGrades(currentSemester.value) { result ->
                    continuation.resume(result)
                }
            }

            if (grades == null) {
                Log.w(TAG, "Failed to fetch grades for semester ${currentSemester.displayName}")
                return
            }

            Log.d(TAG, "Fetched grades for semester ${currentSemester.displayName}")

            // Detect changes
            val changes = changeDetectionService.detectGradeChanges(grades, currentSemester)

            // Only send notification if changes are found
            if (changes != null) {
                val changeDescriptions = changeDetectionService.formatGradeChanges(changes)
                Log.d(TAG, "Found grade changes: ${changeDescriptions.size} items")

                try {
                    notificationManager.showGradeChangeNotification(changeDescriptions)
                    Log.d(TAG, "Grade change notification sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send grade notification", e)
                    // Don't update cache if notification failed
                    return
                }
            } else {
                Log.d(TAG, "No grade changes detected")
            }

            // Only save new data to cache after successful notification (if any)
            gradesCacheManager.cacheGrades(grades, currentSemester)
            Log.d(TAG, "Updated grades cache for semester ${currentSemester.displayName}")

        } catch (e: Exception) {
            Log.e(TAG, "Error checking grade changes", e)
        }
    }

    private suspend fun updateClassReminders(timetableData: Map<LocalDate, List<de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay>>) {
        try {
            val preferencesManager = NotificationPreferencesManager(context)
            val classRemindersEnabled = preferencesManager.getClassReminderNotificationsEnabledBlocking()
            val notificationsEnabled = preferencesManager.getNotificationsEnabledBlocking()

            if (notificationsEnabled && classRemindersEnabled) {
                val reminderMinutes = preferencesManager.getClassReminderTimeMinutesBlocking()
                val classReminderScheduler = ClassReminderScheduler(context)

                // Cancel existing reminders and reschedule with updated data
                classReminderScheduler.cancelAllClassReminders()
                classReminderScheduler.scheduleClassReminders(timetableData, reminderMinutes)
                Log.d(TAG, "Rescheduled class reminders with updated timetable data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating class reminders", e)
        }
    }

    private fun getNext4WeekStarts(): List<LocalDate> {
        val today = LocalDate.now()
        val currentWeekStart = today.with(DayOfWeek.MONDAY)

        return (0..3).map { weekOffset ->
            currentWeekStart.plusWeeks(weekOffset.toLong())
        }
    }
}
