package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.migration.Migration
import androidx.sqlite.execSQL

/**
 * Schema version of [AppDatabase].
 *
 * Declared here rather than inline in the `@Database` annotation so that the migration guard in
 * the test suite can read it without reflection — Room's `@Database` annotation is not retained
 * at runtime.
 *
 * Raising this constant obliges you to add the matching [Migration] to [APP_DATABASE_MIGRATIONS]
 * and to commit the schema export Room writes to `data/schemas/`. The guard test fails otherwise.
 */
const val APP_DATABASE_VERSION = 6

/**
 * Schema 5 adds `grades.resultId`, the key to a module's "Ergebnisdetails" page in Dualis.
 *
 * Added as a nullable column with no default: every cached row keeps its data and simply has no
 * id until the next refresh reads one off the page. Dropping the cache instead would have been a
 * silent re-download of every semester on first launch after the update.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("ALTER TABLE grades ADD COLUMN resultId TEXT")
    }
}

/**
 * Schema 6 adds `cached_documents`, where a downloaded document is kept with its SHA-256 for at
 * most four weeks.
 *
 * A new table only: nothing existing is touched, so an upgrade keeps every cached grade and
 * lecture and simply starts out with no documents cached.
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_documents` (" +
                "`downloadUrl` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`contentHash` TEXT NOT NULL, " +
                "`content` BLOB NOT NULL, " +
                "`cachedAtTimestamp` INTEGER NOT NULL, " +
                "PRIMARY KEY(`downloadUrl`))"
        )
    }
}

/**
 * Migrations for every schema step at or above [OLDEST_SUPPORTED_SCHEMA_VERSION].
 *
 * Raising [APP_DATABASE_VERSION] obliges you to add the matching step here; the guard test in
 * `:data` fails otherwise.
 */
val APP_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_5_6)

/**
 * The oldest schema version that carries real user data.
 *
 * Every published release of this app (v2.0.0 onwards) shipped schema 4. Versions 1 to 3 existed
 * only during development before the first release; their exports still sit in `data/schemas/`
 * under the pre-rename package name.
 */
const val OLDEST_SUPPORTED_SCHEMA_VERSION = 4

/**
 * Schema versions that may be dropped instead of migrated.
 *
 * These are the pre-release development schemas — no installation in the wild holds one, and their
 * `grades` table is not convertible into the current one (v3 replaced it wholesale). Naming them
 * explicitly is the point: an unmigratable *future* gap now fails loudly at open time instead of
 * silently deleting the user's cached grades and timetable, which is what the blanket
 * `fallbackToDestructiveMigration(dropAllTables = true)` used to do on all four platforms.
 */
val DESTRUCTIBLE_SCHEMA_VERSIONS = intArrayOf(1, 2, 3)
