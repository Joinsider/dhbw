/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.i18n

import java.util.Locale

/**
 * Android implementation: Detects system language from Locale.
 */
actual fun getSystemLanguage(): Language {
    val systemLanguageCode = Locale.getDefault().language
    return when (systemLanguageCode) {
        "de" -> Language.GERMAN
        else -> Language.ENGLISH
    }
}

