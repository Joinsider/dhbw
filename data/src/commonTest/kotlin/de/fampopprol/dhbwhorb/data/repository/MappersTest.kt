/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {

    private val start = LocalDateTime(2026, 3, 2, 8, 0)
    private val end = LocalDateTime(2026, 3, 2, 9, 30)

    @Test
    fun lectureEventEntity_toDomain_usesItsOwnTransientLecturersByDefault() {
        val entity = LectureEventEntity(
            lectureId = 1,
            shortSubjectName = "MA3",
            fullSubjectName = "Mathematik III",
            startTime = start,
            endTime = end,
            location = "Room 101",
            isTest = false
        ).apply { lecturers = listOf("Prof. Muster") }

        val domain = entity.toDomain()

        assertEquals(1, domain.id)
        assertEquals("MA3", domain.shortName)
        assertEquals("Mathematik III", domain.fullName)
        assertEquals("Room 101", domain.location)
        assertEquals(listOf("Prof. Muster"), domain.lecturers)
    }

    @Test
    fun lectureEventEntity_toDomain_withNullTransientLecturers_defaultsToEmpty() {
        val entity = LectureEventEntity(
            lectureId = 2,
            shortSubjectName = "MA3",
            fullSubjectName = null,
            startTime = start,
            endTime = end,
            location = "Room 101",
            isTest = true
        )

        val domain = entity.toDomain()

        assertEquals(emptyList(), domain.lecturers)
        assertEquals(true, domain.isTest)
    }

    @Test
    fun lectureEventEntity_toDomain_acceptsAnExplicitLecturerList() {
        val entity = LectureEventEntity(
            lectureId = 3,
            shortSubjectName = "MA3",
            fullSubjectName = null,
            startTime = start,
            endTime = end,
            location = "Room 101"
        )

        val domain = entity.toDomain(lecturers = listOf("Explicit"))

        assertEquals(listOf("Explicit"), domain.lecturers)
    }

    @Test
    fun lectureWithLecturers_toDomain_joinsTheLecturerNames() {
        val entity = LectureEventEntity(
            lectureId = 4,
            shortSubjectName = "MA3",
            fullSubjectName = "Mathematik III",
            startTime = start,
            endTime = end,
            location = "Room 101"
        )
        val withLecturers = LectureWithLecturers(
            lecture = entity,
            lecturers = listOf(
                LecturerEntity(lecturerId = 1, lecturerName = "Prof. A"),
                LecturerEntity(lecturerId = 2, lecturerName = "Prof. B")
            )
        )

        val domain = withLecturers.toDomain()

        assertEquals(listOf("Prof. A", "Prof. B"), domain.lecturers)
        assertEquals("Mathematik III", domain.fullName)
    }

    @Test
    fun gradeEntity_toDomain_copiesEveryField() {
        val entity = GradeEntity(
            id = 7,
            studentId = "s1",
            semesterId = "sem1",
            semesterName = "SoSe 2025",
            moduleNumber = "T4INF2001",
            moduleName = "Mathematik III",
            grade = "1,3",
            credits = 5.0,
            status = "bestanden",
            resultId = "res-1"
        )

        val domain = entity.toDomain()

        assertEquals("sem1", domain.semesterId)
        assertEquals("SoSe 2025", domain.semesterName)
        assertEquals("T4INF2001", domain.moduleNumber)
        assertEquals("Mathematik III", domain.moduleName)
        assertEquals("1,3", domain.grade)
        assertEquals(5.0, domain.credits)
        assertEquals("bestanden", domain.status)
        assertEquals("res-1", domain.resultId)
    }
}
