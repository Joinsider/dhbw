/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.data.storage.preferences

import de.joinside.dhbw.data.storage.credentials.SecureStorage

/**
 * Enum representing the available theme modes
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun fromString(value: String): ThemeMode {
            return try {
                valueOf(value)
            } catch (_: IllegalArgumentException) {
                SYSTEM // Default to system if invalid value
            }
        }
    }
}

/**
 * Manages theme preferences using SecureStorage
 */
class ThemePreferences(private val storage: SecureStorage) {

    companion object {
        private const val THEME_MODE_KEY = "theme_mode_preference"
        private const val MATERIAL_YOU_KEY = "material_you_preference"
        private const val CUSTOM_COLOR_KEY = "custom_color_preference"
    }

    /**
     * Get the current theme mode preference
     * @return The selected ThemeMode, defaults to SYSTEM
     */
    fun getThemeMode(): ThemeMode {
        val storedValue = storage.getString(THEME_MODE_KEY, ThemeMode.SYSTEM.name)
        return ThemeMode.fromString(storedValue)
    }

    /**
     * Set the theme mode preference
     * @param mode The ThemeMode to set
     */
    fun setThemeMode(mode: ThemeMode) {
        storage.setString(THEME_MODE_KEY, mode.name)
    }

    /**
     * Get the Material You preference (Android only)
     * @return True if Material You is enabled, defaults to true
     */
    fun getMaterialYouEnabled(): Boolean {
        val storedValue = storage.getString(MATERIAL_YOU_KEY, "true")
        return storedValue == "true"
    }

    /**
     * Set the Material You preference (Android only)
     * @param enabled True to enable Material You, false to use static colors
     */
    fun setMaterialYouEnabled(enabled: Boolean) {
        storage.setString(MATERIAL_YOU_KEY, enabled.toString())
    }

    /**
     * Get the custom color preference
     * @return The selected color as Long (ARGB), defaults to Purple40 (0xFF6650a4)
     */
    fun getCustomColor(): Long {
        val storedValue = storage.getString(CUSTOM_COLOR_KEY, "4284932260") // 0xFF6650a4 in decimal
        return storedValue.toLongOrNull() ?: 4284932260
    }

    /**
     * Set the custom color preference
     * @param color The color as Long (ARGB)
     */
    fun setCustomColor(color: Long) {
        storage.setString(CUSTOM_COLOR_KEY, color.toString())
    }
}

