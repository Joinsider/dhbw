package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

private const val TAG = "DatabaseFactory"

/**
 * App group shared by the app and the timetable widget extension.
 *
 * Must stay in sync with `com.apple.security.application-groups` in `iosApp.entitlements` and
 * `TimetableWidget.entitlements`.
 */
const val IOS_APP_GROUP_IDENTIFIER = "group.de.fampopprol.dhbwhorb"

/**
 * Opens the database inside the app group container.
 *
 * The database used to live in `NSDocumentDirectory`, which is private to the app process — the
 * widget extension cannot read it there. The migration policy lives in `createRoomDatabase`.
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val directory = appGroupDirectory()
    if (directory != null) {
        discardLegacyDocumentDirectoryDatabase()
    }
    val dbFilePath = (directory ?: documentDirectory()) + "/" + DATABASE_FILE_NAME
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun appGroupDirectory(): String? {
    val container = NSFileManager.defaultManager
        .containerURLForSecurityApplicationGroupIdentifier(IOS_APP_GROUP_IDENTIFIER)
        ?.path
    if (container == null) {
        // Not a crash: without the entitlement the app still works, only the widget stays blind.
        Napier.e(
            "App group $IOS_APP_GROUP_IDENTIFIER unavailable — falling back to the document " +
                "directory. The widget extension will not see this database.",
            tag = TAG,
        )
    }
    return container
}

/**
 * Removes the pre-move database from the document directory.
 *
 * Deliberately deletes instead of copying: the database is a Dualis cache, so the next fetch
 * refills it. A copy path would mean two locations to reason about forever, for data that costs
 * one request to rebuild.
 */
@OptIn(ExperimentalForeignApi::class)
private fun discardLegacyDocumentDirectoryDatabase() {
    val fileManager = NSFileManager.defaultManager
    val legacyBasePath = documentDirectory() + "/" + DATABASE_FILE_NAME
    // WAL mode keeps two sidecar files next to the database and the bundled driver adds a lock
    // file; leaving any of them behind would strand bytes in the old location forever.
    listOf(
        legacyBasePath,
        "$legacyBasePath-wal",
        "$legacyBasePath-shm",
        "$legacyBasePath.lck",
    ).forEach { path ->
        if (fileManager.fileExistsAtPath(path)) {
            fileManager.removeItemAtPath(path, error = null)
            Napier.i("Removed legacy database file at $path", tag = TAG)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
