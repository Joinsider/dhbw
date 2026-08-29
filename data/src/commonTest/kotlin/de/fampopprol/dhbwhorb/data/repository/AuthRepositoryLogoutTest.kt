/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.net.ClearableCookiesStorage
import de.fampopprol.dhbwhorb.testutil.MockAppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Logging out has to leave nothing of the account behind — not the credentials, not the session,
 * and not the cookie the session is really carried in.
 */
class AuthRepositoryLogoutTest {

    @Test
    fun logout_clearsCredentials_session_andTheSessionCookie() = runTest {
        val storage = FakeSecureStorage()
        val sessionManager = SessionManager(storage)
        val cookies = ClearableCookiesStorage()
        val client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val authService = AuthenticationService(sessionManager, client, cookies)

        val credentials = CredentialsStorageProvider(storage)
        credentials.storeCredentials("test@dhbw.de", "password")
        sessionManager.storeAuthData(AuthData(sessionId = "s", authToken = "t", cookie = "c"))
        sessionManager.setDemoMode(true)
        val dualis = Url("https://dualis.dhbw.de/")
        cookies.addCookie(dualis, Cookie(name = "JSESSIONID", value = "abc", path = "/"))

        val repository = AuthRepositoryImpl(
            authenticationService = authService,
            reAuthenticator = ReAuthenticator(sessionManager, authService),
            credentialsProvider = credentials,
            database = MockAppDatabase()
        )

        assertIs<Outcome.Ok<Unit>>(repository.logout())

        assertFalse(credentials.hasStoredCredentials())
        assertNull(sessionManager.getAuthData())
        assertFalse(sessionManager.isDemoMode(), "demo mode is part of the session too")
        assertTrue(cookies.get(dualis).isEmpty())
    }
}
