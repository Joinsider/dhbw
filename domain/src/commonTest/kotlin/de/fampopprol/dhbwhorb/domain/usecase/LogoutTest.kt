/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.domain.repository.AuthRepository
import de.fampopprol.dhbwhorb.domain.session.SessionDataCleaner
import de.fampopprol.dhbwhorb.testutil.fakes.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LogoutTest {

    @Test
    fun logout_clearsTheRepositoryAndEverythingOutsideIt() = runTest {
        val auth = FakeAuthRepository()
        var cleared = 0

        val result = Logout(auth, SessionDataCleaner { cleared++ })()

        assertIs<Outcome.Ok<Unit>>(result)
        assertEquals(1, auth.logoutCount)
        assertEquals(1, cleared, "the reminders, widget and cached files must be cleared too")
    }

    @Test
    fun theCleanerRunsAfterTheRepository_soItSeesAnEmptyDatabase() = runTest {
        val order = mutableListOf<String>()
        val auth = object : AuthRepository {
            override suspend fun login(username: String, password: String) =
                Outcome.Ok(Session(userFullName = null))

            override suspend fun reAuthenticate() = login("", "")

            override suspend fun logout(): Outcome<Unit> {
                order += "repository"
                return Outcome.Ok(Unit)
            }
        }

        Logout(auth, SessionDataCleaner { order += "cleaner" })()

        assertEquals(listOf("repository", "cleaner"), order)
    }

    @Test
    fun aCleanerThatThrows_stillLogsOutAndReportsTheFailure() = runTest {
        val auth = FakeAuthRepository()

        val result = Logout(auth, SessionDataCleaner { throw IllegalStateException("no widget") })()

        assertEquals(1, auth.logoutCount, "the session goes even if the cleanup does not")
        val error = assertIs<Outcome.Err>(result).error
        assertIs<AppError.Storage>(error)
    }

    @Test
    fun aFailedRepository_isReportedEvenWhenTheCleanerSucceeds() = runTest {
        val auth = FakeAuthRepository(logoutResult = Outcome.Err(AppError.Storage("db locked")))
        var cleared = false

        val result = Logout(auth, SessionDataCleaner { cleared = true })()

        assertTrue(cleared, "a failed cache wipe must not skip the rest of the cleanup")
        assertEquals(AppError.Storage("db locked"), assertIs<Outcome.Err>(result).error)
    }

    @Test
    fun withoutACleaner_logoutIsStillTheRepository() = runTest {
        val auth = FakeAuthRepository()

        assertIs<Outcome.Ok<Unit>>(Logout(auth)())
        assertEquals(1, auth.logoutCount)
    }
}
