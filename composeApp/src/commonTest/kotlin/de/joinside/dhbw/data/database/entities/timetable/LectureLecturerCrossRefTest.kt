package de.joinside.dhbw.data.database.entities.timetable

import de.joinside.dhbw.data.storage.database.entities.timetable.LectureLecturerCrossRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LectureLecturerCrossRefTest {

    @Test
    fun `create LectureLecturerCrossRef with valid ids`() {
        // Given
        val crossRef = LectureLecturerCrossRef(
            lectureId = 1L,
            lecturerId = 10L
        )

        // Then
        assertEquals(1L, crossRef.lectureId)
        assertEquals(10L, crossRef.lecturerId)
    }

    @Test
    fun `two LectureLecturerCrossRef with same values are equal`() {
        // Given
        val crossRef1 = LectureLecturerCrossRef(lectureId = 1L, lecturerId = 10L)
        val crossRef2 = LectureLecturerCrossRef(lectureId = 1L, lecturerId = 10L)

        // Then
        assertEquals(crossRef1, crossRef2)
        assertEquals(crossRef1.hashCode(), crossRef2.hashCode())
    }

    @Test
    fun `two LectureLecturerCrossRef with different lectureId are not equal`() {
        // Given
        val crossRef1 = LectureLecturerCrossRef(lectureId = 1L, lecturerId = 10L)
        val crossRef2 = LectureLecturerCrossRef(lectureId = 2L, lecturerId = 10L)

        // Then
        assertNotEquals(crossRef1, crossRef2)
    }

    @Test
    fun `two LectureLecturerCrossRef with different lecturerId are not equal`() {
        // Given
        val crossRef1 = LectureLecturerCrossRef(lectureId = 1L, lecturerId = 10L)
        val crossRef2 = LectureLecturerCrossRef(lectureId = 1L, lecturerId = 20L)

        // Then
        assertNotEquals(crossRef1, crossRef2)
    }

    @Test
    fun `copy LectureLecturerCrossRef with modified lectureId`() {
        // Given
        val originalCrossRef = LectureLecturerCrossRef(lectureId = 1L, lecturerId = 10L)

        // When
        val modifiedCrossRef = originalCrossRef.copy(lectureId = 2L)

        // Then
        assertEquals(2L, modifiedCrossRef.lectureId)
        assertEquals(originalCrossRef.lecturerId, modifiedCrossRef.lecturerId)
        assertNotEquals(originalCrossRef, modifiedCrossRef)
    }

    @Test
    fun `copy LectureLecturerCrossRef with modified lecturerId`() {
        // Given
        val originalCrossRef = LectureLecturerCrossRef(lectureId = 1L, lecturerId = 10L)

        // When
        val modifiedCrossRef = originalCrossRef.copy(lecturerId = 20L)

        // Then
        assertEquals(20L, modifiedCrossRef.lecturerId)
        assertEquals(originalCrossRef.lectureId, modifiedCrossRef.lectureId)
        assertNotEquals(originalCrossRef, modifiedCrossRef)
    }

    @Test
    fun `multiple LectureLecturerCrossRef can represent many-to-many relationship`() {
        // Given - one lecture with multiple lecturers
        val lectureId = 1L
        val crossRefs = listOf(
            LectureLecturerCrossRef(lectureId = lectureId, lecturerId = 10L),
            LectureLecturerCrossRef(lectureId = lectureId, lecturerId = 20L),
            LectureLecturerCrossRef(lectureId = lectureId, lecturerId = 30L)
        )

        // Then
        assertEquals(3, crossRefs.size)
        assertTrue(crossRefs.all { it.lectureId == lectureId })
        assertEquals(setOf(10L, 20L, 30L), crossRefs.map { it.lecturerId }.toSet())
    }

    @Test
    fun `multiple LectureLecturerCrossRef can represent one lecturer teaching multiple lectures`() {
        // Given - one lecturer teaching multiple lectures
        val lecturerId = 10L
        val crossRefs = listOf(
            LectureLecturerCrossRef(lectureId = 1L, lecturerId = lecturerId),
            LectureLecturerCrossRef(lectureId = 2L, lecturerId = lecturerId),
            LectureLecturerCrossRef(lectureId = 3L, lecturerId = lecturerId)
        )

        // Then
        assertEquals(3, crossRefs.size)
        assertTrue(crossRefs.all { it.lecturerId == lecturerId })
        assertEquals(setOf(1L, 2L, 3L), crossRefs.map { it.lectureId }.toSet())
    }
}


