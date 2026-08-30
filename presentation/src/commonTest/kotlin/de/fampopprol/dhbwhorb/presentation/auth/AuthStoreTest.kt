/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.auth

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.domain.usecase.LoginWithCredentials
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.collectEffects
import de.fampopprol.dhbwhorb.testutil.fakes.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Replaces `LoginFormViewModelTest`.
 *
 * The validation half needs no coroutines at all now, and it asserts enum values rather than the
 * English strings the old ViewModel was handed — so the same check holds whatever language the app
 * runs in.
 */
class AuthStoreTest {

    private fun store(repository: FakeAuthRepository = FakeAuthRepository()) =
        AuthStore(LoginWithCredentials(repository), TestScopes.immediate())

    // ── reducer: no coroutines ──────────────────────────────────────────────

    @Test
    fun initialState_isEmpty() {
        val state = AuthState()

        assertEquals("", state.username)
        assertEquals("", state.password)
        assertNull(state.usernameError)
        assertNull(state.passwordError)
        assertFalse(state.isPasswordVisible)
    }

    @Test
    fun typingAUsername_clearsItsError() {
        val invalid = reduceAuth(
            AuthState(),
            AuthMsg.Invalid(UsernameError.Empty, null)
        )
        assertEquals(UsernameError.Empty, invalid.usernameError)

        val typing = reduceAuth(invalid, AuthMsg.UsernameChanged("a"))
        assertNull(typing.usernameError, "The user is already fixing it")
    }

    @Test
    fun typingAPassword_clearsItsError() {
        val invalid = reduceAuth(AuthState(), AuthMsg.Invalid(null, PasswordError.Empty))
        val typing = reduceAuth(invalid, AuthMsg.PasswordChanged("x"))

        assertNull(typing.passwordError)
    }

    @Test
    fun togglingVisibility_flipsBackAndForth() {
        val once = reduceAuth(AuthState(), AuthMsg.PasswordVisibilityToggled)
        assertTrue(once.isPasswordVisible)

        val twice = reduceAuth(once, AuthMsg.PasswordVisibilityToggled)
        assertFalse(twice.isPasswordVisible)
    }

    @Test
    fun aSuccessfulLogin_dropsTheCredentialsFromMemory() {
        val filled = AuthState(username = "a@hb.dhbw-stuttgart.de", password = "secret")

        val after = reduceAuth(filled, AuthMsg.SubmitSucceeded)

        assertEquals("", after.password, "The password must not outlive the login")
        assertEquals("", after.username)
    }

    // ── validation, through the effect handler ──────────────────────────────

    @Test
    fun anEmptyUsername_isRejectedWithoutAskingDualis() = runTest {
        val repository = FakeAuthRepository()
        val store = store(repository)

        store.dispatch(AuthIntent.PasswordChanged("secret"))
        store.dispatch(AuthIntent.Submitted)

        assertEquals(UsernameError.Empty, store.state.value.usernameError)
        assertEquals(0, repository.loginCount, "Nothing should reach the network")
        store.close()
    }

    @Test
    fun anEmptyPassword_isRejected() = runTest {
        val store = store()

        store.dispatch(AuthIntent.UsernameChanged("a.b@hb.dhbw-stuttgart.de"))
        store.dispatch(AuthIntent.Submitted)

        assertEquals(PasswordError.Empty, store.state.value.passwordError)
        store.close()
    }

    @Test
    fun bothEmpty_reportsBothFields() = runTest {
        val store = store()

        store.dispatch(AuthIntent.Submitted)

        assertEquals(UsernameError.Empty, store.state.value.usernameError)
        assertEquals(PasswordError.Empty, store.state.value.passwordError)
        store.close()
    }

    @Test
    fun aNonDhbwAddress_isRejected() = runTest {
        val store = store()

        store.dispatch(AuthIntent.UsernameChanged("someone@gmail.com"))
        store.dispatch(AuthIntent.PasswordChanged("secret"))
        store.dispatch(AuthIntent.Submitted)

        assertEquals(UsernameError.NotADhbwAddress, store.state.value.usernameError)
        store.close()
    }

    @Test
    fun aValidDhbwAddress_reachesTheLogin() = runTest {
        val repository = FakeAuthRepository()
        val store = store(repository)

        store.dispatch(AuthIntent.UsernameChanged("max.mustermann@hb.dhbw-stuttgart.de"))
        store.dispatch(AuthIntent.PasswordChanged("secret"))
        store.dispatch(AuthIntent.Submitted)

        assertEquals(1, repository.loginCount)
        assertNull(store.state.value.usernameError)
        store.close()
    }

    @Test
    fun theLehreDomain_isAlsoAccepted() = runTest {
        val repository = FakeAuthRepository()
        val store = store(repository)

        store.dispatch(AuthIntent.UsernameChanged("max.mustermann@lehre.dhbw-stuttgart.de"))
        store.dispatch(AuthIntent.PasswordChanged("secret"))
        store.dispatch(AuthIntent.Submitted)

        assertEquals(1, repository.loginCount)
        store.close()
    }

    @Test
    fun aRejectedLogin_keepsTheFormAndShowsWhy() = runTest {
        val repository = FakeAuthRepository(loginResult = Outcome.Err(AppError.InvalidCredentials))
        val store = store(repository)

        store.dispatch(AuthIntent.UsernameChanged("max@hb.dhbw-stuttgart.de"))
        store.dispatch(AuthIntent.PasswordChanged("wrong"))
        store.dispatch(AuthIntent.Submitted)

        val state = store.state.value
        assertEquals(AppError.InvalidCredentials, state.loginError)
        assertFalse(state.isSubmitting)
        assertEquals("max@hb.dhbw-stuttgart.de", state.username, "Retyping the address is a chore")
        store.close()
    }

    @Test
    fun aSuccessfulLogin_announcesItself() = runTest {
        val repository = FakeAuthRepository(
            loginResult = Outcome.Ok(Session(userFullName = "Max Mustermann"))
        )
        val store = store(repository)
        val effects = mutableListOf<AuthEffect>()
        val collector = collectEffects(store) { effects += it }

        store.dispatch(AuthIntent.UsernameChanged("max@hb.dhbw-stuttgart.de"))
        store.dispatch(AuthIntent.PasswordChanged("secret"))
        store.dispatch(AuthIntent.Submitted)

        assertEquals(listOf<AuthEffect>(AuthEffect.LoggedIn), effects)
        collector.cancel()
        store.close()
    }

    @Test
    fun togglingVisibility_throughTheStore_flipsIt() = runTest {
        val store = store()

        store.dispatch(AuthIntent.PasswordVisibilityToggled)

        assertTrue(store.state.value.isPasswordVisible)
        store.close()
    }

    @Test
    fun clearing_throughTheStore_resetsEverything() = runTest {
        val store = store()
        store.dispatch(AuthIntent.UsernameChanged("max@hb.dhbw-stuttgart.de"))
        store.dispatch(AuthIntent.PasswordChanged("secret"))

        store.dispatch(AuthIntent.Cleared)

        assertEquals(AuthState(), store.state.value)
        store.close()
    }
}
