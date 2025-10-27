package de.joinside.dhbw.data.database.dao.timetable

import de.joinside.dhbw.data.storage.database.AppDatabase
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.SubjectEntity
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureEventDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LecturerDao
import de.joinside.dhbw.data.storage.database.dao.timetable.SubjectDao
import kotlinx.coroutines.flow.first
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
    private lateinit var subjectDao: SubjectDao
    private lateinit var lecturerDao: LecturerDao
    private lateinit var crossRefDao: LectureLecturerCrossRefDao

    @BeforeTest
    fun setup() {
        database = createDatabase()
        lectureDao = database.lectureDao()
        subjectDao = database.subjectDao()
        lecturerDao = database.lecturerDao()
        crossRefDao = database.lectureLecturerCrossRefDao()
    }

    @AfterTest
    fun teardown() {
        closeDatabase(database)
    }

    @Test
    fun insert_and_retrieve_lecture() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Mathematics"))
        val lecture = LectureEventEntity(
            lectureId = 0L,
            subjectId = subjectId,
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
        assertEquals(subjectId, result.subjectId)
        assertEquals("Room A101", result.location)
        assertTrue(insertedId > 0)
    }

    @Test
    fun insertAll_and_getAll() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Physics"))
        val lectures = listOf(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            ),
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
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
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Chemistry"))
        val lecture = LectureEventEntity(
            lectureId = 0L,
            subjectId = subjectId,
            startTime = LocalDateTime(2024, 1, 15, 10, 0),
            endTime = LocalDateTime(2024, 1, 15, 12, 0),
            location = "Lab 1",
            isTest = false
        )

        // When
        lectureDao.insert(lecture)
        val result = lectureDao.getAllFlow().first()

        // Then
        assertEquals(1, result.size)
    }

    @Test
    fun update_lecture() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Biology"))
        val lecture = LectureEventEntity(
            lectureId = 0L,
            subjectId = subjectId,
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
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Mathematics"))
        val lecture = LectureEventEntity(
            lectureId = 0L,
            subjectId = subjectId,
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
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Physics"))
        val lecture = LectureEventEntity(
            lectureId = 0L,
            subjectId = subjectId,
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
    fun getBySubject_returns_lectures_for_subject() = runTest {
        // Given
        val subjectId1 = subjectDao.insert(SubjectEntity(0L, "Mathematics"))
        val subjectId2 = subjectDao.insert(SubjectEntity(0L, "Physics"))

        lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId1,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )
        lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId1,
                startTime = LocalDateTime(2024, 1, 16, 10, 0),
                endTime = LocalDateTime(2024, 1, 16, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )
        lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId2,
                startTime = LocalDateTime(2024, 1, 17, 10, 0),
                endTime = LocalDateTime(2024, 1, 17, 12, 0),
                location = "Lab 1",
                isTest = false
            )
        )

        // When
        val mathLectures = lectureDao.getBySubject(subjectId1)
        val physicsLectures = lectureDao.getBySubject(subjectId2)

        // Then
        assertEquals(2, mathLectures.size)
        assertEquals(1, physicsLectures.size)
        assertTrue(mathLectures.all { it.subjectId == subjectId1 })
        assertTrue(physicsLectures.all { it.subjectId == subjectId2 })
    }

    @Test
    fun getWithLecturers_returns_lecture_with_relations() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Database Systems"))
        val lecturerId1 = lecturerDao.insert(LecturerEntity(0L, "Prof. Dr. Smith"))
        val lecturerId2 = lecturerDao.insert(LecturerEntity(0L, "Dr. Johnson"))

        val lectureId = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )

        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId1))
        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId2))

        // When
        val result = lectureDao.getWithLecturers(lectureId)

        // Then
        assertNotNull(result)
        assertEquals(lectureId, result.lecture.lectureId)
        assertEquals("Database Systems", result.subject.name)
        assertEquals(2, result.lecturers.size)
        assertEquals(setOf("Prof. Dr. Smith", "Dr. Johnson"), result.lecturers.map { it.lecturerName }.toSet())
    }

    @Test
    fun getAllWithLecturersFlow_returns_all_lectures_with_relations() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Software Engineering"))
        val lecturerId = lecturerDao.insert(LecturerEntity(0L, "Prof. Anderson"))

        val lectureId = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )

        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId))

        // When
        val result = lectureDao.getAllWithLecturersFlow().first()

        // Then
        assertEquals(1, result.size)
        assertEquals("Software Engineering", result[0].subject.name)
        assertEquals(1, result[0].lecturers.size)
        assertEquals("Prof. Anderson", result[0].lecturers[0].lecturerName)
    }

    @Test
    fun cascade_delete_when_subject_is_deleted() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Mathematics"))
        val lectureId = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )

        // When
        subjectDao.deleteById(subjectId)
        val result = lectureDao.getById(lectureId)

        // Then - lecture should be deleted due to CASCADE
        assertNull(result)
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
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Mathematics"))
        val testLecture = LectureEventEntity(
            lectureId = 0L,
            subjectId = subjectId,
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
}