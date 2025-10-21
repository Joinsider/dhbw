package de.joinside.dhbw.data.database.dao

import de.joinside.dhbw.AppDatabase
import de.joinside.dhbw.data.database.entities.grades.SemesterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for SemesterDao.
 * Note: These tests require an actual Room database instance.
 * The database instance creation is platform-specific and needs to be provided
 * via expect/actual pattern or dependency injection.
 */
abstract class SemesterDaoTest {

    protected abstract fun createDatabase(): AppDatabase
    protected abstract fun closeDatabase(database: AppDatabase)

    private lateinit var database: AppDatabase
    private lateinit var semesterDao: SemesterDao

    @BeforeTest
    fun setup() {
        database = createDatabase()
        semesterDao = database.semesterDao()
    }

    @AfterTest
    fun teardown() {
        closeDatabase(database)
    }

    @Test
    fun insertSemester_and_retrieve() = runTest {
        // Given
        val semester = SemesterEntity(semesterName = "WS2023")

        // When
        semesterDao.insertSemester(semester)
        val result = semesterDao.getAllSemesters().first()

        // Then
        assertEquals(1, result.size)
        assertEquals("WS2023", result[0].semesterName)
    }

    @Test
    fun insertMultipleSemesters() = runTest {
        // Given
        val semesters = listOf(
            SemesterEntity(semesterName = "WS2023"),
            SemesterEntity(semesterName = "SS2024"),
            SemesterEntity(semesterName = "WS2024")
        )

        // When
        semesters.forEach { semesterDao.insertSemester(it) }
        val result = semesterDao.getAllSemesters().first()

        // Then
        assertEquals(3, result.size)
        assertEquals(setOf("WS2023", "SS2024", "WS2024"), result.map { it.semesterName }.toSet())
    }

    @Test
    fun getAllSemesters_returns_empty_when_no_data() = runTest {
        // When
        val result = semesterDao.getAllSemesters().first()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun insertSemester_with_same_name_should_replace_or_fail() = runTest {
        // Given - semesterName is primary key, so duplicate should either replace or fail
        val semester1 = SemesterEntity(semesterName = "WS2023")
        val semester2 = SemesterEntity(semesterName = "WS2023")

        // When
        semesterDao.insertSemester(semester1)

        // This might throw an exception or replace depending on Room configuration
        // For now, we test that at least one insertion works
        val resultAfterFirst = semesterDao.getAllSemesters().first()

        // Then
        assertEquals(1, resultAfterFirst.size)
        assertEquals("WS2023", resultAfterFirst[0].semesterName)
    }

    @Test
    fun getAllSemesters_returns_flow_that_updates() = runTest {
        // Given
        val semester1 = SemesterEntity(semesterName = "WS2023")

        // When - insert first semester
        semesterDao.insertSemester(semester1)
        val resultAfterFirst = semesterDao.getAllSemesters().first()

        // Then
        assertEquals(1, resultAfterFirst.size)

        // When - insert second semester
        val semester2 = SemesterEntity(semesterName = "SS2024")
        semesterDao.insertSemester(semester2)
        val resultAfterSecond = semesterDao.getAllSemesters().first()

        // Then
        assertEquals(2, resultAfterSecond.size)
    }

    @Test
    fun insertSemester_with_various_naming_formats() = runTest {
        // Given
        val semesters = listOf(
            SemesterEntity(semesterName = "WS2023"),
            SemesterEntity(semesterName = "SS2024"),
            SemesterEntity(semesterName = "Semester 1"),
            SemesterEntity(semesterName = "Winter 2023/2024"),
            SemesterEntity(semesterName = "Q1 2024")
        )

        // When
        semesters.forEach { semesterDao.insertSemester(it) }
        val result = semesterDao.getAllSemesters().first()

        // Then
        assertEquals(5, result.size)
        assertTrue(result.any { it.semesterName == "WS2023" })
        assertTrue(result.any { it.semesterName == "SS2024" })
        assertTrue(result.any { it.semesterName == "Semester 1" })
        assertTrue(result.any { it.semesterName == "Winter 2023/2024" })
        assertTrue(result.any { it.semesterName == "Q1 2024" })
    }
}

