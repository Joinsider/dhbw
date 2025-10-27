package de.joinside.dhbw.data.database.entities.timetable.relations

import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.SubjectEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.relations.LectureWithLecturers
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LectureWithLecturersTest {

    @Test
    fun `create LectureWithLecturers with empty lecturers list`() {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 1L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val subject = SubjectEntity(subjectId = 10L, name = "Mathematics")
        val lectureWithLecturers = LectureWithLecturers(
            lecture = lecture,
            lecturers = emptyList(),
            subject = subject
        )

        // Then
        assertEquals(lecture, lectureWithLecturers.lecture)
        assertEquals(subject, lectureWithLecturers.subject)
        assertTrue(lectureWithLecturers.lecturers.isEmpty())
    }

    @Test
    fun `create LectureWithLecturers with single lecturer`() {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 1L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val subject = SubjectEntity(subjectId = 10L, name = "Mathematics")
        val lecturer = LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Dr. Smith")
        val lectureWithLecturers = LectureWithLecturers(
            lecture = lecture,
            lecturers = listOf(lecturer),
            subject = subject
        )

        // Then
        assertEquals(1, lectureWithLecturers.lecturers.size)
        assertEquals("Prof. Dr. Smith", lectureWithLecturers.lecturers[0].lecturerName)
    }

    @Test
    fun `create LectureWithLecturers with multiple lecturers`() {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 1L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val subject = SubjectEntity(subjectId = 10L, name = "Software Engineering")
        val lecturers = listOf(
            LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Dr. Smith"),
            LecturerEntity(lecturerId = 2L, lecturerName = "Dr. Johnson"),
            LecturerEntity(lecturerId = 3L, lecturerName = "Prof. Anderson")
        )
        val lectureWithLecturers = LectureWithLecturers(
            lecture = lecture,
            lecturers = lecturers,
            subject = subject
        )

        // Then
        assertEquals(3, lectureWithLecturers.lecturers.size)
        assertEquals("Prof. Dr. Smith", lectureWithLecturers.lecturers[0].lecturerName)
        assertEquals("Dr. Johnson", lectureWithLecturers.lecturers[1].lecturerName)
        assertEquals("Prof. Anderson", lectureWithLecturers.lecturers[2].lecturerName)
    }

    @Test
    fun `LectureWithLecturers correctly associates subject with lecture`() {
        // Given
        val subjectId = 10L
        val lecture = LectureEventEntity(
            lectureId = 1L,
            subjectId = subjectId,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val subject = SubjectEntity(subjectId = subjectId, name = "Database Systems")
        val lectureWithLecturers = LectureWithLecturers(
            lecture = lecture,
            lecturers = emptyList(),
            subject = subject
        )

        // Then
        assertEquals(lecture.subjectId, lectureWithLecturers.subject.subjectId)
        assertEquals("Database Systems", lectureWithLecturers.subject.name)
    }

    @Test
    fun `two LectureWithLecturers with same data are equal`() {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 1L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val subject = SubjectEntity(subjectId = 10L, name = "Mathematics")
        val lecturers = listOf(LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Dr. Smith"))
        val lectureWithLecturers1 = LectureWithLecturers(lecture, lecturers, subject)
        val lectureWithLecturers2 = LectureWithLecturers(lecture, lecturers, subject)

        // Then
        assertEquals(lectureWithLecturers1, lectureWithLecturers2)
    }

    @Test
    fun `two LectureWithLecturers with different lectures are not equal`() {
        // Given
        val lecture1 = LectureEventEntity(
            lectureId = 1L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val lecture2 = LectureEventEntity(
            lectureId = 2L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 16, 10, 0),
            endTime = LocalDateTime(2024, 1, 16, 12, 0),
            location = "Room B202",
            isTest = false
        )
        val subject = SubjectEntity(subjectId = 10L, name = "Mathematics")
        val lecturers = listOf(LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Dr. Smith"))
        val lectureWithLecturers1 = LectureWithLecturers(lecture1, lecturers, subject)
        val lectureWithLecturers2 = LectureWithLecturers(lecture2, lecturers, subject)

        // Then
        assertNotEquals(lectureWithLecturers1, lectureWithLecturers2)
    }

    @Test
    fun `two LectureWithLecturers with different lecturers are not equal`() {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 1L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val subject = SubjectEntity(subjectId = 10L, name = "Mathematics")
        val lecturers1 = listOf(LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Dr. Smith"))
        val lecturers2 = listOf(LecturerEntity(lecturerId = 2L, lecturerName = "Dr. Johnson"))
        val lectureWithLecturers1 = LectureWithLecturers(lecture, lecturers1, subject)
        val lectureWithLecturers2 = LectureWithLecturers(lecture, lecturers2, subject)

        // Then
        assertNotEquals(lectureWithLecturers1, lectureWithLecturers2)
    }

    @Test
    fun `LectureWithLecturers handles test lectures`() {
        // Given
        val testLecture = LectureEventEntity(
            lectureId = 1L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = true
        )
        val subject = SubjectEntity(subjectId = 10L, name = "Mathematics")
        val lecturer = LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Dr. Smith")
        val lectureWithLecturers = LectureWithLecturers(
            lecture = testLecture,
            lecturers = listOf(lecturer),
            subject = subject
        )

        // Then
        assertTrue(lectureWithLecturers.lecture.isTest)
        assertEquals("Mathematics", lectureWithLecturers.subject.name)
        assertEquals(1, lectureWithLecturers.lecturers.size)
    }

    @Test
    fun `copy LectureWithLecturers with modified lecturers list`() {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 1L,
            subjectId = 10L,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val subject = SubjectEntity(subjectId = 10L, name = "Mathematics")
        val originalLecturers = listOf(LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Dr. Smith"))
        val original = LectureWithLecturers(lecture, originalLecturers, subject)

        // When
        val newLecturers = listOf(
            LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Dr. Smith"),
            LecturerEntity(lecturerId = 2L, lecturerName = "Dr. Johnson")
        )
        val modified = original.copy(lecturers = newLecturers)

        // Then
        assertEquals(1, original.lecturers.size)
        assertEquals(2, modified.lecturers.size)
        assertNotEquals(original, modified)
    }
}

