package de.fampopprol.dhbwhorb.data.storage.database

import androidx.room.migration.Migration

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
const val APP_DATABASE_VERSION = 4

/**
 * Migrations for every schema step at or above [OLDEST_SUPPORTED_SCHEMA_VERSION].
 *
 * Empty because version 4 is both the oldest released and the current schema — there is no step to
 * bridge yet. It stops being empty the moment an entity changes; see [APP_DATABASE_VERSION].
 */
val APP_DATABASE_MIGRATIONS: Array<Migration> = emptyArray()

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
