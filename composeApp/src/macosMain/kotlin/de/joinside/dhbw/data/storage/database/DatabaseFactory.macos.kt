package de.joinside.dhbw.data.storage.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = applicationSupportDirectory() + "/grades_database.db"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath,
    ).fallbackToDestructiveMigration(dropAllTables = true)
}

@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportDirectory(): String {
    val applicationSupportDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(applicationSupportDirectory?.path)
}

