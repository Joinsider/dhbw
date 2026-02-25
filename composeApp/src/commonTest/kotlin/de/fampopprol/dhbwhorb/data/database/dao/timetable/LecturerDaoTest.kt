package de.fampopprol.dhbwhorb.data.database.dao.timetable

import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LecturerDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for LecturerDao.
 * Note: These tests require an actual Room database instance.
 * The database instance creation is platform-specific and needs to be provided
 * via expect/actual pattern or dependency injection.
 */
abstract class LecturerDaoTest {

    protected abstract fun createDatabase(): AppDatabase
    protected abstract fun closeDatabase(database: AppDatabase)

    private lateinit var database: AppDatabase
    private lateinit var lecturerDao: LecturerDao

    @BeforeTest
    fun setup() {
        database = createDatabase()
        lecturerDao = database.lecturerDao()
    }

    @AfterTest
    fun teardown() {
        closeDatabase(database)
    }

    @Test
    fun insert_and_retrieve_lecturer() = runTest {
        // Given
        val lecturer = LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Dr. Smith")

        // When
        val insertedId = lecturerDao.insert(lecturer)
        val result = lecturerDao.getById(insertedId)

        // Then
        assertNotNull(result)
        assertEquals("Prof. Dr. Smith", result.lecturerName)
        assertTrue(insertedId > 0)
    }

    @Test
    fun insertAll_and_getAll() = runTest {
        // Given
        val lecturers = listOf(
            LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Dr. Smith"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Dr. Johnson"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Anderson")
        )

        // When
        lecturerDao.insertAll(lecturers)
        val result = lecturerDao.getAll()

        // Then
        assertEquals(3, result.size)
        assertEquals(
            setOf("Prof. Dr. Smith", "Dr. Johnson", "Prof. Anderson"),
            result.map { it.lecturerName }.toSet()
        )
    }

    @Test
    fun getAllFlow_returns_flow_of_lecturers() = runTest {
        // Given
        val lecturers = listOf(
            LecturerEntity(lecturerId = 0L, lecturerName = "Dr. Brown"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Wilson")
        )

        // When
        lecturerDao.insertAll(lecturers)
        val result = lecturerDao.getAllFlow().first()

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun update_lecturer() = runTest {
        // Given
        val lecturer = LecturerEntity(lecturerId = 0L, lecturerName = "Dr. Taylor")
        val insertedId = lecturerDao.insert(lecturer)

        // When
        val retrievedLecturer = lecturerDao.getById(insertedId)!!
        val updatedLecturer = retrievedLecturer.copy(lecturerName = "Prof. Dr. Taylor")
        lecturerDao.update(updatedLecturer)
        val result = lecturerDao.getById(insertedId)

        // Then
        assertNotNull(result)
        assertEquals("Prof. Dr. Taylor", result.lecturerName)
        assertEquals(insertedId, result.lecturerId)
    }

    @Test
    fun delete_lecturer() = runTest {
        // Given
        val lecturer = LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Martinez")
        val insertedId = lecturerDao.insert(lecturer)

        // When
        val retrievedLecturer = lecturerDao.getById(insertedId)!!
        lecturerDao.delete(retrievedLecturer)
        val result = lecturerDao.getById(insertedId)

        // Then
        assertNull(result)
    }

    @Test
    fun deleteById_removes_lecturer() = runTest {
        // Given
        val lecturer = LecturerEntity(lecturerId = 0L, lecturerName = "Dr. Davis")
        val insertedId = lecturerDao.insert(lecturer)

        // When
        lecturerDao.deleteById(insertedId)
        val result = lecturerDao.getById(insertedId)

        // Then
        assertNull(result)
    }

    @Test
    fun searchByName_finds_matching_lecturers() = runTest {
        // Given
        val lecturers = listOf(
            LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Dr. Smith"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Dr. Smith"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Johnson"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Dr. Anderson")
        )
        lecturerDao.insertAll(lecturers)

        // When
        val result = lecturerDao.searchByName("Smith")

        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.lecturerName.contains("Smith") })
    }

    @Test
    fun searchByName_returns_empty_when_no_match() = runTest {
        // Given
        val lecturers = listOf(
            LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Brown"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Dr. Wilson")
        )
        lecturerDao.insertAll(lecturers)

        // When
        val result = lecturerDao.searchByName("Miller")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun getAll_returns_empty_when_no_lecturers() = runTest {
        // When
        val result = lecturerDao.getAll()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun getAllFlow_emits_updates() = runTest {
        // When - initial state
        val initialResult = lecturerDao.getAllFlow().first()
        assertEquals(0, initialResult.size)

        // When - insert lecturer
        lecturerDao.insert(LecturerEntity(lecturerId = 0L, lecturerName = "Prof. White"))
        val afterInsert = lecturerDao.getAllFlow().first()

        // Then
        assertEquals(1, afterInsert.size)
        assertEquals("Prof. White", afterInsert[0].lecturerName)
    }

    @Test
    fun insert_with_onConflictReplace_replaces_existing() = runTest {
        // Given
        val lecturer1 = LecturerEntity(lecturerId = 1L, lecturerName = "Original Name")
        lecturerDao.insert(lecturer1)

        // When
        val lecturer2 = LecturerEntity(lecturerId = 1L, lecturerName = "Updated Name")
        lecturerDao.insert(lecturer2)
        val result = lecturerDao.getById(1L)

        // Then
        assertNotNull(result)
        assertEquals("Updated Name", result.lecturerName)
    }

    @Test
    fun searchByName_is_case_insensitive() = runTest {
        // Given
        val lecturer = LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Dr. Mueller")
        lecturerDao.insert(lecturer)

        // When
        val result1 = lecturerDao.searchByName("mueller")
        val result2 = lecturerDao.searchByName("MUELLER")
        val result3 = lecturerDao.searchByName("MuElLeR")

        // Then
        assertEquals(1, result1.size)
        assertEquals(1, result2.size)
        assertEquals(1, result3.size)
    }

    @Test
    fun searchByName_finds_partial_matches() = runTest {
        // Given
        val lecturers = listOf(
            LecturerEntity(lecturerId = 0L, lecturerName = "Prof. Dr. Smith"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Smithson"),
            LecturerEntity(lecturerId = 0L, lecturerName = "Goldsmith")
        )
        lecturerDao.insertAll(lecturers)

        // When
        val result = lecturerDao.searchByName("smith")

        // Then
        assertEquals(3, result.size)
    }

    @Test
    fun getById_returns_null_for_non_existent_id() = runTest {
        // When
        val result = lecturerDao.getById(999L)

        // Then
        assertNull(result)
    }
}


