package de.joinside.dhbw.data.database.dao.timetable

import de.joinside.dhbw.data.database.AppDatabase
import de.joinside.dhbw.data.database.entities.timetable.SubjectEntity
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
 * Integration tests for SubjectDao.
 * Note: These tests require an actual Room database instance.
 * The database instance creation is platform-specific and needs to be provided
 * via expect/actual pattern or dependency injection.
 */
abstract class SubjectDaoTest {

    protected abstract fun createDatabase(): AppDatabase
    protected abstract fun closeDatabase(database: AppDatabase)

    private lateinit var database: AppDatabase
    private lateinit var subjectDao: SubjectDao

    @BeforeTest
    fun setup() {
        database = createDatabase()
        subjectDao = database.subjectDao()
    }

    @AfterTest
    fun teardown() {
        closeDatabase(database)
    }

    @Test
    fun insert_and_retrieve_subject() = runTest {
        // Given
        val subject = SubjectEntity(subjectId = 0L, name = "Mathematics")

        // When
        val insertedId = subjectDao.insert(subject)
        val result = subjectDao.getById(insertedId)

        // Then
        assertNotNull(result)
        assertEquals("Mathematics", result.name)
        assertTrue(insertedId > 0)
    }

    @Test
    fun insertAll_and_getAll() = runTest {
        // Given
        val subjects = listOf(
            SubjectEntity(subjectId = 0L, name = "Mathematics"),
            SubjectEntity(subjectId = 0L, name = "Physics"),
            SubjectEntity(subjectId = 0L, name = "Chemistry")
        )

        // When
        subjectDao.insertAll(subjects)
        val result = subjectDao.getAll()

        // Then
        assertEquals(3, result.size)
        assertEquals(setOf("Mathematics", "Physics", "Chemistry"), result.map { it.name }.toSet())
    }

    @Test
    fun getAllFlow_returns_flow_of_subjects() = runTest {
        // Given
        val subjects = listOf(
            SubjectEntity(subjectId = 0L, name = "Biology"),
            SubjectEntity(subjectId = 0L, name = "Computer Science")
        )

        // When
        subjectDao.insertAll(subjects)
        val result = subjectDao.getAllFlow().first()

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun update_subject() = runTest {
        // Given
        val subject = SubjectEntity(subjectId = 0L, name = "Computer Science I")
        val insertedId = subjectDao.insert(subject)

        // When
        val retrievedSubject = subjectDao.getById(insertedId)!!
        val updatedSubject = retrievedSubject.copy(name = "Computer Science II")
        subjectDao.update(updatedSubject)
        val result = subjectDao.getById(insertedId)

        // Then
        assertNotNull(result)
        assertEquals("Computer Science II", result.name)
        assertEquals(insertedId, result.subjectId)
    }

    @Test
    fun delete_subject() = runTest {
        // Given
        val subject = SubjectEntity(subjectId = 0L, name = "Mathematics")
        val insertedId = subjectDao.insert(subject)

        // When
        val retrievedSubject = subjectDao.getById(insertedId)!!
        subjectDao.delete(retrievedSubject)
        val result = subjectDao.getById(insertedId)

        // Then
        assertNull(result)
    }

    @Test
    fun deleteById_removes_subject() = runTest {
        // Given
        val subject = SubjectEntity(subjectId = 0L, name = "Physics")
        val insertedId = subjectDao.insert(subject)

        // When
        subjectDao.deleteById(insertedId)
        val result = subjectDao.getById(insertedId)

        // Then
        assertNull(result)
    }

    @Test
    fun searchByName_finds_matching_subjects() = runTest {
        // Given
        val subjects = listOf(
            SubjectEntity(subjectId = 0L, name = "Advanced Mathematics"),
            SubjectEntity(subjectId = 0L, name = "Basic Mathematics"),
            SubjectEntity(subjectId = 0L, name = "Physics"),
            SubjectEntity(subjectId = 0L, name = "Mathematical Physics")
        )
        subjectDao.insertAll(subjects)

        // When
        val result = subjectDao.searchByName("Math")

        // Then
        assertEquals(3, result.size)
        assertTrue(result.all { it.name.contains("Math", ignoreCase = true) })
    }

    @Test
    fun searchByName_returns_empty_when_no_match() = runTest {
        // Given
        val subjects = listOf(
            SubjectEntity(subjectId = 0L, name = "Biology"),
            SubjectEntity(subjectId = 0L, name = "Chemistry")
        )
        subjectDao.insertAll(subjects)

        // When
        val result = subjectDao.searchByName("Physics")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun getAll_returns_empty_when_no_subjects() = runTest {
        // When
        val result = subjectDao.getAll()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun getAllFlow_emits_updates() = runTest {
        // When - initial state
        val initialResult = subjectDao.getAllFlow().first()
        assertEquals(0, initialResult.size)

        // When - insert subject
        subjectDao.insert(SubjectEntity(subjectId = 0L, name = "Mathematics"))
        val afterInsert = subjectDao.getAllFlow().first()

        // Then
        assertEquals(1, afterInsert.size)
        assertEquals("Mathematics", afterInsert[0].name)
    }

    @Test
    fun insert_with_onConflictReplace_replaces_existing() = runTest {
        // Given
        val subject1 = SubjectEntity(subjectId = 1L, name = "Original Name")
        subjectDao.insert(subject1)

        // When
        val subject2 = SubjectEntity(subjectId = 1L, name = "Updated Name")
        subjectDao.insert(subject2)
        val result = subjectDao.getById(1L)

        // Then
        assertNotNull(result)
        assertEquals("Updated Name", result.name)
    }

    @Test
    fun searchByName_is_case_insensitive() = runTest {
        // Given
        val subject = SubjectEntity(subjectId = 0L, name = "Computer Science")
        subjectDao.insert(subject)

        // When
        val result1 = subjectDao.searchByName("computer")
        val result2 = subjectDao.searchByName("COMPUTER")
        val result3 = subjectDao.searchByName("CoMpUtEr")

        // Then
        assertEquals(1, result1.size)
        assertEquals(1, result2.size)
        assertEquals(1, result3.size)
    }

    @Test
    fun getById_returns_null_for_non_existent_id() = runTest {
        // When
        val result = subjectDao.getById(999L)

        // Then
        assertNull(result)
    }
}