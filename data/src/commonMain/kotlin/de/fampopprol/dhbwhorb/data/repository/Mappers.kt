/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Lecture

/**
 * Room entities to domain models.
 *
 * This is the boundary that keeps `@Entity` and `@Relation` out of `:domain` — which is in turn
 * what lets `Shared.framework` expose the domain to Swift without dragging Room along.
 */

/**
 * @param lecturers taken from the transient field the fetch path fills in; for a lecture read
 *   back from the database use the [LectureWithLecturers] overload instead, which has the
 *   junction table joined in.
 */
fun LectureEventEntity.toDomain(lecturers: List<String> = this.lecturers.orEmpty()): Lecture =
    Lecture(
        id = lectureId,
        shortName = shortSubjectName,
        fullName = fullSubjectName,
        start = startTime,
        end = endTime,
        location = location,
        isTest = isTest,
        lecturers = lecturers
    )

fun LectureWithLecturers.toDomain(): Lecture =
    lecture.toDomain(lecturers = lecturers.map { it.lecturerName })

fun GradeEntity.toDomain(): GradeEntry =
    GradeEntry(
        semesterId = semesterId,
        semesterName = semesterName,
        moduleNumber = moduleNumber,
        moduleName = moduleName,
        grade = grade,
        credits = credits,
        status = status,
        resultId = resultId
    )
