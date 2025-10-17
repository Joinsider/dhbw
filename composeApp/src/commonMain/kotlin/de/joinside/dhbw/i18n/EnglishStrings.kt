/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.i18n

/**
 * English string resources.
 */
object EnglishStrings : Strings {
    // App general
    override val appName = "DHBW Horb"

    // Login screen
    override val loginWithDualisAccount = "Login with your Dualis Account"
    override val login = "Login"
    override val username = "Username"
    override val password = "Password"
    override val enterUsername = "Enter your username"
    override val enterPassword = "Enter your password"
    override val usernameCannotBeEmpty = "Username cannot be empty"
    override val passwordCannotBeEmpty = "Password cannot be empty"
    override val loginSuccessful = "Login successful"
    override val loginFailed = "Login failed"

    // Common
    override val ok = "OK"
    override val cancel = "Cancel"
    override val yes = "Yes"
    override val no = "No"
    override val save = "Save"
    override val delete = "Delete"
    override val edit = "Edit"
    override val back = "Back"
    override val next = "Next"
    override val finish = "Finish"
    override val loading = "Loading..."
    override val error = "Error"
    override val success = "Success"
    override val retry = "Retry"

    // Settings
    override val settings = "Settings"
    override val language = "Language"
    override val darkMode = "Dark Mode"
    override val lightMode = "Light Mode"
    override val systemDefault = "System Default"

    // Error messages
    override val networkError = "Network error. Please check your connection."
    override val unknownError = "An unknown error occurred."
    override val invalidCredentials = "Invalid username or password."
}

