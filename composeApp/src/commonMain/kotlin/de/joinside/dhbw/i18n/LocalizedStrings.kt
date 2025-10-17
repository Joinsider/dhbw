/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * CompositionLocal for providing strings throughout the app.
 */
val LocalStrings = compositionLocalOf<Strings> { EnglishStrings }

/**
 * Provides string resources based on the selected language.
 */
@Composable
fun ProvideStrings(
    language: Language = Language.ENGLISH,
    content: @Composable () -> Unit
) {
    val strings = remember(language) {
        when (language) {
            Language.ENGLISH -> EnglishStrings
            Language.GERMAN -> GermanStrings
        }
    }

    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

/**
 * Access strings from any Composable function.
 */
@Composable
fun strings(): Strings = LocalStrings.current

/**
 * Supported languages in the application.
 */
enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: ENGLISH
        }
    }
}

