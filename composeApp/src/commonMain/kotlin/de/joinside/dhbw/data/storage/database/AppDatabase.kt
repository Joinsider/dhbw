package de.joinside.dhbw.data.storage.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import de.joinside.dhbw.data.storage.database.dao.grades.GradesDao
import de.joinside.dhbw.data.storage.database.dao.grades.SemesterDao
import de.joinside.dhbw.data.storage.database.entities.grades.GradesEntity
import de.joinside.dhbw.data.storage.database.entities.grades.SemesterEntity
import de.joinside.dhbw.data.storage.database.converters.DateTimeConverter
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureEventDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LecturerDao
import de.joinside.dhbw.data.storage.database.dao.timetable.SubjectDao
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.SubjectEntity

@Database(
    entities = [
        SemesterEntity::class,
        GradesEntity::class,
        LectureEventEntity::class,
        SubjectEntity::class,
        LecturerEntity::class,
        LectureLecturerCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateTimeConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun gradesDao(): GradesDao

    abstract fun lectureDao(): LectureEventDao
    abstract fun subjectDao(): SubjectDao
    abstract fun lecturerDao(): LecturerDao
    abstract fun lectureLecturerCrossRefDao(): LectureLecturerCrossRefDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
