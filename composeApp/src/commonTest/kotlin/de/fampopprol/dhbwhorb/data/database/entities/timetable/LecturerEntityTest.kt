package de.fampopprol.dhbwhorb.data.database.entities.timetable

import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LecturerEntityTest {

    @Test
    fun `create LecturerEntity with all properties`() {
        // Given
        val lecturer = LecturerEntity(
            lecturerId = 1L,
            lecturerName = "Prof. Dr. Smith"
        )

        // Then
        assertEquals(1L, lecturer.lecturerId)
        assertEquals("Prof. Dr. Smith", lecturer.lecturerName)
    }

    @Test
    fun `create LecturerEntity with default lecturerId`() {
        // Given
        val lecturer = LecturerEntity(
            lecturerId = 0L,
            lecturerName = "Dr. Johnson"
        )

        // Then
        assertEquals(0L, lecturer.lecturerId)
        assertEquals("Dr. Johnson", lecturer.lecturerName)
    }

    @Test
    fun `two LecturerEntity with same values are equal`() {
        // Given
        val lecturer1 = LecturerEntity(
            lecturerId = 1L,
            lecturerName = "Prof. Anderson"
        )
        val lecturer2 = LecturerEntity(
            lecturerId = 1L,
            lecturerName = "Prof. Anderson"
        )

        // Then
        assertEquals(lecturer1, lecturer2)
        assertEquals(lecturer1.hashCode(), lecturer2.hashCode())
    }

    @Test
    fun `two LecturerEntity with different lecturerId are not equal`() {
        // Given
        val lecturer1 = LecturerEntity(lecturerId = 1L, lecturerName = "Dr. Brown")
        val lecturer2 = LecturerEntity(lecturerId = 2L, lecturerName = "Dr. Brown")

        // Then
        assertNotEquals(lecturer1, lecturer2)
    }

    @Test
    fun `two LecturerEntity with different lecturerName are not equal`() {
        // Given
        val lecturer1 = LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Wilson")
        val lecturer2 = LecturerEntity(lecturerId = 1L, lecturerName = "Prof. Davis")

        // Then
        assertNotEquals(lecturer1, lecturer2)
    }

    @Test
    fun `copy LecturerEntity with modified lecturerName`() {
        // Given
        val originalLecturer = LecturerEntity(
            lecturerId = 1L,
            lecturerName = "Dr. Taylor"
        )

        // When
        val modifiedLecturer = originalLecturer.copy(lecturerName = "Prof. Dr. Taylor")

        // Then
        assertEquals("Prof. Dr. Taylor", modifiedLecturer.lecturerName)
        assertEquals(originalLecturer.lecturerId, modifiedLecturer.lecturerId)
        assertNotEquals(originalLecturer, modifiedLecturer)
    }

    @Test
    fun `copy LecturerEntity with modified lecturerId`() {
        // Given
        val originalLecturer = LecturerEntity(
            lecturerId = 1L,
            lecturerName = "Dr. Martinez"
        )

        // When
        val modifiedLecturer = originalLecturer.copy(lecturerId = 2L)

        // Then
        assertEquals(2L, modifiedLecturer.lecturerId)
        assertEquals(originalLecturer.lecturerName, modifiedLecturer.lecturerName)
        assertNotEquals(originalLecturer, modifiedLecturer)
    }

    @Test
    fun `LecturerEntity handles various name formats`() {
        // Given
        val names = listOf(
            "Prof. Dr. Smith",
            "Dr. Johnson",
            "John Doe",
            "Prof. Anderson",
            "Dr.-Ing. Müller"
        )

        // When & Then
        names.forEachIndexed { index, name ->
            val lecturer = LecturerEntity(lecturerId = index.toLong(), lecturerName = name)
            assertEquals(name, lecturer.lecturerName)
        }
    }
}

