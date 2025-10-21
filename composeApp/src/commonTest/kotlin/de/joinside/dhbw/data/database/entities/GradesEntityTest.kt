package de.joinside.dhbw.data.database.entities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GradesEntityTest {

    @Test
    fun `create GradesEntity with all properties`() {
        // Given
        val grade = GradesEntity(
            gradesId = 1L, name = "Mathematics", gradeValue = 1.7, semesterName = "WS2023"
        )

        // Then
        assertEquals(1L, grade.gradesId)
        assertEquals("Mathematics", grade.name)
        assertEquals(1.7, grade.gradeValue)
        assertEquals("WS2023", grade.semesterName)
    }

    @Test
    fun `create GradesEntity with default gradesId`() {
        // Given
        val grade = GradesEntity(
            name = "Physics", gradeValue = 2.0, semesterName = "SS2024"
        )

        // Then
        assertEquals(0L, grade.gradesId)
        assertEquals("Physics", grade.name)
        assertEquals(2.0, grade.gradeValue)
        assertEquals("SS2024", grade.semesterName)
    }

    @Test
    fun `two GradesEntity with same values are equal`() {
        // Given
        val grade1 = GradesEntity(
            gradesId = 1L, name = "Chemistry", gradeValue = 1.3, semesterName = "WS2023"
        )
        val grade2 = GradesEntity(
            gradesId = 1L, name = "Chemistry", gradeValue = 1.3, semesterName = "WS2023"
        )

        // Then
        assertEquals(grade1, grade2)
        assertEquals(grade1.hashCode(), grade2.hashCode())
    }

    @Test
    fun `two GradesEntity with different values are not equal`() {
        // Given
        val grade1 = GradesEntity(
            gradesId = 1L, name = "Chemistry", gradeValue = 1.3, semesterName = "WS2023"
        )
        val grade2 = GradesEntity(
            gradesId = 2L, name = "Biology", gradeValue = 2.0, semesterName = "SS2024"
        )

        // Then
        assertNotEquals(grade1, grade2)
    }

    @Test
    fun `copy GradesEntity with modified properties`() {
        // Given
        val originalGrade = GradesEntity(
            gradesId = 1L, name = "English", gradeValue = 2.3, semesterName = "WS2023"
        )

        // When
        val modifiedGrade = originalGrade.copy(gradeValue = 1.7)

        // Then
        assertEquals(1L, modifiedGrade.gradesId)
        assertEquals("English", modifiedGrade.name)
        assertEquals(1.7, modifiedGrade.gradeValue)
        assertEquals("WS2023", modifiedGrade.semesterName)
        assertNotEquals(originalGrade, modifiedGrade)
    }

    @Test
    fun `test valid grade values`() {
        // Given valid grade range (1.0 to 5.0 in German grading system)
        val excellentGrade = GradesEntity(
            name = "Computer Science", gradeValue = 1.0, semesterName = "WS2023"
        )
        val poorGrade = GradesEntity(
            name = "History", gradeValue = 5.0, semesterName = "WS2023"
        )

        // Then
        assertEquals(1.0, excellentGrade.gradeValue)
        assertEquals(5.0, poorGrade.gradeValue)
    }
}