package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * File name of the database on every platform.
 *
 * Kept identical across platforms so an existing installation keeps its cache: the released app
 * has always written `grades_database.db`.
 */
const val DATABASE_FILE_NAME = "grades_database.db"

/**
 * Applies the migration policy to a platform builder and opens the database.
 *
 * The platform `getDatabaseBuilder()` actuals only decide *where* the file lives; everything about
 * *how* it is opened lives here, so the four platforms cannot drift apart on it.
 */
fun createRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations(*APP_DATABASE_MIGRATIONS)
        .fallbackToDestructiveMigrationFrom(
            dropAllTables = true,
            *DESTRUCTIBLE_SCHEMA_VERSIONS
        )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
