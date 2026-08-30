package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.RoomDatabase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
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
     * @param dispatcher Where the initialization runs; defaults to [Dispatchers.IO] but is
     *   injectable so tests can use a deterministic dispatcher.
     * @return The initialized AppDatabase instance.
     */
    suspend fun initializeDatabaseAsync(
        builder: RoomDatabase.Builder<AppDatabase>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): AppDatabase = withContext(dispatcher) {
        Napier.d("Initializing database asynchronously...", tag = TAG)
        val db = createRoomDatabase(builder)
        Napier.d("Database initialized successfully", tag = TAG)
        db
    }
}
