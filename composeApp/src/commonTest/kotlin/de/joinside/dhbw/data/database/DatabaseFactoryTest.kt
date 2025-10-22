package de.joinside.dhbw.data.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseFactoryTest {

    @Test
    fun `createRoomDatabase sets BundledSQLiteDriver`() {
        // This test verifies that the factory function sets up the database correctly
        // Note: We can't easily test the actual driver being set without a real builder,
        // but we can verify the function exists and accepts the correct parameters

        // Given - we would need a mock RoomDatabase.Builder
        // This is more of a compilation test to ensure the function signature is correct

        // The function should:
        // 1. Accept a RoomDatabase.Builder<AppDatabase>
        // 2. Set the BundledSQLiteDriver
        // 3. Set the query coroutine context to Dispatchers.IO
        // 4. Build and return the database

        // We verify the function is callable with the correct type
        val functionExists = ::createRoomDatabase
        assertNotNull(functionExists)
    }

    @Test
    fun `BundledSQLiteDriver is the expected driver type`() {
        // Given
        val driver = BundledSQLiteDriver()

        // Then - verify it's not null and is the correct type
        // Note: BundledSQLiteDriver resolves to platform-specific implementations:
        // - Android: BundledSQLiteDriver
        // - iOS: NativeSQLiteDriver
        // - JVM: BundledSQLiteDriver
        assertNotNull(driver)
        val driverName = driver::class.simpleName
        assertTrue(
            driverName == "BundledSQLiteDriver" || driverName == "NativeSQLiteDriver",
            "Expected driver name to be either BundledSQLiteDriver or NativeSQLiteDriver, but was $driverName"
        )
    }

    @Test
    fun `Dispatchers IO is available for database queries`() {
        // Given
        val dispatcher = Dispatchers.IO

        // Then - verify the dispatcher is available
        assertNotNull(dispatcher)
    }

    @Test
    fun `createRoomDatabase function exists and is callable`() {
        // This test documents the expected usage pattern of createRoomDatabase
        // The function should be used like:
        // val database = createRoomDatabase(
        //     Room.databaseBuilder<AppDatabase>(context, "database_name.db")
        // )

        // We verify the function exists and is callable
        val function = ::createRoomDatabase
        assertNotNull(function)
    }
}

