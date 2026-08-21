/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.auth

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.usecase.LoginWithCredentials
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import kotlinx.coroutines.CoroutineScope

class AuthStore(
    private val loginWithCredentials: LoginWithCredentials,
    scope: CoroutineScope
) : BaseStore<AuthState, AuthIntent, AuthMsg, AuthEffect>(
    initialState = AuthState(),
    scope = scope
) {
    private companion object {
        /** Dualis accounts are `first.last@hb.` or `@lehre.dhbw-stuttgart.de`. */
        val DHBW_ADDRESS = Regex("""^[a-zA-Z0-9.]+@(?:hb|lehre)\.dhbw-stuttgart\.de$""")
    }

    /** One login attempt at a time; a double tap on the button must not send two. */
    override fun dedupeKey(intent: AuthIntent): Any? =
        if (intent is AuthIntent.Submitted) "submit" else null

    override fun reduce(state: AuthState, msg: AuthMsg): AuthState = reduceAuth(state, msg)

    override suspend fun EffectScope<AuthMsg, AuthEffect>.handle(
        intent: AuthIntent,
        state: AuthState
    ) {
        when (intent) {
            is AuthIntent.UsernameChanged -> emit(AuthMsg.UsernameChanged(intent.value))
            is AuthIntent.PasswordChanged -> emit(AuthMsg.PasswordChanged(intent.value))
            AuthIntent.PasswordVisibilityToggled -> emit(AuthMsg.PasswordVisibilityToggled)
            AuthIntent.Cleared -> emit(AuthMsg.Cleared)

            AuthIntent.Submitted -> {
                val usernameError = validateUsername(state.username)
                val passwordError = if (state.password.isBlank()) PasswordError.Empty else null

                if (usernameError != null || passwordError != null) {
                    emit(AuthMsg.Invalid(usernameError, passwordError))
                    return
                }

                emit(AuthMsg.SubmitStarted)
                when (val result = loginWithCredentials(state.username, state.password)) {
                    is Outcome.Ok -> {
                        emit(AuthMsg.SubmitSucceeded)
                        send(AuthEffect.LoggedIn)
                    }
                    is Outcome.Err -> emit(AuthMsg.SubmitFailed(result.error))
                }
            }
        }
    }

    private fun validateUsername(username: String): UsernameError? = when {
        username.isBlank() -> UsernameError.Empty
        !DHBW_ADDRESS.matches(username.lowercase()) -> UsernameError.NotADhbwAddress
        else -> null
    }
}

/**
 * The login form state after [msg].
 *
 * Top-level and therefore unable to reach a store, a repository or a scope: the reducer's purity
 * is structural rather than a promise. Its tests call it directly, with no coroutines involved.
 */
fun reduceAuth(state: AuthState, msg: AuthMsg): AuthState = when (msg) {
    // Typing clears the field's error: the user is already fixing it.
    is AuthMsg.UsernameChanged -> state.copy(
        username = msg.value,
        usernameError = null,
        loginError = null
    )

    is AuthMsg.PasswordChanged -> state.copy(
        password = msg.value,
        passwordError = null,
        loginError = null
    )

    AuthMsg.PasswordVisibilityToggled ->
        state.copy(isPasswordVisible = !state.isPasswordVisible)

    is AuthMsg.Invalid -> state.copy(
        usernameError = msg.username,
        passwordError = msg.password,
        isSubmitting = false
    )

    AuthMsg.SubmitStarted -> state.copy(isSubmitting = true, loginError = null)

    // The password does not stay in memory a moment longer than it has to.
    AuthMsg.SubmitSucceeded -> AuthState()

    is AuthMsg.SubmitFailed -> state.copy(isSubmitting = false, loginError = msg.error)

    AuthMsg.Cleared -> AuthState()
}
