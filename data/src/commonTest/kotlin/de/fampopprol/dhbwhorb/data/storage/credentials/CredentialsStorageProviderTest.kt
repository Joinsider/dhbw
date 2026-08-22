package de.fampopprol.dhbwhorb.data.storage.credentials

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialsStorageProviderTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var credentialsProvider: CredentialsStorageProvider

    @BeforeTest
    fun setup() {
        fakeSecureStorage = FakeSecureStorage()
        credentialsProvider = CredentialsStorageProvider(fakeSecureStorage)
    }

    @Test
    fun storeCredentials_storesUsernameAndPassword() {
        // Given
        val username = "test@dhbw.de"
        val password = "testpassword"

        // When
        credentialsProvider.storeCredentials(username, password)

        // Then
        assertEquals(username, credentialsProvider.getUsername())
        assertEquals(password, credentialsProvider.getPassword())
    }

    @Test
    fun hasStoredCredentials_returnsTrueWhenBothStored() {
        // Given
        credentialsProvider.storeCredentials("test@dhbw.de", "testpassword")

        // When
        val hasCredentials = credentialsProvider.hasStoredCredentials()

        // Then
        assertTrue(hasCredentials)
    }

    @Test
    fun hasStoredCredentials_returnsFalseWhenNoCredentials() {
        // When
        val hasCredentials = credentialsProvider.hasStoredCredentials()

        // Then
        assertFalse(hasCredentials)
    }

    @Test
    fun hasStoredCredentials_returnsFalseWhenOnlyUsernameStored() {
        // Given
        fakeSecureStorage.setString("dualis_username", "test@dhbw.de")

        // When
        val hasCredentials = credentialsProvider.hasStoredCredentials()

        // Then
        assertFalse(hasCredentials)
    }

    @Test
    fun hasStoredCredentials_returnsFalseWhenOnlyPasswordStored() {
        // Given
        fakeSecureStorage.setString("dualis_password", "testpassword")

        // When
        val hasCredentials = credentialsProvider.hasStoredCredentials()

        // Then
        assertFalse(hasCredentials)
    }

    @Test
    fun getUsername_returnsEmptyStringWhenNotStored() {
        // When
        val username = credentialsProvider.getUsername()

        // Then
        assertEquals("", username)
    }

    @Test
    fun getPassword_returnsEmptyStringWhenNotStored() {
        // When
        val password = credentialsProvider.getPassword()

        // Then
        assertEquals("", password)
    }

    @Test
    fun getCredentials_returnsPairWhenStored() {
        // Given
        val username = "test@dhbw.de"
        val password = "testpassword"
        credentialsProvider.storeCredentials(username, password)

        // When
        val credentials = credentialsProvider.getCredentials()

        // Then
        assertNotNull(credentials)
        assertEquals(username, credentials.first)
        assertEquals(password, credentials.second)
    }

    @Test
    fun getCredentials_returnsNullWhenNotStored() {
        // When
        val credentials = credentialsProvider.getCredentials()

        // Then
        assertNull(credentials)
    }

    @Test
    fun clearCredentials_removesStoredCredentials() {
        // Given
        credentialsProvider.storeCredentials("test@dhbw.de", "testpassword")
        assertTrue(credentialsProvider.hasStoredCredentials())

        // When
        credentialsProvider.clearCredentials()

        // Then
        assertFalse(credentialsProvider.hasStoredCredentials())
        assertEquals("", credentialsProvider.getUsername())
        assertEquals("", credentialsProvider.getPassword())
    }

    @Test
    fun clearAllData_removesAllData() {
        // Given
        credentialsProvider.storeCredentials("test@dhbw.de", "testpassword")
        fakeSecureStorage.setString("other_key", "other_value")

        // When
        credentialsProvider.clearAllData()

        // Then
        assertFalse(credentialsProvider.hasStoredCredentials())
        assertEquals("", fakeSecureStorage.getString("other_key"))
    }

    @Test
    fun storeCredentials_overwritesPreviousCredentials() {
        // Given
        credentialsProvider.storeCredentials("old@dhbw.de", "oldpassword")

        // When
        val newUsername = "new@dhbw.de"
        val newPassword = "newpassword"
        credentialsProvider.storeCredentials(newUsername, newPassword)

        // Then
        assertEquals(newUsername, credentialsProvider.getUsername())
        assertEquals(newPassword, credentialsProvider.getPassword())
    }

    @Test
    fun storeCredentials_withEmptyStrings_storesEmptyValues() {
        // Given
        val username = ""
        val password = ""

        // When
        credentialsProvider.storeCredentials(username, password)

        // Then
        assertEquals(username, credentialsProvider.getUsername())
        assertEquals(password, credentialsProvider.getPassword())
        assertFalse(credentialsProvider.hasStoredCredentials())
    }

    @Test
    fun storeCredentials_withSpecialCharacters_storesCorrectly() {
        // Given
        val username = "test+special@dhbw.de"
        val password = "p@ssw0rd!#$%"

        // When
        credentialsProvider.storeCredentials(username, password)

        // Then
        assertEquals(username, credentialsProvider.getUsername())
        assertEquals(password, credentialsProvider.getPassword())
    }
}

