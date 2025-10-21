package de.joinside.dhbw.data.database.entities

import de.joinside.dhbw.data.database.entities.grades.SemesterEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SemesterEntityTest {

    @Test
    fun `create SemesterEntity with semesterName`() {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")

        // Then
        assertEquals("WS2023", semester.semesterName)
    }

    @Test
    fun `two SemesterEntity with same semesterName are equal`() {
        // Given
        val semester1 = SemesterEntity(semesterName = "WS2023")
        val semester2 = SemesterEntity(semesterName = "WS2023")

        // Then
        assertEquals(semester1, semester2)
        assertEquals(semester1.hashCode(), semester2.hashCode())
    }

    @Test
    fun `two SemesterEntity with different semesterName are not equal`() {
        // Given
        val semester1 = SemesterEntity(semesterName = "WS2023")
        val semester2 = SemesterEntity(semesterName = "SS2024")

        // Then
        assertNotEquals(semester1, semester2)
    }

    @Test
    fun `copy SemesterEntity with modified semesterName`() {
        // Given
        val originalSemester = SemesterEntity(semesterName = "WS2023")

        // When
        val modifiedSemester = originalSemester.copy(semesterName = "SS2024")

        // Then
        assertEquals("SS2024", modifiedSemester.semesterName)
        assertNotEquals(originalSemester, modifiedSemester)
    }

    @Test
    fun `test various semester naming conventions`() {
        // Given different semester formats
        val winterSemester = SemesterEntity(semesterName = "WS2023")
        val summerSemester = SemesterEntity(semesterName = "SS2024")
        val semesterWithNumber = SemesterEntity(semesterName = "Semester 1")
        val semesterWithFullName = SemesterEntity(semesterName = "Winter Semester 2023/2024")

        // Then
        assertEquals("WS2023", winterSemester.semesterName)
        assertEquals("SS2024", summerSemester.semesterName)
        assertEquals("Semester 1", semesterWithNumber.semesterName)
        assertEquals("Winter Semester 2023/2024", semesterWithFullName.semesterName)
    }
}

