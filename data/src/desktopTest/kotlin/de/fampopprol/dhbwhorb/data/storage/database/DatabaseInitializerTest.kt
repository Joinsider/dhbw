/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseInitializerTest {

    private val databaseFile = Files.createTempFile("dhbw-initializer-test", ".db").toFile()

    @AfterTest
    fun cleanUp() {
        databaseFile.delete()
    }

    @Test
    fun initializeDatabaseAsync_buildsAUsableDatabase() = runTest {
        val builder = Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath)

        val database = DatabaseInitializer.initializeDatabaseAsync(
            builder = builder,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        assertNotNull(database)
        database.close()
    }
}
