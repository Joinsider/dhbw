package de.fampopprol.dhbwhorb.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.fampopprol.dhbwhorb.data.storage.database.APP_DATABASE_MIGRATIONS
import de.fampopprol.dhbwhorb.data.storage.database.APP_DATABASE_VERSION
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.database.DESTRUCTIBLE_SCHEMA_VERSIONS
import de.fampopprol.dhbwhorb.data.storage.database.OLDEST_SUPPORTED_SCHEMA_VERSION
import de.fampopprol.dhbwhorb.data.storage.database.createRoomDatabase
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the promise of P6: an app update must not silently drop the user's cached grades and
 * timetable. Until this phase all four platforms opened the database with
 * `fallbackToDestructiveMigration(dropAllTables = true)`, which deletes everything on any version
 * mismatch — a green build and a passing test suite both stayed green while the data went away.
 */
class AppDatabaseMigrationTest {

    private val databaseDirectory: Path = Files.createTempDirectory("dhbw-migration-test")
    private val databasePath: Path = databaseDirectory.resolve("grades_database.db")

    private fun helper() = MigrationTestHelper(
        schemaDirectoryPath = schemaDirectory(),
        databasePath = databasePath,
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @AfterTest
    fun cleanUp() {
        databaseDirectory.toFile().deleteRecursively()
    }

    @Test
    fun `a version 4 database keeps its rows when the current app opens it`() = runTest {
        helper().createDatabase(version = 4).use { connection ->
            connection.execSQL(
                "INSERT INTO grades (id, studentId, semesterId, semesterName, moduleNumber, " +
                    "moduleName, grade, credits, status) VALUES " +
                    "(1, 'student-1', 'sem-1', 'WS 2025/26', 'T3INF1001', 'Mathematik I', " +
                    "'1.7', 5.0, 'bestanden')"
            )
            connection.execSQL(
                "INSERT INTO lecture (lectureId, shortSubjectName, fullSubjectName, startTime, " +
                    "endTime, location, isTest, fetchedAt) VALUES " +
                    "(1, 'Mathe I', 'Mathematik I', '2026-08-21T08:00:00', " +
                    "'2026-08-21T11:15:00', 'A101', 0, '2026-08-21T07:00:00')"
            )
        }

        openCurrentDatabase().useDatabase { database ->
            val grades = database.gradeDao().getGradesForSemester("student-1", "sem-1")
            assertEquals(1, grades.size, "the grade from the previous version was dropped")
            assertEquals("1.7", grades.single().grade)

            val lectures = database.lectureDao().getAll()
            assertEquals(1, lectures.size, "the lecture from the previous version was dropped")
            assertEquals("Mathe I", lectures.single().shortSubjectName)
        }
    }

    @Test
    fun `the declared migrations carry a version 4 database to the current schema`() {
        helper().createDatabase(version = 4).close()

        // Throws if the migrations leave the schema differing from the exported JSON in any way.
        helper().runMigrationsAndValidate(
            version = APP_DATABASE_VERSION,
            migrations = APP_DATABASE_MIGRATIONS.toList(),
        ).close()
    }

    @Test
    fun `a pre-release schema is rebuilt instead of blocking the app`() = runTest {
        // Version 3 never shipped; it predates the first release and its `grades` table cannot be
        // converted. Dropping it is the declared exception, not the default.
        assertTrue(3 in DESTRUCTIBLE_SCHEMA_VERSIONS.toList())

        helper().createDatabase(version = 3).use { connection ->
            connection.execSQL(
                "INSERT INTO grades (id, studentId, semesterId, semesterName, moduleNumber, " +
                    "moduleName, grade, credits, status) VALUES " +
                    "(1, 'student-1', 'sem-1', 'WS 2025/26', 'T3INF1001', 'Mathematik I', " +
                    "'1.7', 5.0, 'bestanden')"
            )
        }

        openCurrentDatabase().useDatabase { database ->
            assertEquals(
                emptyList(),
                database.gradeDao().getGradesForSemester("student-1", "sem-1"),
                "a dropped pre-release schema must leave an empty, usable database",
            )
        }
    }

    @Test
    fun `every schema version at or above the oldest supported one has a migration`() {
        val migrations = APP_DATABASE_MIGRATIONS.associateBy { it.startVersion }
        (OLDEST_SUPPORTED_SCHEMA_VERSION until APP_DATABASE_VERSION).forEach { version ->
            val migration = migrations[version]
            assertTrue(
                migration != null && migration.endVersion == version + 1,
                "no migration from schema $version to ${version + 1}: raising " +
                    "APP_DATABASE_VERSION obliges you to add one to APP_DATABASE_MIGRATIONS",
            )
        }
    }

    @Test
    fun `the current schema is exported`() {
        val exported = schemaDirectory().resolve(AppDatabase::class.qualifiedName!!)
        assertTrue(
            exported.resolve("$APP_DATABASE_VERSION.json").exists(),
            "data/schemas holds no export for version $APP_DATABASE_VERSION — run the build and " +
                "commit what Room writes, otherwise the next migration has nothing to test against",
        )
        val exportedVersions = exported.listDirectoryEntries("*.json")
            .mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
        assertEquals(
            APP_DATABASE_VERSION,
            exportedVersions.max(),
            "an export newer than APP_DATABASE_VERSION exists — the version constant was lowered " +
                "or an export was committed without bumping it",
        )
    }

    private fun openCurrentDatabase(): AppDatabase =
        createRoomDatabase(Room.databaseBuilder<AppDatabase>(name = databasePath.toString()))

    private inline fun <T> AppDatabase.useDatabase(block: (AppDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}

/**
 * Locates `data/schemas` from the test's working directory, which differs between a Gradle run and
 * an IDE run.
 */
private fun schemaDirectory(): Path {
    var directory: Path? = Path(System.getProperty("user.dir")).toAbsolutePath()
    while (directory != null) {
        val candidate = directory.resolve("data").resolve("schemas")
        if (candidate.exists()) return candidate
        directory = directory.parent
    }
    error("data/schemas not found above ${System.getProperty("user.dir")}")
}
