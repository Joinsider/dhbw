/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDateTime

/**
 * High-level notification manager that coordinates between
 * LectureChangeMonitor, NotificationDispatcher, and preferences.
 */
class NotificationManager(
    private val monitor: LectureChangeMonitor,
    private val dispatcher: NotificationDispatcher,
    private val preferences: NotificationPreferencesInteractor
) {
    companion object {
        private const val TAG = "NotificationManager"
    }

    /**
     * Check for lecture changes and send notifications if appropriate.
     * Only sends notifications if user has enabled them and granted permission.
     *
     * @return true if check completed successfully (with or without changes), false if error occurred
     */
    suspend fun checkAndNotify(): Boolean {
        Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
        Napier.d("🔔 NotificationManager: Starting check and notify process", tag = TAG)
        Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)

        // Check if notifications should be processed
        val shouldProcess = preferences.shouldProcessLectureAlerts()
        Napier.d("📋 Preference check:", tag = TAG)
        Napier.d("   - Notifications enabled: ${preferences.getNotificationsEnabled()}", tag = TAG)
        Napier.d("   - Lecture alerts enabled: ${preferences.getLectureAlertsEnabled()}", tag = TAG)
        Napier.d("   - Should process: $shouldProcess", tag = TAG)

        if (!shouldProcess) {
            Napier.d("⏭️  Notifications or lecture alerts disabled, skipping check", tag = TAG)
            Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
            return true // Not an error, just disabled
        }

        // Check if we have permission
        val hasPermission = dispatcher.hasPermission()
        Napier.d("🔐 Permission check: ${if (hasPermission) "✅ Granted" else "❌ Denied"}", tag = TAG)
        if (!hasPermission) {
            Napier.w("⚠️  No notification permission, skipping check", tag = TAG)
            Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
            return true // Not an error, just no permission
        }

        // Monitor for changes
        Napier.d("🔍 Starting lecture change monitoring...", tag = TAG)
        when (val result = monitor.checkForChanges()) {
            is MonitorResult.Changes -> {
                val changes = result.changes
                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
                Napier.d("📬 CHANGES DETECTED - Found ${changes.size} lecture change(s)", tag = TAG)
                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)

                changes.forEach { change ->
                    Napier.d("   📝 ${change::class.simpleName}: ${change.courseName}", tag = TAG)
                }

                if (changes.size == 1) {
                    // Single change - show detailed notification
                    val change = changes.first()
                    val (title, message) = formatSingleChange(change)
                    Napier.d("📤 Sending single notification:", tag = TAG)
                    Napier.d("   Title: $title", tag = TAG)
                    Napier.d("   Message: $message", tag = TAG)
                    dispatcher.showNotification(title, message, change.lectureId)
                    Napier.d("✅ Single notification dispatched", tag = TAG)
                } else {
                    // Multiple changes - show summary
                    val title = "Lecture Changes"
                    val message = formatMultipleChanges(changes)
                    Napier.d("📤 Sending summary notification:", tag = TAG)
                    Napier.d("   Title: $title", tag = TAG)
                    Napier.d("   Message: $message", tag = TAG)
                    Napier.d("   Change count: ${changes.size}", tag = TAG)
                    dispatcher.showSummaryNotification(title, message, changes.size)
                    Napier.d("✅ Summary notification dispatched", tag = TAG)
                }
                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
                return true
            }

            is MonitorResult.NoChanges -> {
                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
                Napier.d("✅ No lecture changes detected (${result.lecturesChecked} lectures checked)", tag = TAG)
                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
                return true
            }

            is MonitorResult.Error -> {
                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
                Napier.e("❌ ERROR checking for lecture changes: ${result.message}", tag = TAG)
                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
                return false
            }
        }
    }

    /**
     * Format a single lecture change into notification title and message.
     */
    private fun formatSingleChange(change: LectureChange): Pair<String, String> {
        return when (change) {
            is LectureChange.TimeChange -> {
                val title = "Time Change: ${change.courseName}"
                val oldTime = formatTime(change.oldStartTime)
                val newTime = formatTime(change.newStartTime)
                val message = "Changed from $oldTime to $newTime"
                title to message
            }

            is LectureChange.LocationChange -> {
                val title = "Location Change: ${change.courseName}"
                val message = "Moved from ${change.oldLocation} to ${change.newLocation}"
                title to message
            }

            is LectureChange.LecturerChange -> {
                val title = "Lecturer Change: ${change.courseName}"
                val message = "Changed from ${change.oldLecturers.joinToString(", ")} to ${change.newLecturers.joinToString(", ")}"
                title to message
            }

            is LectureChange.TypeChange -> {
                val title = "Type Change: ${change.courseName}"
                val message = if (change.newIsTest) "Changed to exam/test" else "Changed to regular lecture"
                title to message
            }

            is LectureChange.Cancellation -> {
                val title = "Cancelled: ${change.courseName}"
                val message = "Lecture on ${formatDate(change.cancelledLecture.startTime)} has been cancelled"
                title to message
            }

            is LectureChange.NewLecture -> {
                val title = "New Lecture: ${change.courseName}"
                val message = "Added on ${formatDate(change.lecture.startTime)} at ${formatTime(change.lecture.startTime)}"
                title to message
            }
        }
    }

    /**
     * Format multiple lecture changes into a summary message.
     */
    private fun formatMultipleChanges(changes: List<LectureChange>): String {
        val changeTypes = changes.groupBy { it::class.simpleName }
        val parts = mutableListOf<String>()

        changeTypes.forEach { (type, list) ->
            val count = list.size
            val label = when (type) {
                "TimeChange" -> "time change${if (count > 1) "s" else ""}"
                "LocationChange" -> "location change${if (count > 1) "s" else ""}"
                "LecturerChange" -> "lecturer change${if (count > 1) "s" else ""}"
                "TypeChange" -> "type change${if (count > 1) "s" else ""}"
                "Cancellation" -> "cancellation${if (count > 1) "s" else ""}"
                "NewLecture" -> "new lecture${if (count > 1) "s" else ""}"
                else -> "change${if (count > 1) "s" else ""}"
            }
            parts.add("$count $label")
        }

        return parts.joinToString(", ") + " detected"
    }

    /**
     * Format a LocalDateTime to time string (HH:mm).
     */
    private fun formatTime(dateTime: LocalDateTime?): String {
        if (dateTime == null) return "Unknown"
        return "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    }

    /**
     * Format a LocalDateTime to date string (dd.MM.yyyy).
     */
    private fun formatDate(dateTime: LocalDateTime): String {
        return "${dateTime.day.toString().padStart(2, '0')}.${dateTime.month.toString().padStart(2, '0')}.${dateTime.year}"
    }
}

