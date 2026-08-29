package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.RoomDatabase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Utility for initializing the Room database asynchronously.
 * This is used to prevent blocking the main thread during app startup.
 */
object DatabaseInitializer {
    private const val TAG = "DatabaseInitializer"

    /**
     * Initializes the Room database asynchronously on a background thread.
     * @param builder The RoomDatabase.Builder configured for the current platform.
     * @return The initialized AppDatabase instance.
     */
    suspend fun initializeDatabaseAsync(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase = withContext(Dispatchers.IO) {
        Napier.d("Initializing database asynchronously...", tag = TAG)
        val db = createRoomDatabase(builder)
        Napier.d("Database initialized successfully", tag = TAG)
        db
    }
}
