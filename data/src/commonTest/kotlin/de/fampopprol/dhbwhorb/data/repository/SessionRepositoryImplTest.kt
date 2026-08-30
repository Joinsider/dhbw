/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRepositoryImplTest {

    private fun repository(): Pair<SessionRepositoryImpl, SessionManager> {
        val sessionManager = SessionManager(FakeSecureStorage())
        return SessionRepositoryImpl(sessionManager) to sessionManager
    }

    @Test
    fun currentSession_inDemoMode_isAnonymousAndMarkedDemo() {
        val (repository, sessionManager) = repository()
        sessionManager.setDemoMode(true)

        val session = repository.currentSession()

        assertEquals(null, session?.userFullName)
        assertTrue(session?.isDemo == true)
    }

    @Test
    fun currentSession_withoutAuthDataOrDemoMode_isNull() {
        val (repository, _) = repository()

        assertNull(repository.currentSession())
    }

    @Test
    fun currentSession_withStoredAuthData_carriesTheFullName() {
        val (repository, sessionManager) = repository()
        sessionManager.storeAuthData(AuthData(sessionId = "s1", authToken = "t1", userFullName = "Max Mustermann"))

        val session = repository.currentSession()

        assertEquals("Max Mustermann", session?.userFullName)
        assertFalse(session?.isDemo == true)
    }

    @Test
    fun isLoggedIn_reflectsSessionManagerAuthentication() {
        val (repository, sessionManager) = repository()
        assertFalse(repository.isLoggedIn())

        sessionManager.storeAuthData(AuthData(sessionId = "s1", authToken = "t1"))
        assertTrue(repository.isLoggedIn())
    }

    @Test
    fun canAuthenticate_isTrueWhenOnlyCredentialsAreStored() {
        val (repository, sessionManager) = repository()
        assertFalse(repository.canAuthenticate())

        sessionManager.storeCredentials("test@dhbw.de", "hunter2")
        assertTrue(repository.canAuthenticate(), "stored credentials alone are enough to retry a login")
    }

    @Test
    fun canAuthenticate_isTrueWhenAlreadyAuthenticated() {
        val (repository, sessionManager) = repository()
        sessionManager.storeAuthData(AuthData(sessionId = "s1", authToken = "t1"))

        assertTrue(repository.canAuthenticate())
    }

    @Test
    fun canAuthenticate_isFalseWithNeitherCredentialsNorSession() {
        val (repository, _) = repository()

        assertFalse(repository.canAuthenticate())
    }

    @Test
    fun isDemoMode_reflectsSessionManager() {
        val (repository, sessionManager) = repository()
        assertFalse(repository.isDemoMode())

        sessionManager.setDemoMode(true)
        assertTrue(repository.isDemoMode())
    }
}
