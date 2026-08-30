/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.testutil.fakes.FakeAuthRepository
import de.fampopprol.dhbwhorb.testutil.fakes.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthUseCasesTest {

    @Test
    fun loginWithCredentials_delegatesToTheRepository() = runTest {
        val auth = FakeAuthRepository(loginResult = Outcome.Ok(Session(userFullName = "Jane")))

        val result = LoginWithCredentials(auth)("jane", "secret")

        assertEquals(1, auth.loginCount)
        assertEquals("Jane", assertIs<Outcome.Ok<Session>>(result).value.userFullName)
    }

    @Test
    fun loginWithCredentials_propagatesAFailure() = runTest {
        val auth = FakeAuthRepository(loginResult = Outcome.Err(AppError.InvalidCredentials))

        val result = LoginWithCredentials(auth)("jane", "wrong")

        assertEquals(AppError.InvalidCredentials, assertIs<Outcome.Err>(result).error)
    }

    @Test
    fun restoreSession_returnsTheStoredSessionWithoutReauthenticating() = runTest {
        val session = Session(userFullName = "Stored")
        val sessionRepo = FakeSessionRepository(session = session)
        val auth = FakeAuthRepository()

        val result = RestoreSession(sessionRepo, auth)()

        assertEquals(session, assertIs<Outcome.Ok<Session>>(result).value)
        assertEquals(0, auth.loginCount, "a stored session must not trigger a network re-auth")
    }

    @Test
    fun restoreSession_withNoStoredSessionAndNoCredentials_reportsNoCredentials() = runTest {
        val sessionRepo = FakeSessionRepository(session = null, canAuthenticate = false)
        val auth = FakeAuthRepository()

        val result = RestoreSession(sessionRepo, auth)()

        assertEquals(AppError.NoCredentials, assertIs<Outcome.Err>(result).error)
    }

    @Test
    fun restoreSession_withStoredCredentials_reAuthenticates() = runTest {
        val sessionRepo = FakeSessionRepository(session = null, canAuthenticate = true)
        val restored = Session(userFullName = "Rehydrated")
        val auth = FakeAuthRepository(loginResult = Outcome.Ok(restored))

        val result = RestoreSession(sessionRepo, auth)()

        assertEquals(restored, assertIs<Outcome.Ok<Session>>(result).value)
    }
}
