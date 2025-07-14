/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import android.util.Log
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TimetableChanges(
    val addedEvents: List<TimetableEvent>,
    val removedEvents: List<TimetableEvent>,
    val modifiedEvents: List<Pair<TimetableEvent, TimetableEvent>>, // old, new
    val weekStart: LocalDate
)

data class GradeChanges(
    val newGrades: List<String>,
    val updatedGrades: List<String>
)

class ChangeDetectionService(private val context: Context) {

    private val timetableCacheManager = TimetableCacheManager(context)
    private val gradesCacheManager = GradesCacheManager(context)

    companion object {
        private const val TAG = "ChangeDetectionService"
    }

    /**
     * Compare new timetable data with cached data for the current and next 3 weeks
     */
    fun detectTimetableChanges(
        newTimetableData: Map<LocalDate, List<TimetableDay>>
    ): List<TimetableChanges> {
        val changes = mutableListOf<TimetableChanges>()

        newTimetableData.forEach { (weekStart, newWeekData) ->
            val cachedWeekData = timetableCacheManager.loadTimetable(weekStart)

            if (cachedWeekData != null) {
                val weekChanges = compareWeekTimetables(cachedWeekData, newWeekData, weekStart)
                if (weekChanges.hasChanges()) {
                    changes.add(weekChanges)
                    Log.d(TAG, "Detected timetable changes for week $weekStart")
                }
            } else {
                // First time loading this week - don't notify, just cache
                Log.d(TAG, "First time loading timetable for week $weekStart - no notification")
            }
        }

        return changes
    }

    /**
     * Compare new grade data with cached data
     */
    suspend fun detectGradeChanges(
        newGrades: StudyGrades,
        semester: Semester
    ): GradeChanges? {
        val cachedData = gradesCacheManager.getCachedGrades()

        if (cachedData != null) {
            val (cachedGrades, _) = cachedData // Extract StudyGrades from the Pair
            return compareGrades(cachedGrades, newGrades)
        } else {
            // First time loading grades - don't notify
            Log.d(TAG, "First time loading grades - no notification")
            return null
        }
    }

    private fun compareWeekTimetables(
        oldWeek: List<TimetableDay>,
        newWeek: List<TimetableDay>,
        weekStart: LocalDate
    ): TimetableChanges {
        val addedEvents = mutableListOf<TimetableEvent>()
        val removedEvents = mutableListOf<TimetableEvent>()
        val modifiedEvents = mutableListOf<Pair<TimetableEvent, TimetableEvent>>()

        // Create maps for easier comparison
        val oldEventsMap = oldWeek.flatMap { day ->
            day.events.map { event -> "${day.date}_${event.title}_${event.startTime}" to event }
        }.toMap()

        val newEventsMap = newWeek.flatMap { day ->
            day.events.map { event -> "${day.date}_${event.title}_${event.startTime}" to event }
        }.toMap()

        // Find added events
        newEventsMap.forEach { (key, newEvent) ->
            if (!oldEventsMap.containsKey(key)) {
                addedEvents.add(newEvent)
            }
        }

        // Find removed and modified events
        oldEventsMap.forEach { (key, oldEvent) ->
            val newEvent = newEventsMap[key]
            if (newEvent == null) {
                removedEvents.add(oldEvent)
            } else if (!eventsAreEqual(oldEvent, newEvent)) {
                modifiedEvents.add(oldEvent to newEvent)
            }
        }

        return TimetableChanges(addedEvents, removedEvents, modifiedEvents, weekStart)
    }

    private fun compareGrades(oldGrades: StudyGrades, newGrades: StudyGrades): GradeChanges? {
        val newGradesList = mutableListOf<String>()
        val updatedGradesList = mutableListOf<String>()

        // Create maps for easier comparison using the correct model structure
        val oldExamsMap = oldGrades.modules.flatMap { module ->
            module.exams.map { exam -> exam.name to exam.grade }
        }.toMap()

        val newExamsMap = newGrades.modules.flatMap { module ->
            module.exams.map { exam -> exam.name to exam.grade }
        }.toMap()

        // Find new and updated grades
        newExamsMap.forEach { (examName, newGrade) ->
            val oldGrade = oldExamsMap[examName]

            if (oldGrade == null) {
                // New exam
                if (newGrade.gradeValue.isNotEmpty()) {
                    newGradesList.add("$examName: ${newGrade.gradeValue}")
                }
            } else if (oldGrade.gradeValue != newGrade.gradeValue && newGrade.gradeValue.isNotEmpty()) {
                // Updated grade
                updatedGradesList.add("$examName: ${oldGrade.gradeValue} → ${newGrade.gradeValue}")
            }
        }

        return if (newGradesList.isNotEmpty() || updatedGradesList.isNotEmpty()) {
            GradeChanges(newGradesList, updatedGradesList)
        } else {
            null
        }
    }

    private fun eventsAreEqual(event1: TimetableEvent, event2: TimetableEvent): Boolean {
        return event1.title == event2.title &&
                event1.startTime == event2.startTime &&
                event1.endTime == event2.endTime &&
                event1.room == event2.room &&
                event1.lecturer == event2.lecturer
    }

    private fun TimetableChanges.hasChanges(): Boolean {
        return addedEvents.isNotEmpty() || removedEvents.isNotEmpty() || modifiedEvents.isNotEmpty()
    }

    /**
     * Generate user-friendly change descriptions
     */
    fun formatTimetableChanges(changes: List<TimetableChanges>): List<String> {
        val descriptions = mutableListOf<String>()

        changes.forEach { weekChanges ->
            val weekStr = weekChanges.weekStart.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

            weekChanges.addedEvents.forEach { event ->
                descriptions.add("Added: ${event.title} on $weekStr at ${event.startTime}")
            }

            weekChanges.removedEvents.forEach { event ->
                descriptions.add("Removed: ${event.title} on $weekStr at ${event.startTime}")
            }

            weekChanges.modifiedEvents.forEach { (old, new) ->
                descriptions.add("Changed: ${old.title} → room changed from ${old.room} to ${new.room}")
            }
        }

        return descriptions
    }

    fun formatGradeChanges(changes: GradeChanges): List<String> {
        val descriptions = mutableListOf<String>()
        descriptions.addAll(changes.newGrades)
        descriptions.addAll(changes.updatedGrades)
        return descriptions
    }
}
