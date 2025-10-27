package de.joinside.dhbw.data.database.entities.grades.relations

import de.joinside.dhbw.data.storage.database.entities.grades.GradesEntity
import de.joinside.dhbw.data.storage.database.entities.grades.SemesterEntity
import de.joinside.dhbw.data.storage.database.entities.grades.relations.SemesterWithGrades
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SemesterWithGradesTest {

    @Test
    fun `create SemesterWithGrades with empty grades list`() {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")
        val semesterWithGrades = SemesterWithGrades(
            semester = semester,
            grades = emptyList()
        )

        // Then
        assertEquals(semester, semesterWithGrades.semester)
        assertTrue(semesterWithGrades.grades.isEmpty())
    }

    @Test
    fun `create SemesterWithGrades with multiple grades`() {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")
        val grades = listOf(
            GradesEntity(
                gradesId = 1L,
                name = "Mathematics",
                gradeValue = 1.7,
                semesterName = "WS2023"
            ),
            GradesEntity(
                gradesId = 2L,
                name = "Physics",
                gradeValue = 2.0,
                semesterName = "WS2023"
            ),
            GradesEntity(
                gradesId = 3L,
                name = "Chemistry",
                gradeValue = 1.3,
                semesterName = "WS2023"
            )
        )
        val semesterWithGrades = SemesterWithGrades(
            semester = semester,
            grades = grades
        )

        // Then
        assertEquals("WS2023", semesterWithGrades.semester.semesterName)
        assertEquals(3, semesterWithGrades.grades.size)
        assertEquals("Mathematics", semesterWithGrades.grades[0].name)
        assertEquals("Physics", semesterWithGrades.grades[1].name)
        assertEquals("Chemistry", semesterWithGrades.grades[2].name)
    }

    @Test
    fun `two SemesterWithGrades with same data are equal`() {
        // Given
        val semester = SemesterEntity(semesterName = "SS2024")
        val grades = listOf(
            GradesEntity(
                gradesId = 1L,
                name = "Biology",
                gradeValue = 1.7,
                semesterName = "SS2024"
            )
        )
        val semesterWithGrades1 = SemesterWithGrades(semester = semester, grades = grades)
        val semesterWithGrades2 = SemesterWithGrades(semester = semester, grades = grades)

        // Then
        assertEquals(semesterWithGrades1, semesterWithGrades2)
    }

    @Test
    fun `two SemesterWithGrades with different data are not equal`() {
        // Given
        val semester1 = SemesterEntity(semesterName = "WS2023")
        val semester2 = SemesterEntity(semesterName = "SS2024")
        val grades1 = listOf(
            GradesEntity(
                gradesId = 1L,
                name = "Mathematics",
                gradeValue = 1.7,
                semesterName = "WS2023"
            )
        )
        val grades2 = listOf(
            GradesEntity(
                gradesId = 2L,
                name = "Physics",
                gradeValue = 2.0,
                semesterName = "SS2024"
            )
        )
        val semesterWithGrades1 = SemesterWithGrades(semester = semester1, grades = grades1)
        val semesterWithGrades2 = SemesterWithGrades(semester = semester2, grades = grades2)

        // Then
        assertNotEquals(semesterWithGrades1, semesterWithGrades2)
    }

    @Test
    fun `copy SemesterWithGrades with modified grades`() {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")
        val originalGrades = listOf(
            GradesEntity(
                gradesId = 1L,
                name = "Mathematics",
                gradeValue = 1.7,
                semesterName = "WS2023"
            )
        )
        val original = SemesterWithGrades(semester = semester, grades = originalGrades)

        // When
        val newGrades = listOf(
            GradesEntity(
                gradesId = 1L,
                name = "Mathematics",
                gradeValue = 1.7,
                semesterName = "WS2023"
            ),
            GradesEntity(
                gradesId = 2L,
                name = "Physics",
                gradeValue = 2.0,
                semesterName = "WS2023"
            )
        )
        val modified = original.copy(grades = newGrades)

        // Then
        assertEquals(1, original.grades.size)
        assertEquals(2, modified.grades.size)
    }

    @Test
    fun `verify grades belong to correct semester`() {
        // Given
        val semesterName = "WS2023"
        val semester = SemesterEntity(semesterName = semesterName)
        val grades = listOf(
            GradesEntity(
                gradesId = 1L,
                name = "Mathematics",
                gradeValue = 1.7,
                semesterName = semesterName
            ),
            GradesEntity(
                gradesId = 2L,
                name = "Physics",
                gradeValue = 2.0,
                semesterName = semesterName
            )
        )
        val semesterWithGrades = SemesterWithGrades(semester = semester, grades = grades)

        // Then - all grades should have the same semester name
        assertTrue(semesterWithGrades.grades.all { it.semesterName == semesterName })
    }
}