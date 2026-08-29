/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.auth

import de.fampopprol.dhbwhorb.core.error.AppError

/** Why the username is not acceptable. The UI turns this into a sentence. */
enum class UsernameError {
    Empty,

    /** Not a `@hb.dhbw-stuttgart.de` or `@lehre.dhbw-stuttgart.de` address. */
    NotADhbwAddress
}

enum class PasswordError { Empty }

/**
 * The login form.
 *
 * The validation results are enums, not strings. `LoginFormViewModel.validateFields()` took three
 * localised messages as parameters, which meant the form could only be validated from a composable
 * that had already resolved them — and made the same check untestable without a resource loader.
 */
data class AuthState(
    val username: String = "",
    val password: String = "",
    val usernameError: UsernameError? = null,
    val passwordError: PasswordError? = null,
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val loginError: AppError? = null
)

sealed interface AuthIntent {
    data class UsernameChanged(val value: String) : AuthIntent
    data class PasswordChanged(val value: String) : AuthIntent
    data object PasswordVisibilityToggled : AuthIntent
    data object Submitted : AuthIntent
    data object Cleared : AuthIntent
}

sealed interface AuthMsg {
    data class UsernameChanged(val value: String) : AuthMsg
    data class PasswordChanged(val value: String) : AuthMsg
    data object PasswordVisibilityToggled : AuthMsg
    data class Invalid(val username: UsernameError?, val password: PasswordError?) : AuthMsg
    data object SubmitStarted : AuthMsg
    data object SubmitSucceeded : AuthMsg
    data class SubmitFailed(val error: AppError) : AuthMsg
    data object Cleared : AuthMsg
}

sealed interface AuthEffect {
    /** The session is established; the root can leave the login screen. */
    data object LoggedIn : AuthEffect
}
