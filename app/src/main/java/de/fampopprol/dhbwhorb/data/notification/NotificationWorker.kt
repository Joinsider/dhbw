package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

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

            // Login to Dualis
            var isLoggedIn = false
            dualisService.login(username, password) { result ->
                isLoggedIn = result != null
            }

            if (!isLoggedIn) {
                Log.e(TAG, "Failed to login to Dualis")
                return@withContext Result.retry()
            }

            Log.d(TAG, "Successfully logged in to Dualis")

            // Check timetable changes for current and next 3 weeks
            checkTimetableChanges(dualisService, changeDetectionService, timetableCacheManager, notificationManager)

            // Check grade changes
            checkGradeChanges(dualisService, changeDetectionService, gradesCacheManager, notificationManager)

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

            // Fetch timetable data for each week using getWeeklySchedule
            for (weekStart in weekStarts) {
                dualisService.getWeeklySchedule(weekStart) { timetable ->
                    if (timetable != null) {
                        newTimetableData[weekStart] = timetable
                        Log.d(TAG, "Fetched timetable for week $weekStart")
                    }
                }
            }

            // Detect changes
            val changes = changeDetectionService.detectTimetableChanges(newTimetableData)

            if (changes.isNotEmpty()) {
                val changeDescriptions = changeDetectionService.formatTimetableChanges(changes)
                Log.d(TAG, "Found ${changes.size} timetable changes")
                notificationManager.showTimetableChangeNotification(changeDescriptions)
            }

            // Save new data to cache
            newTimetableData.forEach { (weekStart, timetable) ->
                timetableCacheManager.saveTimetable(weekStart, timetable)
            }

            // Reschedule class reminders with updated timetable data
            try {
                val preferencesManager = NotificationPreferencesManager(context)
                val classRemindersEnabled = preferencesManager.getClassReminderNotificationsEnabledBlocking()
                val notificationsEnabled = preferencesManager.getNotificationsEnabledBlocking()

                if (notificationsEnabled && classRemindersEnabled) {
                    val reminderMinutes = preferencesManager.getClassReminderTimeMinutesBlocking()
                    val classReminderScheduler = ClassReminderScheduler(context)

                    // Cancel existing reminders and reschedule with updated data
                    classReminderScheduler.cancelAllClassReminders()
                    classReminderScheduler.scheduleClassReminders(newTimetableData, reminderMinutes)
                    Log.d(TAG, "Rescheduled class reminders with updated timetable data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling class reminders", e)
            }
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
            // Get available semesters
            dualisService.getAvailableSemesters { semesters ->
                if (semesters != null && semesters.isNotEmpty()) {
                    val currentSemester = semesters.first() // Use the first (most recent) semester

                    // Get grades for current semester using getStudyGrades
                    dualisService.getStudyGrades(currentSemester.value) { grades ->
                        if (grades != null) {
                            Log.d(TAG, "Fetched grades for semester ${currentSemester.displayName}")

                            // Detect changes
                            try {
                                val changes = kotlinx.coroutines.runBlocking {
                                    changeDetectionService.detectGradeChanges(grades, currentSemester)
                                }

                                if (changes != null) {
                                    val changeDescriptions = changeDetectionService.formatGradeChanges(changes)
                                    Log.d(TAG, "Found grade changes: ${changeDescriptions.size} items")
                                    notificationManager.showGradeChangeNotification(changeDescriptions)
                                }

                                // Save new data to cache
                                kotlinx.coroutines.runBlocking {
                                    gradesCacheManager.cacheGrades(grades, currentSemester)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing grade changes", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking grade changes", e)
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
