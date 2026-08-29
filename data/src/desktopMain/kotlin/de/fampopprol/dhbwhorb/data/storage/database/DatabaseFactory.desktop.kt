package de.fampopprol.dhbwhorb.data.storage.database


import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import java.util.Locale

// The migration policy lives in `createRoomDatabase`, not here — this only picks the file.
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(desktopDataDirectory(), DATABASE_FILE_NAME)
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
}

/**
 * Per-user data directory for the desktop build.
 *
 * The database used to live in `java.io.tmpdir`, where the operating system is free to delete it —
 * removing the destructive migration fallback would have been pointless while the file itself was
 * disposable. Existing desktop installations re-sync once after this change; the database is a
 * Dualis cache, not a primary store.
 */
private fun desktopDataDirectory(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    val home = File(System.getProperty("user.home"))
    val directory = when {
        os.contains("mac") -> File(home, "Library/Application Support/$APPLICATION_DIRECTORY_NAME")
        os.contains("win") -> File(
            System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path,
            APPLICATION_DIRECTORY_NAME
        )
        else -> File(
            System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path,
            APPLICATION_DIRECTORY_NAME
        )
    }
    directory.mkdirs()
    return directory
}

private const val APPLICATION_DIRECTORY_NAME = "dhbw-horb-student-app"
