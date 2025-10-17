/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.i18n

/**
 * Interface for string resources across all languages.
 * Each language implementation provides translations for all strings.
 */
interface Strings {
    // App general
    val appName: String

    // Login screen
    val loginWithDualisAccount: String
    val login: String
    val username: String
    val password: String
    val enterUsername: String
    val enterPassword: String
    val usernameCannotBeEmpty: String
    val passwordCannotBeEmpty: String
    val loginSuccessful: String
    val loginFailed: String

    // Common
    val ok: String
    val cancel: String
    val yes: String
    val no: String
    val save: String
    val delete: String
    val edit: String
    val back: String
    val next: String
    val finish: String
    val loading: String
    val error: String
    val success: String
    val retry: String

    // Settings
    val settings: String
    val language: String
    val darkMode: String
    val lightMode: String
    val systemDefault: String

    // Error messages
    val networkError: String
    val unknownError: String
    val invalidCredentials: String
}

