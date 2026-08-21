/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
import io.github.aakira.napier.Napier

/**
 * Detects what changed between the timetable Dualis serves now and the one in the local cache.
 *
 * Compares first and writes second: the fetched week only replaces the cached one once a change
 * has been found, so an unchanged week costs no database writes and the "old" side of every
 * comparison is genuinely the previous state.
 */
class LectureChangeMonitor(
    private val dualisLectureServiceFactory: () -> DualisLectureService,
    private val lectureEventDao: LectureEventDao,
    private val lectureLecturerCrossRefDao: LectureLecturerCrossRefDao
) {
    private val dualisLectureService by lazy { dualisLectureServiceFactory() }

    companion object {
        private const val TAG = "LectureChangeMonitor"
    }

    /**
     * Check for lecture changes by comparing current Dualis data with database.
     * Detects time changes, lecturer changes, type changes, location changes, and cancellations.
     *
     * @return MonitorResult containing detected changes or errors
     */
    suspend fun checkForChanges(): MonitorResult {
        try {
            Napier.d("🔍 Starting lecture change check", tag = TAG)

            // Get current week date range for filtering
            val (weekStart, weekEnd) = TimeHelper.getCurrentWeekDates()
            Napier.d("📅 Current week range: $weekStart to $weekEnd", tag = TAG)

            // Step 1: Get OLD stored lectures from database BEFORE fetching from Dualis
            Napier.d(
                "💾 Step 1: Retrieving OLD lectures from database (current week only)...",
                tag = TAG
            )
            val allStoredLecturesOld = lectureEventDao.getAllWithLecturers()
            val storedLecturesOld = allStoredLecturesOld.filter { lectureWithLecturers ->
                val lecture = lectureWithLecturers.lecture
                // Check if lecture overlaps with current week
                lecture.startTime < weekEnd && lecture.endTime > weekStart
            }
            Napier.d(
                "✅ Retrieved ${storedLecturesOld.size} OLD lectures from database (filtered from ${allStoredLecturesOld.size} total)",
                tag = TAG
            )

            // Step 2: Fetch current lectures from Dualis (IN MEMORY, not saved to DB yet)
            Napier.d("📥 Step 2: Fetching current lectures from Dualis (in memory)...", tag = TAG)
            val newLectures: List<LectureEventEntity> =
                when (val fetched = dualisLectureService.getWeeklyLecturesForWeek(weekStart, weekEnd)) {
                    is Outcome.Ok -> fetched.value
                    is Outcome.Err -> {
                        Napier.e("❌ Failed to fetch current lectures: ${fetched.error}", tag = TAG)
                        return MonitorResult.Error("Failed to fetch lectures: ${fetched.error}", null)
                    }
                }
            Napier.d(
                "✅ Fetched ${newLectures.size} lectures from Dualis (in memory, not saved yet)",
                tag = TAG
            )

            // Step 3: Compare OLD (from DB) with NEW (from Dualis in memory)
            Napier.d("🔍 Step 3: Detecting changes...", tag = TAG)
            val changes = mutableListOf<LectureChange>()

            // Convert new lectures to map for comparison (use the same comparison key as DB)
            val newLectureMap: Map<String, LectureEventEntity> = newLectures.associateBy {
                it.toComparisonKey()
            }

            Napier.d("📊 Comparison setup:", tag = TAG)
            Napier.d("   OLD lectures: ${storedLecturesOld.size} from database", tag = TAG)
            Napier.d("   NEW lectures: ${newLectures.size} from Dualis (in memory)", tag = TAG)

            // Create old lecture map for comparison
            val oldLectureMap = storedLecturesOld.associateBy { it.toComparisonKey() }

            // Check for modifications and deletions (cancellations)
            Napier.d("🔄 Checking for modifications and cancellations...", tag = TAG)
            for (oldLecture in storedLecturesOld) {
                val comparisonKey = oldLecture.toComparisonKey()
                val newLecture = newLectureMap[comparisonKey]

                if (newLecture == null) {
                    // Lecture was cancelled
                    Napier.d("🚫 Cancelled lecture detected: ${oldLecture.lecture.shortSubjectName}", tag = TAG)
                    changes.add(
                        LectureChange.Cancellation(
                            lectureId = oldLecture.lecture.lectureId,
                            courseName = oldLecture.lecture.shortSubjectName,
                            cancelledLecture = oldLecture.lecture,
                            confirmedAfterDelay = false // Not using delay confirmation anymore
                        )
                    )
                } else {
                    // Compare existing lecture for changes (OLD from DB, NEW from memory)
                    Napier.d("🔎 Comparing lecture: ${oldLecture.lecture.shortSubjectName}", tag = TAG)
                    val lectureChanges = detectLectureChangesFromMemory(oldLecture, newLecture)
                    if (lectureChanges.isNotEmpty()) {
                        Napier.d("   📝 Found ${lectureChanges.size} change(s) in this lecture", tag = TAG)
                        lectureChanges.forEach { change ->
                            Napier.d("      - ${change::class.simpleName}", tag = TAG)
                        }
                    } else {
                        Napier.d("   ✅ No changes detected", tag = TAG)
                    }
                    changes.addAll(lectureChanges)
                }
            }

            // Check for new lectures
            Napier.d("➕ Checking for new lectures...", tag = TAG)
            for (newLecture in newLectures) {
                val comparisonKey = newLecture.toComparisonKey()
                if (!oldLectureMap.containsKey(comparisonKey)) {
                    Napier.d("🆕 New lecture detected: ${newLecture.shortSubjectName}", tag = TAG)
                    changes.add(
                        LectureChange.NewLecture(
                            lectureId = 0, // Temporary, will be assigned when saved
                            courseName = newLecture.shortSubjectName,
                            lecture = newLecture
                        )
                    )
                }
            }

            Napier.d(
                "📊 Change detection complete: ${changes.size} total change(s) found",
                tag = TAG
            )
            if (changes.isNotEmpty()) {
                Napier.d("📋 Summary of changes:", tag = TAG)
                changes.groupBy { it::class.simpleName }.forEach { (type, list) ->
                    Napier.d("   - $type: ${list.size}", tag = TAG)
                }
            }

            // Step 4: If changes detected, save new lectures to database
            val savedLectureCount: Int
            if (changes.isNotEmpty()) {
                Napier.d(
                    "💾 Step 4: Changes detected! Saving ${newLectures.size} new lectures to database...",
                    tag = TAG
                )
                when (val saved = dualisLectureService.saveLecturesToDatabase(newLectures, weekStart, weekEnd)) {
                    is Outcome.Ok -> Napier.d("✅ Database updated with new lectures", tag = TAG)
                    is Outcome.Err -> {
                        // The changes were detected against data that is now not persisted, so
                        // the next run would report them again. Better to say so than to notify.
                        Napier.e("❌ Could not persist the new lectures: ${saved.error}", tag = TAG)
                        return MonitorResult.Error("Could not persist lectures: ${saved.error}", null)
                    }
                }
                savedLectureCount = newLectures.size
            } else {
                Napier.d("✅ Step 4: No changes detected, database left untouched", tag = TAG)
                savedLectureCount = storedLecturesOld.size
            }

            return if (changes.isEmpty()) {
                MonitorResult.NoChanges(savedLectureCount)
            } else {
                MonitorResult.Changes(changes)
            }

        } catch (e: Exception) {
            Napier.e("Exception during change check: ${e.message}", e, tag = TAG)
            return MonitorResult.Error("Exception: ${e.message}", e)
        }
    }

    /**
     * Detect specific changes between an old lecture (from DB) and new lecture (from memory).
     *
     * @param oldLecture The old lecture from database (LectureWithLecturers)
     * @param newLecture The new lecture from Dualis in memory (LectureEventEntity with lecturers field)
     */
    private fun detectLectureChangesFromMemory(
        oldLecture: LectureWithLecturers,
        newLecture: LectureEventEntity
    ): List<LectureChange> {
        val changes = mutableListOf<LectureChange>()
        val old = oldLecture.lecture

        // Check time changes
        if (old.startTime != newLecture.startTime || old.endTime != newLecture.endTime) {
            Napier.d("      ⏰ Time change: ${old.startTime} -> ${newLecture.startTime}", tag = TAG)
            changes.add(
                LectureChange.TimeChange(
                    lectureId = old.lectureId,
                    courseName = old.shortSubjectName,
                    oldStartTime = old.startTime,
                    newStartTime = newLecture.startTime,
                    oldEndTime = old.endTime,
                    newEndTime = newLecture.endTime
                )
            )
        }

        // Check location changes
        if (old.location != newLecture.location) {
            Napier.d(
                "      📍 Location change: '${old.location}' -> '${newLecture.location}'",
                tag = TAG
            )
            changes.add(
                LectureChange.LocationChange(
                    lectureId = old.lectureId,
                    courseName = old.shortSubjectName,
                    oldLocation = old.location,
                    newLocation = newLecture.location
                )
            )
        }

        // Check type changes (lecture <-> test)
        if (old.isTest != newLecture.isTest) {
            Napier.d(
                "      📝 Type change: ${if (old.isTest) "Test" else "Lecture"} -> ${if (newLecture.isTest) "Test" else "Lecture"}",
                tag = TAG
            )
            changes.add(
                LectureChange.TypeChange(
                    lectureId = old.lectureId,
                    courseName = old.shortSubjectName,
                    oldIsTest = old.isTest,
                    newIsTest = newLecture.isTest
                )
            )
        }

        // Check lecturer changes
        val oldLecturerNames = oldLecture.lecturers.map { it.lecturerName }
        val newLecturerNames = newLecture.lecturers ?: emptyList()

        if (oldLecturerNames.sorted() != newLecturerNames.sorted()) {
            Napier.d("      👨‍🏫 Lecturer change:", tag = TAG)
            Napier.d("         Old: ${oldLecturerNames.joinToString(", ")}", tag = TAG)
            Napier.d("         New: ${newLecturerNames.joinToString(", ")}", tag = TAG)
            changes.add(
                LectureChange.LecturerChange(
                    lectureId = old.lectureId,
                    courseName = old.shortSubjectName,
                    oldLecturers = oldLecturerNames,
                    newLecturers = newLecturerNames
                )
            )
        }

        return changes
    }

    /**
     * Convert a lecture to a unique key for comparison.
     * Does NOT use lectureId since IDs are auto-generated and change between DB queries.
     */
    private fun LectureEventEntity.toComparisonKey(): String {
        return "${shortSubjectName}_${startTime}_${endTime}"
    }

    /**
     * Convert a lecture with lecturers to a unique key for comparison.
     */
    private fun LectureWithLecturers.toComparisonKey(): String {
        return lecture.toComparisonKey()
    }
}
