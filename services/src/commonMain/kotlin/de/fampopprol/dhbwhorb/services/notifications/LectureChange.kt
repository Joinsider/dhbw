/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import kotlinx.datetime.LocalDateTime

/**
 * One thing that changed about one lecture.
 *
 * Every case carries [occursAt], the moment the lecture it talks about starts — for [TimeChange]
 * the *new* one. It is what makes [notificationKey] unique: notifications are addressed by id on
 * both platforms, and two changes that share an id replace each other silently.
 */
sealed class LectureChange {
    abstract val lectureId: Long
    abstract val courseName: String

    /** When the lecture this change is about starts. */
    abstract val occursAt: LocalDateTime

    /**
     * The id the platform notification is filed under.
     *
     * Not the database id: a new lecture has none yet (it is assigned on insert, after the
     * notification is built), so every new lecture used to arrive as `lecture_0` and overwrite the
     * previous one. Subject plus start time identifies the lecture without needing a row.
     */
    val notificationKey: String get() = "lecture_${courseName}_$occursAt"

    /** The lecture moved — a different start, a different end, or both. */
    data class TimeChange(
        override val lectureId: Long,
        override val courseName: String,
        val oldStartTime: LocalDateTime?,
        val newStartTime: LocalDateTime,
        val oldEndTime: LocalDateTime?,
        val newEndTime: LocalDateTime
    ) : LectureChange() {
        override val occursAt: LocalDateTime get() = newStartTime
    }

    /** Lecturers added, removed or swapped. */
    data class LecturerChange(
        override val lectureId: Long,
        override val courseName: String,
        override val occursAt: LocalDateTime,
        val oldLecturers: List<String>,
        val newLecturers: List<String>
    ) : LectureChange()

    /** A lecture became an exam, or an exam became a lecture. */
    data class TypeChange(
        override val lectureId: Long,
        override val courseName: String,
        override val occursAt: LocalDateTime,
        val oldIsTest: Boolean,
        val newIsTest: Boolean
    ) : LectureChange()

    /** A different room. */
    data class LocationChange(
        override val lectureId: Long,
        override val courseName: String,
        override val occursAt: LocalDateTime,
        val oldLocation: String,
        val newLocation: String
    ) : LectureChange()

    /** The lecture is gone from the timetable and nothing took its place. */
    data class Cancellation(
        override val lectureId: Long,
        override val courseName: String,
        val cancelledLecture: LectureEventEntity
    ) : LectureChange() {
        override val occursAt: LocalDateTime get() = cancelledLecture.startTime
    }

    /** A lecture that was not in the timetable before. */
    data class NewLecture(
        override val lectureId: Long,
        override val courseName: String,
        val lecture: LectureEventEntity
    ) : LectureChange() {
        override val occursAt: LocalDateTime get() = lecture.startTime
    }
}

/** What one monitoring run found. */
sealed class MonitorResult {
    data class Changes(val changes: List<LectureChange>) : MonitorResult()
    data class NoChanges(val lecturesChecked: Int) : MonitorResult()

    /**
     * The run could not complete.
     *
     * Carries an [AppError] rather than a message string, so a caller can decide whether retrying
     * makes sense — the Android worker used to search the text for the words "network" and "auth".
     */
    data class Error(val error: AppError) : MonitorResult()
}
