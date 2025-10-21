package de.joinside.dhbw

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import de.joinside.dhbw.data.database.dao.GradesDao
import de.joinside.dhbw.data.database.dao.SemesterDao
import de.joinside.dhbw.data.database.entities.grades.GradesEntity
import de.joinside.dhbw.data.database.entities.grades.SemesterEntity

// shared/src/commonMain/kotlin/Database.kt

@Database(
    entities = [SemesterEntity::class, GradesEntity::class],
    version = 1,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun gradesDao(): GradesDao
}


// The Room compiler generates the `actual` implementations.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
