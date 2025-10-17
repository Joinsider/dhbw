/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.i18n

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/**
 * iOS implementation: Detects system language from NSLocale.
 */
actual fun getSystemLanguage(): Language {
    val systemLanguageCode = NSLocale.currentLocale.languageCode
    return when (systemLanguageCode) {
        "de" -> Language.GERMAN
        else -> Language.ENGLISH
    }
}

