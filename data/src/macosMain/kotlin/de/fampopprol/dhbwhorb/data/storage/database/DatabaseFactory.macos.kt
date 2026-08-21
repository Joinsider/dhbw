package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

// Application Support is already a persistent, per-app location and macOS ships no widget
// extension, so unlike iOS this needs no app-group container. The migration policy lives in
// `createRoomDatabase`.
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = applicationSupportDirectory() + "/" + DATABASE_FILE_NAME
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath,
    )
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
