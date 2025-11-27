@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package de.joinside.dhbw.data.storage.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import de.joinside.dhbw.data.storage.database.dao.grades.GradeDao
import de.joinside.dhbw.data.storage.database.entities.grades.GradeEntity
import de.joinside.dhbw.data.storage.database.converters.DateTimeConverter
import de.joinside.dhbw.data.storage.database.dao.SyncMetadataDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureEventDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LecturerDao
import de.joinside.dhbw.data.storage.database.entities.SyncMetadataEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity

@Database(
    entities = [
        GradeEntity::class,
        LectureEventEntity::class,
        LecturerEntity::class,
        LectureLecturerCrossRef::class,
        SyncMetadataEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(DateTimeConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gradeDao(): GradeDao

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
        gradeDao().deleteAll() // Note: deleteAll needs to be added to GradeDao if it's not there.
        syncMetadataDao().clearAllSyncMetadata()
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
