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
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.NotificationType
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
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
    private val timetableCacheManager = TimetableCacheManager(applicationContext)

    override fun doWork(): Result {
        Log.d(TAG, "=== STARTING SCHEDULE CHANGE CHECK ===")

        if (!permissionHelper.hasNotificationPermission()) {
            Log.d(TAG, "Notification permission not granted, skipping check")
            return Result.success()
        }

        // Check if notifications are enabled
        return try {
            val notificationsEnabled =
                runBlocking { preferencesManager.getNotificationsEnabledBlocking() }
            val timetableNotificationsEnabled =
                runBlocking { preferencesManager.getTimetableNotificationsEnabledBlocking() }

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
        var workerResult: Result = Result.success()

        dualisService.getUnreadNotifications { notificationList ->
            try {
                if (notificationList != null) {
                    Log.d(TAG, "Received ${notificationList.totalUnreadCount} unread notifications")

                    // Filter for schedule-related notifications
                    val scheduleNotifications =
                        notificationList.unreadNotifications.filter { notification ->
                            notification.type == NotificationType.SCHEDULE_CHANGE ||
                                    notification.type == NotificationType.SCHEDULE_SET
                        }

                    Log.d(TAG, "Found ${scheduleNotifications.size} schedule-related notifications")

                    if (scheduleNotifications.isNotEmpty()) {
                        // Check if these are new notifications we haven't seen before
                        val newNotifications = filterNewNotifications(scheduleNotifications)

                        if (newNotifications.isNotEmpty()) {
                            val verified = verifyRoomRemovalBeforeNotify(newNotifications)

                            if (verified.isNotEmpty()) {
                                Log.d(
                                    TAG,
                                    "Notifying ${verified.size} schedule notifications after room verification"
                                )
                                showScheduleChangeNotification(verified)
                            } else {
                                Log.d(
                                    TAG,
                                    "All new schedule notifications suppressed after room recheck"
                                )
                            }

                            updateStoredNotificationIds(scheduleNotifications)
                        } else {
                            Log.d(TAG, "No new schedule notifications since last check")
                        }
                    } else {
                        Log.d(TAG, "No schedule-related notifications found")
                    }
                } else {
                    Log.w(TAG, "Failed to fetch notifications from Dualis")
                    workerResult = Result.retry()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notifications", e)
                workerResult = Result.failure()
            } finally {
                preferences.edit()
                    .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                    .apply()
                latch.countDown()
            }
        }

        val completed = latch.await(WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return if (completed) workerResult else {
            Log.w(TAG, "Notification fetch timed out")
            Result.retry()
        }
    }

    private fun filterNewNotifications(
        notifications: List<de.fampopprol.dhbwhorb.data.dualis.models.Notification>
    ): List<de.fampopprol.dhbwhorb.data.dualis.models.Notification> {
        val lastNotificationIds =
            preferences.getStringSet(KEY_LAST_NOTIFICATION_IDS, emptySet()) ?: emptySet()
        return notifications.filter { notification -> !lastNotificationIds.contains(notification.id) }
    }

    private fun updateStoredNotificationIds(
        notifications: List<de.fampopprol.dhbwhorb.data.dualis.models.Notification>
    ) {
        val notificationIds = notifications.map { it.id }.toSet()
        preferences.edit()
            .putStringSet(KEY_LAST_NOTIFICATION_IDS, notificationIds)
            .apply()
    }

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

    private fun extractCourseName(subject: String): String {
        val regex = Regex("\"([^\"]*?)\"")
        val match = regex.find(subject)
        if (match != null) {
            val fullCourseName = match.groupValues[1]
            val parts = fullCourseName.split(" / ")
            if (parts.size >= 2) {
                val courseNamePart = parts[1].trim()
                val cleanCourseName = courseNamePart.replace(Regex("\\s+[A-Z]+-[A-Z0-9]+$"), "")
                return cleanCourseName.ifEmpty { courseNamePart }
            }
        }
        return subject
    }

    // ===== Room removal verification logic =====

    private data class EventKey(val title: String, val start: String, val end: String)

    private fun getWeekStart(): LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)

    private fun fetchWeeklyScheduleBlocking(weekStart: LocalDate): List<TimetableDay>? {
        val latch = CountDownLatch(1)
        var result: List<TimetableDay>? = null
        dualisService.getWeeklySchedule(weekStart) { timetable ->
            result = timetable
            latch.countDown()
        }
        latch.await(15, TimeUnit.SECONDS)
        return result
    }

    private fun buildEventMap(timetable: List<TimetableDay>): Map<EventKey, String> {
        val map = mutableMapOf<EventKey, String>()
        timetable.forEach { day ->
            day.events.forEach { ev ->
                val key = EventKey(ev.title.trim(), ev.startTime.trim(), ev.endTime.trim())
                map[key] = ev.room.trim()
            }
        }
        return map
    }

    private fun verifyRoomRemovalBeforeNotify(
        newNotifications: List<de.fampopprol.dhbwhorb.data.dualis.models.Notification>
    ): List<de.fampopprol.dhbwhorb.data.dualis.models.Notification> {
        val weekStart = getWeekStart()
        val oldTimetable = timetableCacheManager.loadTimetable(weekStart)
        if (oldTimetable == null) {
            Log.d(TAG, "No cached timetable; skip room removal verification")
            return newNotifications
        }

        val firstFetch = fetchWeeklyScheduleBlocking(weekStart)
        if (firstFetch == null) {
            Log.w(TAG, "First timetable fetch failed; notifying all new schedule notifications")
            return newNotifications
        }

        val oldMap = buildEventMap(oldTimetable)
        val firstMap = buildEventMap(firstFetch)

        val potentialRoomRemovalIds = mutableSetOf<String>()

        newNotifications.forEach { notif ->
            val courseName = extractCourseName(notif.subject)
            val oldEvents = oldMap.filter { it.key.title.contains(courseName, ignoreCase = true) }
            val newEvents = firstMap.filter { it.key.title.contains(courseName, ignoreCase = true) }
            if (oldEvents.isNotEmpty() && newEvents.isNotEmpty()) {
                oldEvents.forEach { (key, oldRoom) ->
                    val newRoom = newEvents[key]
                    if (!oldRoom.isNullOrBlank() && (newRoom != null && newRoom.isBlank())) {
                        potentialRoomRemovalIds.add(notif.id)
                        Log.d(
                            TAG,
                            "Potential room removal: ${notif.id} oldRoom='$oldRoom' -> newRoom='' (rechecking)"
                        )
                    }
                }
            }
        }

        if (potentialRoomRemovalIds.isEmpty()) {
            timetableCacheManager.saveTimetable(weekStart, firstFetch)
            return newNotifications
        }

        try {
            Thread.sleep(5000)
        } catch (_: InterruptedException) {
        }

        val secondFetch = fetchWeeklyScheduleBlocking(weekStart)
        if (secondFetch == null) {
            Log.w(TAG, "Second fetch failed; suppressing potential temporary removals")
            timetableCacheManager.saveTimetable(weekStart, firstFetch)
            return newNotifications.filter { it.id !in potentialRoomRemovalIds }
        }

        val secondMap = buildEventMap(secondFetch)
        val confirmedRemoval = mutableSetOf<String>()
        val resolved = mutableSetOf<String>()

        newNotifications.forEach { notif ->
            if (notif.id in potentialRoomRemovalIds) {
                val courseName = extractCourseName(notif.subject)
                val oldEvents =
                    oldMap.filter { it.key.title.contains(courseName, ignoreCase = true) }
                val newEventsSecond =
                    secondMap.filter { it.key.title.contains(courseName, ignoreCase = true) }
                var stillRemoved = false
                oldEvents.forEach { (key, oldRoom) ->
                    val newRoomSecond = newEventsSecond[key]
                    if (!oldRoom.isNullOrBlank() && (newRoomSecond != null && newRoomSecond.isBlank())) {
                        stillRemoved = true
                    }
                }
                if (stillRemoved) {
                    confirmedRemoval.add(notif.id)
                } else {
                    resolved.add(notif.id)
                }
            }
        }

        timetableCacheManager.saveTimetable(weekStart, secondFetch)

        Log.d(
            TAG,
            "Room removal check: potential=${potentialRoomRemovalIds.size} confirmed=${confirmedRemoval.size} resolved=${resolved.size}"
        )

        return newNotifications.filter { notif ->
            (notif.id !in potentialRoomRemovalIds) || (notif.id in confirmedRemoval)
        }
    }
}
