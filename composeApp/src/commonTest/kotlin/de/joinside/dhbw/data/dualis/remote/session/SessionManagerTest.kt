package de.joinside.dhbw.data.dualis.remote.session

import de.joinside.dhbw.data.dualis.remote.models.AuthData
import de.joinside.dhbw.data.storage.credentials.FakeSecureStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    private fun createSessionManager(): Pair<SessionManager, FakeSecureStorage> {
        val fakeStorage = FakeSecureStorage()
        val sessionManager = SessionManager(fakeStorage)
        return Pair(sessionManager, fakeStorage)
    }

    @Test
    fun `storeCredentials stores username and password`() {
        // Given
        val (sessionManager, _) = createSessionManager()
        val username = "test@dhbw.de"
        val password = "testpass123"

        // When
        sessionManager.storeCredentials(username, password)

        // Then
        val credentials = sessionManager.getStoredCredentials()
        assertNotNull(credentials)
        assertEquals(username, credentials.first)
        assertEquals(password, credentials.second)
    }

    @Test
    fun `getStoredCredentials returns null when no credentials stored`() {
        // Given
        val (sessionManager, _) = createSessionManager()

        // When
        val credentials = sessionManager.getStoredCredentials()

        // Then
        assertNull(credentials)
    }

    @Test
    fun `hasStoredCredentials returns true when credentials exist`() {
        // Given
        val (sessionManager, _) = createSessionManager()
        sessionManager.storeCredentials("test@dhbw.de", "password")

        // When
        val hasCredentials = sessionManager.hasStoredCredentials()

        // Then
        assertTrue(hasCredentials)
    }

    @Test
    fun `hasStoredCredentials returns false when no credentials exist`() {
        // Given
        val (sessionManager, _) = createSessionManager()

        // When
        val hasCredentials = sessionManager.hasStoredCredentials()

        // Then
        assertFalse(hasCredentials)
    }

    @Test
    fun `storeAuthData stores session ID and auth token`() {
        // Given
        val (sessionManager, _) = createSessionManager()
        val authData = AuthData(
            sessionId = "JSESSIONID123456",
            authToken = "987654321098765"
        )

        // When
        sessionManager.storeAuthData(authData)

        // Then
        val storedAuthData = sessionManager.getAuthData()
        assertNotNull(storedAuthData)
        assertEquals(authData.sessionId, storedAuthData.sessionId)
        assertEquals(authData.authToken, storedAuthData.authToken)
    }

    @Test
    fun `getAuthData returns null when no auth data stored`() {
        // Given
        val (sessionManager, _) = createSessionManager()

        // When
        val authData = sessionManager.getAuthData()

        // Then
        assertNull(authData)
    }

    @Test
    fun `isAuthenticated returns true when auth data exists`() {
        // Given
        val (sessionManager, _) = createSessionManager()
        sessionManager.storeAuthData(AuthData("session123", "token456"))

        // When
        val isAuth = sessionManager.isAuthenticated()

        // Then
        assertTrue(isAuth)
    }

    @Test
    fun `isAuthenticated returns true when demo mode is active`() {
        // Given
        val (sessionManager, _) = createSessionManager()
        sessionManager.setDemoMode(true)

        // When
        val isAuth = sessionManager.isAuthenticated()

        // Then
        assertTrue(isAuth)
    }

    @Test
    fun `isAuthenticated returns false when no auth data and no demo mode`() {
        // Given
        val (sessionManager, _) = createSessionManager()

        // When
        val isAuth = sessionManager.isAuthenticated()

        // Then
        assertFalse(isAuth)
    }

    @Test
    fun `isDemoUser returns true for demo credentials`() {
        // Given
        val (sessionManager, _) = createSessionManager()

        // When
        val isDemoUser = sessionManager.isDemoUser(
            SessionManager.DEMO_EMAIL,
            SessionManager.DEMO_PASSWORD
        )

        // Then
        assertTrue(isDemoUser)
    }

    @Test
    fun `isDemoUser returns false for non-demo credentials`() {
        // Given
        val (sessionManager, _) = createSessionManager()

        // When
        val isDemoUser = sessionManager.isDemoUser("test@dhbw.de", "password")

        // Then
        assertFalse(isDemoUser)
    }

    @Test
    fun `setDemoMode stores demo mode state`() {
        // Given
        val (sessionManager, _) = createSessionManager()

        // When
        sessionManager.setDemoMode(true)

        // Then
        assertTrue(sessionManager.isDemoMode())

        // When
        sessionManager.setDemoMode(false)

        // Then
        assertFalse(sessionManager.isDemoMode())
    }

    @Test
    fun `logout clears all session data`() {
        // Given
        val (sessionManager, _) = createSessionManager()
        sessionManager.storeCredentials("test@dhbw.de", "password")
        sessionManager.storeAuthData(AuthData("session123", "token456"))
        sessionManager.setDemoMode(true)

        // When
        sessionManager.logout()

        // Then
        assertNull(sessionManager.getStoredCredentials())
        assertNull(sessionManager.getAuthData())
        assertFalse(sessionManager.isDemoMode())
        assertFalse(sessionManager.isAuthenticated())
    }

    @Test
    fun `clearAuthData removes only auth data keeps credentials`() {
        // Given
        val (sessionManager, _) = createSessionManager()
        sessionManager.storeCredentials("test@dhbw.de", "password")
        sessionManager.storeAuthData(AuthData("session123", "token456"))

        // When
        sessionManager.clearAuthData()

        // Then
        assertNull(sessionManager.getAuthData())
        assertNotNull(sessionManager.getStoredCredentials())
    }

    @Test
    fun `setReAuthenticating changes re-authentication state`() {
        // Given
        val (sessionManager, _) = createSessionManager()

        // When
        sessionManager.setReAuthenticating(true)

        // Then
        assertTrue(sessionManager.isReAuthenticating())

        // When
        sessionManager.setReAuthenticating(false)

        // Then
        assertFalse(sessionManager.isReAuthenticating())
    }

    @Test
    fun `getAuthData returns cached data if available`() {
        // Given
        val (sessionManager, _) = createSessionManager()
        val authData = AuthData("session123", "token456")
        sessionManager.storeAuthData(authData)

        // When - call multiple times
        val firstCall = sessionManager.getAuthData()
        val secondCall = sessionManager.getAuthData()

        // Then - should return same data
        assertEquals(firstCall?.sessionId, secondCall?.sessionId)
        assertEquals(firstCall?.authToken, secondCall?.authToken)
    }
}

