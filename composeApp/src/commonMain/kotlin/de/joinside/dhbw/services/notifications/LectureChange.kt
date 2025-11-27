/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.services.notifications

import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import kotlinx.datetime.LocalDateTime

/**
 * Sealed class hierarchy representing different types of lecture changes
 */
sealed class LectureChange {
    abstract val lectureId: Long
    abstract val courseName: String

    /**
     * Time change: start time, end time, or both changed
     */
    data class TimeChange(
        override val lectureId: Long,
        override val courseName: String,
        val oldStartTime: LocalDateTime?,
        val newStartTime: LocalDateTime,
        val oldEndTime: LocalDateTime?,
        val newEndTime: LocalDateTime
    ) : LectureChange()

    /**
     * Lecturer change: lecturers added, removed, or changed
     */
    data class LecturerChange(
        override val lectureId: Long,
        override val courseName: String,
        val oldLecturers: List<String>,
        val newLecturers: List<String>
    ) : LectureChange()

    /**
     * Type change: lecture <-> test/exam
     */
    data class TypeChange(
        override val lectureId: Long,
        override val courseName: String,
        val oldIsTest: Boolean,
        val newIsTest: Boolean
    ) : LectureChange()

    /**
     * Location change: room or building changed
     */
    data class LocationChange(
        override val lectureId: Long,
        override val courseName: String,
        val oldLocation: String,
        val newLocation: String
    ) : LectureChange()

    /**
     * Cancellation: lecture cancelled (detected and confirmed after delay)
     */
    data class Cancellation(
        override val lectureId: Long,
        override val courseName: String,
        val cancelledLecture: LectureEventEntity,
        val confirmedAfterDelay: Boolean = true
    ) : LectureChange()

    /**
     * New lecture: lecture added that wasn't in database
     */
    data class NewLecture(
        override val lectureId: Long,
        override val courseName: String,
        val lecture: LectureEventEntity
    ) : LectureChange()
}

/**
 * Result of a lecture monitoring check
 */
sealed class MonitorResult {
    data class Changes(val changes: List<LectureChange>) : MonitorResult()
    data class NoChanges(val lecturesChecked: Int) : MonitorResult()
    data class Error(val message: String, val exception: Throwable? = null) : MonitorResult()
}

