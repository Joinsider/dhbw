/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.util

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The first of the user's preferred languages, cut down to its language code.
 *
 * `preferredLanguages` rather than `currentLocale`: the entries are what the user actually ordered
 * in Settings ("de-DE", "en-GB"), which is also what the bundle uses to pick its strings — so a
 * notification and the screen behind it end up in the same language.
 */
actual fun currentLanguage(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)
        ?.substringBefore('-')
        ?.lowercase()
        ?: "en"
