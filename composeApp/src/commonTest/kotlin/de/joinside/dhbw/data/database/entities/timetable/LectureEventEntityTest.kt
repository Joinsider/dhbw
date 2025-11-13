package de.joinside.dhbw.data.database.entities.timetable

import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LectureEventEntityTest {

    @Test
    fun `create LectureEventEntity with all properties`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val lecture = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "Mathematics",
            startTime = startTime,
            endTime = endTime,
            location = "Room A101",
            isTest = false,
        )

        // Then
        assertEquals(1L, lecture.lectureId)
        assertEquals("Mathematics", lecture.shortSubjectName)
        assertEquals(startTime, lecture.startTime)
        assertEquals(endTime, lecture.endTime)
        assertEquals("Room A101", lecture.location)
        assertFalse(lecture.isTest)
    }

    @Test
    fun `create LectureEventEntity with isTest true`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val lecture = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "Physics",
            startTime = startTime,
            endTime = endTime,
            location = "Room B202",
            isTest = true,
        )

        // Then
        assertTrue(lecture.isTest)
    }

    @Test
    fun `create LectureEventEntity with default isTest value`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val lecture = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "Chemistry",
            startTime = startTime,
            endTime = endTime,
            location = "Room A101",
        )

        // Then
        assertFalse(lecture.isTest) // default value should be false
    }

    @Test
    fun `two LectureEventEntity with same values are equal`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val lecture1 = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "Biology",
            startTime = startTime,
            endTime = endTime,
            location = "Room A101",
            isTest = false,
        )
        val lecture2 = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "Biology",
            startTime = startTime,
            endTime = endTime,
            location = "Room A101",
            isTest = false,
        )

        // Then
        assertEquals(lecture1, lecture2)
        assertEquals(lecture1.hashCode(), lecture2.hashCode())
    }

    @Test
    fun `two LectureEventEntity with different lectureId are not equal`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val lecture1 = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "History",
            startTime = startTime,
            endTime = endTime,
            location = "Room A101",
            isTest = false,
        )
        val lecture2 = LectureEventEntity(
            lectureId = 2L,
            shortSubjectName = "History",
            startTime = startTime,
            endTime = endTime,
            location = "Room A101",
            isTest = false,
        )

        // Then
        assertNotEquals(lecture1, lecture2)
    }

    @Test
    fun `two LectureEventEntity with different times are not equal`() {
        // Given
        val startTime1 = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime1 = LocalDateTime(2024, 1, 15, 12, 0)
        val startTime2 = LocalDateTime(2024, 1, 15, 14, 0)
        val endTime2 = LocalDateTime(2024, 1, 15, 16, 0)
        val lecture1 = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "Geography",
            startTime = startTime1,
            endTime = endTime1,
            location = "Room A101",
            isTest = false,
        )
        val lecture2 = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "Geography",
            startTime = startTime2,
            endTime = endTime2,
            location = "Room A101",
            isTest = false,
        )

        // Then
        assertNotEquals(lecture1, lecture2)
    }

    @Test
    fun `copy LectureEventEntity with modified location`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val originalLecture = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "Computer Science",
            startTime = startTime,
            endTime = endTime,
            location = "Room A101",
            isTest = false,
        )

        // When
        val modifiedLecture = originalLecture.copy(location = "Room B202")

        // Then
        assertEquals("Room B202", modifiedLecture.location)
        assertEquals(originalLecture.lectureId, modifiedLecture.lectureId)
        assertEquals(originalLecture.shortSubjectName, modifiedLecture.shortSubjectName)
        assertNotEquals(originalLecture, modifiedLecture)
    }

    @Test
    fun `copy LectureEventEntity with modified isTest`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val originalLecture = LectureEventEntity(
            lectureId = 1L,
            shortSubjectName = "English",
            startTime = startTime,
            endTime = endTime,
            location = "Room A101",
            isTest = false,
        )

        // When
        val modifiedLecture = originalLecture.copy(isTest = true)

        // Then
        assertTrue(modifiedLecture.isTest)
        assertFalse(originalLecture.isTest)
        assertNotEquals(originalLecture, modifiedLecture)
    }

    @Test
    fun `LectureEventEntity handles different locations`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val locations = listOf("Room A101", "Online", "Building C - Room 305", "Lab 1")

        // When & Then
        locations.forEach { location ->
            val lecture = LectureEventEntity(
                lectureId = 1L,
                shortSubjectName = "Art",
                startTime = startTime,
                endTime = endTime,
                location = location,
                isTest = false,
            )
            assertEquals(location, lecture.location)
        }
    }

    @Test
    fun `LectureEventEntity handles different subject names`() {
        // Given
        val startTime = LocalDateTime(2024, 1, 15, 10, 0)
        val endTime = LocalDateTime(2024, 1, 15, 12, 0)
        val subjects = listOf("Mathematics", "Physics", "Chemistry", "Computer Science")

        // When & Then
        subjects.forEach { subject ->
            val lecture = LectureEventEntity(
                lectureId = 1L,
                shortSubjectName = subject,
                startTime = startTime,
                endTime = endTime,
                location = "Room A101",
                isTest = false,
            )
            assertEquals(subject, lecture.shortSubjectName)
        }
    }
}