package de.joinside.dhbw.data.database.entities.timetable

import de.joinside.dhbw.data.storage.database.entities.timetable.SubjectEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SubjectEntityTest {

    @Test
    fun `create SubjectEntity with all properties`() {
        // Given
        val subject = SubjectEntity(
            subjectId = 1L,
            name = "Mathematics"
        )

        // Then
        assertEquals(1L, subject.subjectId)
        assertEquals("Mathematics", subject.name)
    }

    @Test
    fun `create SubjectEntity with default subjectId`() {
        // Given
        val subject = SubjectEntity(
            subjectId = 0L,
            name = "Physics"
        )

        // Then
        assertEquals(0L, subject.subjectId)
        assertEquals("Physics", subject.name)
    }

    @Test
    fun `two SubjectEntity with same values are equal`() {
        // Given
        val subject1 = SubjectEntity(
            subjectId = 1L,
            name = "Chemistry"
        )
        val subject2 = SubjectEntity(
            subjectId = 1L,
            name = "Chemistry"
        )

        // Then
        assertEquals(subject1, subject2)
        assertEquals(subject1.hashCode(), subject2.hashCode())
    }

    @Test
    fun `two SubjectEntity with different subjectId are not equal`() {
        // Given
        val subject1 = SubjectEntity(subjectId = 1L, name = "Biology")
        val subject2 = SubjectEntity(subjectId = 2L, name = "Biology")

        // Then
        assertNotEquals(subject1, subject2)
    }

    @Test
    fun `two SubjectEntity with different name are not equal`() {
        // Given
        val subject1 = SubjectEntity(subjectId = 1L, name = "Mathematics")
        val subject2 = SubjectEntity(subjectId = 1L, name = "Physics")

        // Then
        assertNotEquals(subject1, subject2)
    }

    @Test
    fun `copy SubjectEntity with modified name`() {
        // Given
        val originalSubject = SubjectEntity(
            subjectId = 1L,
            name = "Computer Science I"
        )

        // When
        val modifiedSubject = originalSubject.copy(name = "Computer Science II")

        // Then
        assertEquals("Computer Science II", modifiedSubject.name)
        assertEquals(originalSubject.subjectId, modifiedSubject.subjectId)
        assertNotEquals(originalSubject, modifiedSubject)
    }

    @Test
    fun `copy SubjectEntity with modified subjectId`() {
        // Given
        val originalSubject = SubjectEntity(
            subjectId = 1L,
            name = "Mathematics"
        )

        // When
        val modifiedSubject = originalSubject.copy(subjectId = 2L)

        // Then
        assertEquals(2L, modifiedSubject.subjectId)
        assertEquals(originalSubject.name, modifiedSubject.name)
        assertNotEquals(originalSubject, modifiedSubject)
    }

    @Test
    fun `SubjectEntity handles various subject names`() {
        // Given
        val subjects = listOf(
            "Mathematics",
            "Advanced Programming",
            "Database Systems I",
            "Software Engineering & Project Management",
            "Theoretical Computer Science"
        )

        // When & Then
        subjects.forEachIndexed { index, name ->
            val subject = SubjectEntity(subjectId = index.toLong(), name = name)
            assertEquals(name, subject.name)
        }
    }
}

