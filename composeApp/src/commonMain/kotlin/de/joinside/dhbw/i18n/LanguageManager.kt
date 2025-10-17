/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Manages language preferences across the application.
 */
class LanguageManager {
    private val _currentLanguage = mutableStateOf<Language?>(null)

    /**
     * Gets the current language, defaulting to system language if not set.
     */
    fun getCurrentLanguage(): Language {
        return _currentLanguage.value ?: getSystemLanguage()
    }

    /**
     * Sets the current language preference.
     */
    fun setLanguage(language: Language) {
        _currentLanguage.value = language
    }

    /**
     * Resets to system default language.
     */
    fun resetToSystemDefault() {
        _currentLanguage.value = null
    }

    /**
     * Returns true if using system default language.
     */
    fun isUsingSystemDefault(): Boolean {
        return _currentLanguage.value == null
    }

    companion object {
        private var instance: LanguageManager? = null

        fun getInstance(): LanguageManager {
            if (instance == null) {
                instance = LanguageManager()
            }
            return instance!!
        }
    }
}

/**
 * Remember language manager in Composable context.
 */
@Composable
fun rememberLanguageManager(): LanguageManager {
    return remember { LanguageManager.getInstance() }
}
