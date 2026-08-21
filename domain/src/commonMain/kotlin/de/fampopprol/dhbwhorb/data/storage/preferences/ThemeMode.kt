/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.preferences

/**
 * The three theme settings the user can choose between.
 *
 * Lives in `:domain` because [de.fampopprol.dhbwhorb.domain.repository.PreferencesRepository]
 * returns it; the package name is unchanged, so nothing that reads it had to be touched.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        /** Unknown or corrupted stored values fall back to [SYSTEM] rather than failing. */
        fun fromString(value: String): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
