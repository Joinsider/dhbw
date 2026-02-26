@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeCacheMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeCacheMetadata
import de.fampopprol.dhbwhorb.data.storage.database.converters.DateTimeConverter
import de.fampopprol.dhbwhorb.data.storage.database.dao.SyncMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LecturerDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.SyncMetadataEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity

@Database(
    entities = [
        GradeEntity::class,
        GradeCacheMetadata::class,
        LectureEventEntity::class,
        LecturerEntity::class,
        LectureLecturerCrossRef::class,
        SyncMetadataEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(DateTimeConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gradeDao(): GradeDao
    abstract fun gradeCacheMetadataDao(): GradeCacheMetadataDao

    abstract fun lectureDao(): LectureEventDao
    abstract fun lecturerDao(): LecturerDao
    abstract fun lectureLecturerCrossRefDao(): LectureLecturerCrossRefDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    /**
     * Clear all data from the database.
     * This is used during logout to remove all cached data.
     */
    suspend fun clearAllData() {
        // Clear all tables
        lectureLecturerCrossRefDao().deleteAll()
        lectureDao().deleteAll()
        lecturerDao().deleteAll()
        gradeDao().deleteAll()
        gradeCacheMetadataDao().deleteAll()
        syncMetadataDao().clearAllSyncMetadata()
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
