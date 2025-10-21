package de.joinside.dhbw.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import de.joinside.dhbw.data.database.dao.grades.GradesDao
import de.joinside.dhbw.data.database.dao.grades.SemesterDao
import de.joinside.dhbw.data.database.entities.grades.GradesEntity
import de.joinside.dhbw.data.database.entities.grades.SemesterEntity
import de.joinside.dhbw.data.database.converters.DateTimeConverter
import de.joinside.dhbw.data.database.dao.timetable.LectureDao
import de.joinside.dhbw.data.database.dao.timetable.LectureLecturerCrossRefDao
import de.joinside.dhbw.data.database.dao.timetable.LecturerDao
import de.joinside.dhbw.data.database.dao.timetable.SubjectDao
import de.joinside.dhbw.data.database.entities.timetable.LectureEntity
import de.joinside.dhbw.data.database.entities.timetable.LectureLecturerCrossRef
import de.joinside.dhbw.data.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.database.entities.timetable.SubjectEntity

@Database(
    entities = [
        SemesterEntity::class,
        GradesEntity::class,
        LectureEntity::class,
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

    abstract fun lectureDao(): LectureDao
    abstract fun subjectDao(): SubjectDao
    abstract fun lecturerDao(): LecturerDao
    abstract fun lectureLecturerCrossRefDao(): LectureLecturerCrossRefDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
