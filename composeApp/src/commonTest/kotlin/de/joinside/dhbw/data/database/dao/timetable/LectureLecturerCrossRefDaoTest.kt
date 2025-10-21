package de.joinside.dhbw.data.database.dao.timetable

import de.joinside.dhbw.data.database.AppDatabase
import de.joinside.dhbw.data.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.database.entities.timetable.LectureLecturerCrossRef
import de.joinside.dhbw.data.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.database.entities.timetable.SubjectEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for LectureLecturerCrossRefDao.
 * Note: These tests require an actual Room database instance.
 * The database instance creation is platform-specific and needs to be provided
 * via expect/actual pattern or dependency injection.
 */
abstract class LectureLecturerCrossRefDaoTest {

    protected abstract fun createDatabase(): AppDatabase
    protected abstract fun closeDatabase(database: AppDatabase)

    private lateinit var database: AppDatabase
    private lateinit var crossRefDao: LectureLecturerCrossRefDao
    private lateinit var lectureDao: LectureEventDao
    private lateinit var lecturerDao: LecturerDao
    private lateinit var subjectDao: SubjectDao

    @BeforeTest
    fun setup() {
        database = createDatabase()
        crossRefDao = database.lectureLecturerCrossRefDao()
        lectureDao = database.lectureDao()
        lecturerDao = database.lecturerDao()
        subjectDao = database.subjectDao()
    }

    @AfterTest
    fun teardown() {
        closeDatabase(database)
    }

    @Test
    fun insert_crossRef() = runTest {
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
        val lecturerId = lecturerDao.insert(LecturerEntity(0L, "Prof. Dr. Smith"))
        val crossRef = LectureLecturerCrossRef(lectureId, lecturerId)

        // When
        crossRefDao.insert(crossRef)
        val result = lectureDao.getWithLecturers(lectureId)

        // Then
        assertEquals(1, result?.lecturers?.size)
        assertEquals("Prof. Dr. Smith", result?.lecturers?.get(0)?.lecturerName)
    }

    @Test
    fun insertAll_crossRefs() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Physics"))
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
        val lecturerId1 = lecturerDao.insert(LecturerEntity(0L, "Prof. Anderson"))
        val lecturerId2 = lecturerDao.insert(LecturerEntity(0L, "Dr. Brown"))
        val crossRefs = listOf(
            LectureLecturerCrossRef(lectureId, lecturerId1),
            LectureLecturerCrossRef(lectureId, lecturerId2)
        )

        // When
        crossRefDao.insertAll(crossRefs)
        val result = lectureDao.getWithLecturers(lectureId)

