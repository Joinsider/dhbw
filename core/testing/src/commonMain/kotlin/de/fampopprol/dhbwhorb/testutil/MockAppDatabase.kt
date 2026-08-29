package de.fampopprol.dhbwhorb.testutil

import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.database.dao.documents.CachedDocumentDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeCacheMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LecturerDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.SyncMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
import de.fampopprol.dhbwhorb.data.storage.database.entities.documents.CachedDocumentEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.documents.CachedDocumentHead
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeCacheMetadata
import de.fampopprol.dhbwhorb.data.storage.database.entities.SyncMetadataEntity
import androidx.room.InvalidationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDateTime

class MockAppDatabase : AppDatabase() {
    override fun lectureDao(): LectureEventDao = MockLectureEventDao()
    override fun lecturerDao(): LecturerDao = MockLecturerDao()
    override fun lectureLecturerCrossRefDao(): LectureLecturerCrossRefDao = MockLectureLecturerCrossRefDao()
    override fun gradeDao(): GradeDao = MockGradeDao()
    override fun gradeCacheMetadataDao(): GradeCacheMetadataDao = MockGradeCacheMetadataDao()
    override fun cachedDocumentDao(): CachedDocumentDao = InMemoryCachedDocumentDao()
    override fun syncMetadataDao(): SyncMetadataDao = MockSyncMetadataDao()
    override fun createInvalidationTracker(): InvalidationTracker = throw NotImplementedError()
    
    @Suppress("NOTHING_TO_OVERRIDE")
    override fun clearAllTables() {}
}

// Open so a test can answer one query for real without reimplementing the other twelve.
open class MockLectureEventDao : LectureEventDao {
    override suspend fun insert(lecture: LectureEventEntity): Long = 0L
    override suspend fun insertAll(lectures: List<LectureEventEntity>) {}
    override suspend fun update(lecture: LectureEventEntity) {}
    override suspend fun delete(lecture: LectureEventEntity) {}
    override suspend fun getById(id: Long): LectureEventEntity? = null
    override fun getAllFlow(): Flow<List<LectureEventEntity>> = flowOf(emptyList())
    override suspend fun getAll(): List<LectureEventEntity> = emptyList()
    override suspend fun getByIdWithLecturers(id: Long): LectureWithLecturers? = null
    override suspend fun getAllWithLecturers(): List<LectureWithLecturers> = emptyList()
    override fun getAllWithLecturersFlow(): Flow<List<LectureWithLecturers>> = flowOf(emptyList())
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteAll() {}
    override suspend fun deleteInRange(start: LocalDateTime, end: LocalDateTime) {}
    override suspend fun deleteEndedBefore(cutoff: LocalDateTime) {}
}

class MockLecturerDao : LecturerDao {
    override suspend fun insert(lecturer: LecturerEntity): Long = 0L
    override suspend fun insertAll(lecturers: List<LecturerEntity>) {}
    override suspend fun update(lecturer: LecturerEntity) {}
    override suspend fun delete(lecturer: LecturerEntity) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteAll() {}
    override suspend fun getAll(): List<LecturerEntity> = emptyList()
    override fun getAllFlow(): Flow<List<LecturerEntity>> = flowOf(emptyList())
    override suspend fun getById(id: Long): LecturerEntity? = null
    override suspend fun searchByName(searchQuery: String): List<LecturerEntity> = emptyList()
}

class MockLectureLecturerCrossRefDao : LectureLecturerCrossRefDao {
    override suspend fun insert(crossRef: LectureLecturerCrossRef) {}
    override suspend fun insertAll(crossRefs: List<LectureLecturerCrossRef>) {}
    override suspend fun delete(crossRef: LectureLecturerCrossRef) {}
    override suspend fun deleteByLectureId(lectureId: Long) {}
    override suspend fun deleteByLecturerId(lecturerId: Long) {}
    override suspend fun deleteAll() {}
    override suspend fun getByLectureId(lectureId: Long): List<LectureLecturerCrossRef> = emptyList()
    override suspend fun getByLecturerId(lecturerId: Long): List<LectureLecturerCrossRef> = emptyList()
}

class MockGradeDao : GradeDao {
    override suspend fun insert(grade: GradeEntity) {}
    override suspend fun insertAll(grades: List<GradeEntity>) {}
    override suspend fun getGradesForSemester(studentId: String, semesterId: String): List<GradeEntity> = emptyList()
    override suspend fun deleteGradesForSemester(studentId: String, semesterId: String) {}
    override suspend fun deleteAll() {}
}

class MockGradeCacheMetadataDao : GradeCacheMetadataDao {
    override suspend fun insert(metadata: GradeCacheMetadata) {}
    override suspend fun getMetadata(studentId: String, semesterId: String): GradeCacheMetadata? = null
    override suspend fun deleteMetadata(studentId: String, semesterId: String) {}
    override suspend fun deleteAll() {}
}

class MockSyncMetadataDao : SyncMetadataDao {
    override suspend fun insert(syncMetadataEntity: SyncMetadataEntity) {}
    override suspend fun insertAll(syncMetadataEntities: List<SyncMetadataEntity>) {}
    override suspend fun update(syncMetadataEntity: SyncMetadataEntity) {}
    override suspend fun delete(syncMetadataEntity: SyncMetadataEntity) {}
    override suspend fun getSyncMetadata(key: String): SyncMetadataEntity? = null
    override suspend fun clearAllSyncMetadata() {}
    override suspend fun getAllSyncMetadata(): List<SyncMetadataEntity> = emptyList()
    override suspend fun deleteByKey(key: String) {}
}

/**
 * The document cache, in a map.
 *
 * Behaves like the real DAO rather than answering nothing: the cache's whole job is what it does
 * on the second call, which a stub that always returns null can never show.
 */
open class InMemoryCachedDocumentDao : CachedDocumentDao {
    private val stored = mutableMapOf<String, CachedDocumentEntity>()

    override suspend fun insert(document: CachedDocumentEntity) {
        stored[document.downloadUrl] = document
    }

    override suspend fun get(downloadUrl: String): CachedDocumentEntity? = stored[downloadUrl]

    override suspend fun delete(downloadUrl: String) {
        stored.remove(downloadUrl)
    }

    override suspend fun deleteCachedAtOrBefore(cutoffTimestamp: Long): Int {
        val doomed = stored.values.filter { it.cachedAtTimestamp <= cutoffTimestamp }.map { it.downloadUrl }
        doomed.forEach { stored.remove(it) }
        return doomed.size
    }

    override suspend fun heads(headLength: Int): List<CachedDocumentHead> =
        stored.values.map { CachedDocumentHead(it.downloadUrl, it.content.copyOf(minOf(headLength, it.content.size))) }

    override suspend fun deleteAll() {
        stored.clear()
    }

    /** For the assertions: how many documents are held right now. */
    val size: Int get() = stored.size
}
