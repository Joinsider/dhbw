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
                                    "Notifying ${verified.size} schedule notifications after room/lecturer verification"
                                )
                                showScheduleChangeNotification(verified)
                            } else {
                                Log.d(
                                    TAG,
                                    "All new schedule notifications suppressed after room/lecturer recheck"
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

    private fun buildEventMap(timetable: List<TimetableDay>): Map<EventKey, EventInfo> {
        val map = mutableMapOf<EventKey, EventInfo>()
        timetable.forEach { day ->
            day.events.forEach { ev ->
                val key = EventKey(ev.title.trim(), ev.startTime.trim(), ev.endTime.trim())
                map[key] = EventInfo(ev.room.trim(), ev.lecturer.trim())
            }
        }
        return map
    }

    private data class EventInfo(val room: String, val lecturer: String)

    private fun verifyRoomRemovalBeforeNotify(
        newNotifications: List<de.fampopprol.dhbwhorb.data.dualis.models.Notification>
    ): List<de.fampopprol.dhbwhorb.data.dualis.models.Notification> {
        val weekStart = getWeekStart()
        val oldTimetable = timetableCacheManager.loadTimetable(weekStart)
        if (oldTimetable == null) {
            Log.d(TAG, "No cached timetable; skip room/lecturer removal verification")
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
        val potentialLecturerRemovalIds = mutableSetOf<String>()

        newNotifications.forEach { notif ->
            val courseName = extractCourseName(notif.subject)
            val oldEvents = oldMap.filter { it.key.title.contains(courseName, ignoreCase = true) }
            val newEvents = firstMap.filter { it.key.title.contains(courseName, ignoreCase = true) }
            if (oldEvents.isNotEmpty() && newEvents.isNotEmpty()) {
                oldEvents.forEach { (key, oldInfo) ->
                    val newInfo = newEvents[key]
                    if (newInfo != null) {
                        if (oldInfo.room.isNotBlank() && newInfo.room.isBlank()) {
                            potentialRoomRemovalIds.add(notif.id)
                            Log.d(
                                TAG,
                                "Potential room removal: ${notif.id} oldRoom='${oldInfo.room}' -> newRoom='' (rechecking)"
                            )
                        }
                        if (oldInfo.lecturer.isNotBlank() && newInfo.lecturer.isBlank()) {
                            potentialLecturerRemovalIds.add(notif.id)
                            Log.d(
                                TAG,
                                "Potential lecturer removal: ${notif.id} oldLecturer='${oldInfo.lecturer}' -> newLecturer='' (rechecking)"
                            )
                        }
                    }
                }
            }
        }

        if (potentialRoomRemovalIds.isEmpty() && potentialLecturerRemovalIds.isEmpty()) {
            timetableCacheManager.saveTimetable(weekStart, firstFetch)
            return newNotifications
        }

        try {
            Thread.sleep(5000)
        } catch (_: InterruptedException) {
        }

        val secondFetch = fetchWeeklyScheduleBlocking(weekStart)
        if (secondFetch == null) {
            Log.w(TAG, "Second fetch failed; suppressing potential temporary removals (room/lecturer)")
            timetableCacheManager.saveTimetable(weekStart, firstFetch)
            return newNotifications.filter { it.id !in potentialRoomRemovalIds && it.id !in potentialLecturerRemovalIds }
        }

        val secondMap = buildEventMap(secondFetch)
        val confirmedRoomRemoval = mutableSetOf<String>()
        val confirmedLecturerRemoval = mutableSetOf<String>()
        val resolvedRoom = mutableSetOf<String>()
        val resolvedLecturer = mutableSetOf<String>()

        newNotifications.forEach { notif ->
            val courseName = extractCourseName(notif.subject)
            val oldEvents = oldMap.filter { it.key.title.contains(courseName, ignoreCase = true) }
            val newEventsSecond = secondMap.filter { it.key.title.contains(courseName, ignoreCase = true) }

            if (notif.id in potentialRoomRemovalIds) {
                var stillRemoved = false
                oldEvents.forEach { (key, oldInfo) ->
                    val newInfoSecond = newEventsSecond[key]
                    if (oldInfo.room.isNotBlank() && newInfoSecond != null && newInfoSecond.room.isBlank()) {
                        stillRemoved = true
                    }
                }
                if (stillRemoved) confirmedRoomRemoval.add(notif.id) else resolvedRoom.add(notif.id)
            }

            if (notif.id in potentialLecturerRemovalIds) {
                var stillRemovedLecturer = false
                oldEvents.forEach { (key, oldInfo) ->
                    val newInfoSecond = newEventsSecond[key]
                    if (oldInfo.lecturer.isNotBlank() && newInfoSecond != null && newInfoSecond.lecturer.isBlank()) {
                        stillRemovedLecturer = true
                    }
                }
                if (stillRemovedLecturer) confirmedLecturerRemoval.add(notif.id) else resolvedLecturer.add(notif.id)
            }
        }

        timetableCacheManager.saveTimetable(weekStart, secondFetch)

        Log.d(
            TAG,
            "Removal check: potentialRoom=${potentialRoomRemovalIds.size} confirmedRoom=${confirmedRoomRemoval.size} resolvedRoom=${resolvedRoom.size} | potentialLecturer=${potentialLecturerRemovalIds.size} confirmedLecturer=${confirmedLecturerRemoval.size} resolvedLecturer=${resolvedLecturer.size}"
        )

        return newNotifications.filter { notif ->
            (notif.id !in potentialRoomRemovalIds || notif.id in confirmedRoomRemoval) &&
                    (notif.id !in potentialLecturerRemovalIds || notif.id in confirmedLecturerRemoval)
        }
    }
}
