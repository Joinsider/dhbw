package de.joinside.dhbw.data.database.dao.timetable

import de.joinside.dhbw.data.storage.database.AppDatabase
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureEventDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LecturerDao
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for LectureEventDao.
 * Note: These tests require an actual Room database instance.
 * The database instance creation is platform-specific and needs to be provided
 * via expect/actual pattern or dependency injection.
 */
abstract class LectureEventDaoTest {

    protected abstract fun createDatabase(): AppDatabase
    protected abstract fun closeDatabase(database: AppDatabase)

    private lateinit var database: AppDatabase
    private lateinit var lectureDao: LectureEventDao
    private lateinit var lecturerDao: LecturerDao

    @BeforeTest
    fun setup() {
        database = createDatabase()
        lectureDao = database.lectureDao()
        lecturerDao = database.lecturerDao()
    }

    @AfterTest
    fun teardown() {
        closeDatabase(database)
    }

    @Test
    fun insert_and_retrieve_lecture() = runTest {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 0L,
            shortSubjectName = "Mathematics",
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )

        // When
        val insertedId = lectureDao.insert(lecture)
        val result = lectureDao.getById(insertedId)

        // Then
        assertNotNull(result)
        assertEquals("Mathematics", result.shortSubjectName)
        assertEquals("Room A101", result.location)
        assertTrue(insertedId > 0)
    }

    @Test
    fun insertAll_and_getAll() = runTest {
        // Given
        val lectures = listOf(
            LectureEventEntity(
                lectureId = 0L,
                shortSubjectName = "Physics",
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            ),
            LectureEventEntity(
                lectureId = 0L,
                shortSubjectName = "Physics",
                startTime = LocalDateTime(2024, 1, 16, 14, 0),
                endTime = LocalDateTime(2024, 1, 16, 16, 0),
                location = "Room B202",
                isTest = false
            )
        )

        // When
        lectureDao.insertAll(lectures)
        val result = lectureDao.getAll()

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun getAllFlow_returns_flow_of_lectures() = runTest {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 0L,
            shortSubjectName = "Chemistry",
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Lab 1",
            isTest = false
        )

        // When
        lectureDao.insert(lecture)
        val result = lectureDao.getAll()

        // Then
        assertEquals(1, result.size)
        assertEquals("Chemistry", result[0].shortSubjectName)
    }

    @Test
    fun update_lecture() = runTest {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 0L,
            shortSubjectName = "Biology",
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val insertedId = lectureDao.insert(lecture)

        // When
        val retrievedLecture = lectureDao.getById(insertedId)!!
        val updatedLecture = retrievedLecture.copy(location = "Room B202", isTest = true)
        lectureDao.update(updatedLecture)
        val result = lectureDao.getById(insertedId)

        // Then
        assertNotNull(result)
        assertEquals("Room B202", result.location)
        assertTrue(result.isTest)
    }

    @Test
    fun delete_lecture() = runTest {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 0L,
            shortSubjectName = "Mathematics",
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val insertedId = lectureDao.insert(lecture)

        // When
        val retrievedLecture = lectureDao.getById(insertedId)!!
        lectureDao.delete(retrievedLecture)
        val result = lectureDao.getById(insertedId)

        // Then
        assertNull(result)
    }

    @Test
    fun deleteById_removes_lecture() = runTest {
        // Given
        val lecture = LectureEventEntity(
            lectureId = 0L,
            shortSubjectName = "Physics",
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = false
        )
        val insertedId = lectureDao.insert(lecture)

        // When
        lectureDao.deleteById(insertedId)
        val result = lectureDao.getById(insertedId)

        // Then
        assertNull(result)
    }

    @Test
    fun cascade_delete_when_lecture_is_deleted() = runTest {
        // Given
        val lectureId = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                shortSubjectName = "Mathematics",
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )
        val lecturerId = lecturerDao.insert(LecturerEntity(0L, "Prof. Martinez"))

        // Create association in junction table
        database.lectureLecturerCrossRefDao().insert(
            de.joinside.dhbw.data.storage.database.entities.timetable.LectureLecturerCrossRef(
                lectureId = lectureId,
                lecturerId = lecturerId
            )
        )

        // When - delete the lecture
        val lecture = lectureDao.getById(lectureId)!!
        lectureDao.delete(lecture)

        // Then - junction table entry should be deleted due to CASCADE
        val associations = database.lectureLecturerCrossRefDao().getByLectureId(lectureId)
        assertTrue(associations.isEmpty())
    }

    @Test
    fun getAll_returns_empty_when_no_lectures() = runTest {
        // When
        val result = lectureDao.getAll()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun insert_test_lecture() = runTest {
        // Given
        val testLecture = LectureEventEntity(
            lectureId = 0L,
            shortSubjectName = "Mathematics",
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Room A101",
            isTest = true
        )

        // When
        val insertedId = lectureDao.insert(testLecture)
        val result = lectureDao.getById(insertedId)

        // Then
        assertNotNull(result)
        assertTrue(result.isTest)
    }

    @Test
    fun getById_returns_null_for_non_existent_id() = runTest {
        // When
        val result = lectureDao.getById(999L)

        // Then
        assertNull(result)
    }

    @Test
    fun multiple_lectures_with_same_subject_name() = runTest {
        // Given
        lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                shortSubjectName = "Computer Science",
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )
        lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                shortSubjectName = "Computer Science",
                startTime = LocalDateTime(2024, 1, 16, 14, 0),
                endTime = LocalDateTime(2024, 1, 16, 16, 0),
                location = "Room B202",
                isTest = false
            )
        )

        // When
        val result = lectureDao.getAll()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.shortSubjectName == "Computer Science" })
    }
}
