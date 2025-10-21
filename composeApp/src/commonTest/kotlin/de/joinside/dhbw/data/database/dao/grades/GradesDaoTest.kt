package de.joinside.dhbw.data.database.dao.grades

import de.joinside.dhbw.data.database.AppDatabase
import de.joinside.dhbw.data.database.entities.grades.GradesEntity
import de.joinside.dhbw.data.database.entities.grades.SemesterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for GradesDao.
 * Note: These tests require an actual Room database instance.
 * The database instance creation is platform-specific and needs to be provided
 * via expect/actual pattern or dependency injection.
 */
abstract class GradesDaoTest {

    protected abstract fun createDatabase(): AppDatabase
    protected abstract fun closeDatabase(database: AppDatabase)

    private lateinit var database: AppDatabase
    private lateinit var gradesDao: GradesDao
    private lateinit var semesterDao: SemesterDao

    @BeforeTest
    fun setup() {
        database = createDatabase()
        gradesDao = database.gradesDao()
        semesterDao = database.semesterDao()
    }

    @AfterTest
    fun teardown() {
        closeDatabase(database)
    }

    @Test
    fun insertGrade_and_retrieve() = runTest {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")
        semesterDao.insertSemester(semester)

        val grade = GradesEntity(
            name = "Mathematics", gradeValue = 1.7, semesterName = "WS2023"
        )

        // When
        gradesDao.insertGrade(grade)
        val result = gradesDao.getGradesForSemester("WS2023").first()

        // Then
        assertEquals(1, result.size)
        assertEquals("Mathematics", result[0].name)
        assertEquals(1.7, result[0].gradeValue)
        assertEquals("WS2023", result[0].semesterName)
    }

    @Test
    fun insertMultipleGrades_for_same_semester() = runTest {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")
        semesterDao.insertSemester(semester)

        val grades = listOf(
            GradesEntity(name = "Mathematics", gradeValue = 1.7, semesterName = "WS2023"),
            GradesEntity(name = "Physics", gradeValue = 2.0, semesterName = "WS2023"),
            GradesEntity(name = "Chemistry", gradeValue = 1.3, semesterName = "WS2023")
        )

        // When
        grades.forEach { gradesDao.insertGrade(it) }
        val result = gradesDao.getGradesForSemester("WS2023").first()

        // Then
        assertEquals(3, result.size)
        assertEquals(setOf("Mathematics", "Physics", "Chemistry"), result.map { it.name }.toSet())
    }

    @Test
    fun getGradesForSemester_returns_empty_when_no_grades() = runTest {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")
        semesterDao.insertSemester(semester)

        // When
        val result = gradesDao.getGradesForSemester("WS2023").first()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun getGradesForSemester_filters_by_semester() = runTest {
        // Given
        val semester1 = SemesterEntity(semesterName = "WS2023")
        val semester2 = SemesterEntity(semesterName = "SS2024")
        semesterDao.insertSemester(semester1)
        semesterDao.insertSemester(semester2)

        gradesDao.insertGrade(GradesEntity(name = "Math WS", gradeValue = 1.7, semesterName = "WS2023"))
        gradesDao.insertGrade(GradesEntity(name = "Math SS", gradeValue = 2.0, semesterName = "SS2024"))

        // When
        val ws2023Grades = gradesDao.getGradesForSemester("WS2023").first()
        val ss2024Grades = gradesDao.getGradesForSemester("SS2024").first()

        // Then
        assertEquals(1, ws2023Grades.size)
        assertEquals("Math WS", ws2023Grades[0].name)
        assertEquals(1, ss2024Grades.size)
        assertEquals("Math SS", ss2024Grades[0].name)
    }

    @Test
    fun getAllSemestersWithGrades_returns_correct_relation() = runTest {
        // Given
        val semester1 = SemesterEntity(semesterName = "WS2023")
        val semester2 = SemesterEntity(semesterName = "SS2024")
        semesterDao.insertSemester(semester1)
        semesterDao.insertSemester(semester2)

        gradesDao.insertGrade(GradesEntity(name = "Mathematics", gradeValue = 1.7, semesterName = "WS2023"))
        gradesDao.insertGrade(GradesEntity(name = "Physics", gradeValue = 2.0, semesterName = "WS2023"))
        gradesDao.insertGrade(GradesEntity(name = "Chemistry", gradeValue = 1.3, semesterName = "SS2024"))

        // When
        val result = gradesDao.getAllSemestersWithGrades().first()

        // Then
        assertEquals(2, result.size)

        val ws2023 = result.find { it.semester.semesterName == "WS2023" }
        val ss2024 = result.find { it.semester.semesterName == "SS2024" }

        assertEquals(2, ws2023?.grades?.size)
        assertEquals(1, ss2024?.grades?.size)
    }

    @Test
    fun getAllSemestersWithGrades_handles_semester_without_grades() = runTest {
        // Given
        val semester1 = SemesterEntity(semesterName = "WS2023")
        val semester2 = SemesterEntity(semesterName = "SS2024")
        semesterDao.insertSemester(semester1)
        semesterDao.insertSemester(semester2)

        gradesDao.insertGrade(GradesEntity(name = "Mathematics", gradeValue = 1.7, semesterName = "WS2023"))

        // When
        val result = gradesDao.getAllSemestersWithGrades().first()

        // Then
        assertEquals(2, result.size)

        val ws2023 = result.find { it.semester.semesterName == "WS2023" }
        val ss2024 = result.find { it.semester.semesterName == "SS2024" }

        assertEquals(1, ws2023?.grades?.size)
        assertEquals(0, ss2024?.grades?.size)
    }

    @Test
    fun gradesId_is_auto_generated() = runTest {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")
        semesterDao.insertSemester(semester)

        val grade1 = GradesEntity(name = "Mathematics", gradeValue = 1.7, semesterName = "WS2023")
        val grade2 = GradesEntity(name = "Physics", gradeValue = 2.0, semesterName = "WS2023")

        // When
        gradesDao.insertGrade(grade1)
        gradesDao.insertGrade(grade2)
        val result = gradesDao.getGradesForSemester("WS2023").first()

        // Then
        assertEquals(2, result.size)
        assertTrue(result[0].gradesId > 0)
        assertTrue(result[1].gradesId > 0)
        assertTrue(result[0].gradesId != result[1].gradesId)
    }
}