        // Then
        assertEquals(2, result?.lecturers?.size)
        assertEquals(
            setOf("Prof. Anderson", "Dr. Brown"),
            result?.lecturers?.map { it.lecturerName }?.toSet()
        )
    }

    @Test
    fun delete_specific_crossRef() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Chemistry"))
        val lectureId = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Lab 1",
                isTest = false
            )
        )
        val lecturerId1 = lecturerDao.insert(LecturerEntity(0L, "Prof. Wilson"))
        val lecturerId2 = lecturerDao.insert(LecturerEntity(0L, "Dr. Davis"))

        val crossRef1 = LectureLecturerCrossRef(lectureId, lecturerId1)
        val crossRef2 = LectureLecturerCrossRef(lectureId, lecturerId2)
        crossRefDao.insert(crossRef1)
        crossRefDao.insert(crossRef2)

        // When
        crossRefDao.delete(crossRef1)
        val result = lectureDao.getWithLecturers(lectureId)

        // Then
        assertEquals(1, result?.lecturers?.size)
        assertEquals("Dr. Davis", result?.lecturers?.get(0)?.lecturerName)
    }

    @Test
    fun deleteByLectureId_removes_all_lecturers_for_lecture() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Biology"))
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
        val lecturerId1 = lecturerDao.insert(LecturerEntity(0L, "Prof. Taylor"))
        val lecturerId2 = lecturerDao.insert(LecturerEntity(0L, "Dr. Martinez"))

        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId1))
        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId2))

        // When
        crossRefDao.deleteByLectureId(lectureId)
        val result = lectureDao.getWithLecturers(lectureId)

        // Then
        assertTrue(result?.lecturers?.isEmpty() ?: false)
    }

    @Test
    fun deleteByLecturerId_removes_lecturer_from_all_lectures() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Mathematics"))
        val lectureId1 = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )
        val lectureId2 = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 16, 10, 0),
                endTime = LocalDateTime(2024, 1, 16, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )
        val lecturerId = lecturerDao.insert(LecturerEntity(0L, "Prof. White"))

        crossRefDao.insert(LectureLecturerCrossRef(lectureId1, lecturerId))
        crossRefDao.insert(LectureLecturerCrossRef(lectureId2, lecturerId))

        // When
        crossRefDao.deleteByLecturerId(lecturerId)
        val result1 = lectureDao.getWithLecturers(lectureId1)
        val result2 = lectureDao.getWithLecturers(lectureId2)

        // Then
        assertTrue(result1?.lecturers?.isEmpty() ?: false)
        assertTrue(result2?.lecturers?.isEmpty() ?: false)
    }

    @Test
    fun deleteByIds_removes_specific_relation() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Computer Science"))
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
        val lecturerId1 = lecturerDao.insert(LecturerEntity(0L, "Prof. Black"))
        val lecturerId2 = lecturerDao.insert(LecturerEntity(0L, "Dr. Green"))

        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId1))
        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId2))

        // When
        crossRefDao.deleteByIds(lectureId, lecturerId1)
        val result = lectureDao.getWithLecturers(lectureId)

        // Then
        assertEquals(1, result?.lecturers?.size)
        assertEquals("Dr. Green", result?.lecturers?.get(0)?.lecturerName)
    }

    @Test
    fun cascade_delete_when_lecture_is_deleted() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Physics"))
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
        val lecturerId = lecturerDao.insert(LecturerEntity(0L, "Prof. Gray"))
        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId))

        // When
        lectureDao.deleteById(lectureId)
        // Trying to get a deleted lecture should return null
        val lecture = lectureDao.getById(lectureId)

        // Then - crossRef should be cascade deleted
        assertEquals(null, lecture)
    }

    @Test
    fun cascade_delete_when_lecturer_is_deleted() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Chemistry"))
        val lectureId = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Lab 1",
                isTest = false
            )
        )
        val lecturerId = lecturerDao.insert(LecturerEntity(0L, "Prof. Silver"))
        crossRefDao.insert(LectureLecturerCrossRef(lectureId, lecturerId))

        // When
        lecturerDao.deleteById(lecturerId)
        val result = lectureDao.getWithLecturers(lectureId)

        // Then - lecturer should be removed from the relation
        assertTrue(result?.lecturers?.isEmpty() ?: false)
    }

    @Test
    fun replace_on_conflict_for_duplicate_insert() = runTest {
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
        val lecturerId = lecturerDao.insert(LecturerEntity(0L, "Prof. Gold"))
        val crossRef = LectureLecturerCrossRef(lectureId, lecturerId)

        // When - insert the same crossRef twice
        crossRefDao.insert(crossRef)
        crossRefDao.insert(crossRef) // Should replace, not throw error
        val result = lectureDao.getWithLecturers(lectureId)

        // Then - should still have only one lecturer
        assertEquals(1, result?.lecturers?.size)
    }

    @Test
    fun many_to_many_relationship_multiple_lecturers_multiple_lectures() = runTest {
        // Given
        val subjectId = subjectDao.insert(SubjectEntity(0L, "Software Engineering"))
        val lectureId1 = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 15, 10, 0),
                endTime = LocalDateTime(2024, 1, 15, 12, 0),
                location = "Room A101",
                isTest = false
            )
        )
        val lectureId2 = lectureDao.insert(
            LectureEventEntity(
                lectureId = 0L,
                subjectId = subjectId,
                startTime = LocalDateTime(2024, 1, 16, 10, 0),
                endTime = LocalDateTime(2024, 1, 16, 12, 0),
                location = "Room B202",
                isTest = false
            )
        )
        val lecturerId1 = lecturerDao.insert(LecturerEntity(0L, "Prof. Red"))
        val lecturerId2 = lecturerDao.insert(LecturerEntity(0L, "Dr. Blue"))

        // When - create many-to-many relationships
        crossRefDao.insert(LectureLecturerCrossRef(lectureId1, lecturerId1))
        crossRefDao.insert(LectureLecturerCrossRef(lectureId1, lecturerId2))
        crossRefDao.insert(LectureLecturerCrossRef(lectureId2, lecturerId1))

        val lecture1WithLecturers = lectureDao.getWithLecturers(lectureId1)
        val lecture2WithLecturers = lectureDao.getWithLecturers(lectureId2)

        // Then
        assertEquals(2, lecture1WithLecturers?.lecturers?.size) // Both lecturers
        assertEquals(1, lecture2WithLecturers?.lecturers?.size) // Only one lecturer
        assertEquals("Prof. Red", lecture2WithLecturers?.lecturers?.get(0)?.lecturerName)
    }
}


