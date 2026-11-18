/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.credentials

import com.github.javakeyring.PasswordAccessException
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * Covers [DesktopSecureStorage] against a map-backed [KeyringAccess], so the developer's real
 * keyring is never touched. The Preferences fallback runs on a throwaway node that is removed
 * after each test.
 */
class DesktopSecureStorageTest {

    private val keyring = FakeKeyringAccess()
    private val clock = TestTimeSource()
    private lateinit var node: Preferences

    @BeforeTest
    fun setUp() {
        node = Preferences.userRoot().node("dhbw-test-${UUID.randomUUID()}")
    }

    @AfterTest
    fun tearDown() {
        node.removeNode()
    }

    private fun storage(): DesktopSecureStorage =
        DesktopSecureStorage(keyring = keyring, preferences = { node }, timeSource = clock)

    // ─── Round trip and cache ─────────────────────────────────────────────────────────────────

    @Test
    fun setGetRemove_roundTrip() {
        val storage = storage()

        storage.setString("k", "v")
        assertEquals("v", storage.getString("k", "default"))

        storage.remove("k")
        assertEquals("default", storage.getString("k", "default"))
    }

    @Test
    fun repeatedRead_isServedFromTheCache() {
        keyring.entries[BUNDLE] = bundle("k" to "v")
        val storage = storage()

        assertEquals("v", storage.getString("k", "default"))
        val readsAfterFirst = keyring.reads.size

        assertEquals("v", storage.getString("k", "default"))
        assertEquals("default", storage.getString("missing", "default"))
        assertEquals(readsAfterFirst, keyring.reads.size)
    }

    @Test
    fun emptyValue_behavesLikeRemove() {
        val storage = storage()
        storage.setString("k", "v")

        storage.setString("k", "")

        assertEquals("default", storage.getString("k", "default"))
        assertFalse(storage.isLocked)
        assertEquals(listOf(BUNDLE, BUNDLE), keyring.writes)
    }

    @Test
    fun emptyValue_forAnAbsentKey_writesNothing() {
        val storage = storage()
        storage.setString("other", "x")
        val writesBefore = keyring.writes.size

        storage.setString("absent", "")

        assertEquals(writesBefore, keyring.writes.size)
    }

    // ─── Fresh install and corrupt bundle ─────────────────────────────────────────────────────

    @Test
    fun freshInstall_withNothingStored_returnsDefaultAndWritesNothing() {
        val storage = storage()

        assertEquals("default", storage.getString("k", "default"))
        assertFalse(storage.isLocked)
        assertEquals(emptyList(), keyring.writes)
        assertEquals(emptyList(), keyring.deletes)
    }

    @Test
    fun noStoredCredentialsMessage_isTreatedAsAbsentNotLocked() {
        keyring.readFailures[BUNDLE] = "No stored credentials match"
        keyring.readFailures[INDEX] = "No stored credentials match"
        val storage = storage()

        assertEquals("default", storage.getString("k", "default"))
        assertFalse(storage.isLocked)
        assertEquals(emptyList(), keyring.writes)
    }

    @Test
    fun corruptBundle_startsEmptyAndIsNotLocked() {
        keyring.entries[BUNDLE] = "{not json"
        val storage = storage()

        assertEquals("default", storage.getString("k", "default"))
        assertFalse(storage.isLocked)

        // The empty map is authoritative, so the next write replaces the corrupt bundle.
        storage.setString("k", "v")
        assertEquals(mapOf("k" to "v"), decodeBundle(keyring.entries[BUNDLE]))
    }

    // ─── Legacy migration ─────────────────────────────────────────────────────────────────────

    @Test
    fun legacyLayout_isMovedIntoOneBundleAndOldEntriesAreDeleted() {
        keyring.entries[INDEX] = "a,b"
        keyring.entries["a"] = "1"
        keyring.entries["b"] = "2"
        val storage = storage()

        assertEquals("1", storage.getString("a", ""))
        assertEquals("2", storage.getString("b", ""))
        assertEquals(mapOf("a" to "1", "b" to "2"), decodeBundle(keyring.entries[BUNDLE]))
        assertNull(keyring.entries["a"])
        assertNull(keyring.entries["b"])
        assertNull(keyring.entries[INDEX])
        assertEquals(listOf("a", "b", INDEX), keyring.deletes)
    }

    @Test
    fun legacyLayout_whenTheBundleCannotBeWritten_keepsTheOldEntries() {
        keyring.entries[INDEX] = "a,b"
        keyring.entries["a"] = "1"
        keyring.entries["b"] = "2"
        keyring.writeFailure = "write refused"
        val storage = storage()

        assertEquals("1", storage.getString("a", ""))
        assertEquals("2", storage.getString("b", ""))
        assertFalse(storage.isLocked)
        assertEquals("1", keyring.entries["a"])
        assertEquals("2", keyring.entries["b"])
        assertEquals("a,b", keyring.entries[INDEX])
        assertNull(keyring.entries[BUNDLE])
        assertEquals(emptyList(), keyring.deletes)
    }

    @Test
    fun legacyCleanup_whichFailsToDelete_stillMigrates() {
        keyring.entries[INDEX] = "a"
        keyring.entries["a"] = "1"
        keyring.deleteFailure = "delete refused"
        val storage = storage()

        assertEquals("1", storage.getString("a", ""))
        assertEquals(mapOf("a" to "1"), decodeBundle(keyring.entries[BUNDLE]))
        assertEquals(listOf("a", INDEX), keyring.deletes)
    }

    // ─── Locked keyring ───────────────────────────────────────────────────────────────────────

    @Test
    fun lockedKeyring_isReportedNotCachedAndRetriedAfterTheWindow() {
        keyring.entries[BUNDLE] = bundle("k" to "v")
        keyring.readFailures[BUNDLE] = "Secret Service is locked"
        val storage = storage()

        assertEquals("default", storage.getString("k", "default"))
        assertTrue(storage.isLocked)
        assertEquals(1, keyring.reads.size)

        // Inside the 30 s window the keyring is not asked again.
        clock += 10.seconds
        assertEquals("default", storage.getString("k", "default"))
        assertEquals(1, keyring.reads.size)

        // After the window it is asked again, and still locked.
        clock += 21.seconds
        assertEquals("default", storage.getString("k", "default"))
        assertEquals(2, keyring.reads.size)
        assertTrue(storage.isLocked)

        // Once the keyring answers, the stored values come back and the state clears.
        keyring.readFailures.clear()
        clock += 31.seconds
        assertEquals("v", storage.getString("k", "default"))
        assertFalse(storage.isLocked)
    }

    @Test
    fun writesAreRefusedWhileLocked_andTheBundleSurvivesUnlock() {
        val original = bundle("k" to "v")
        keyring.entries[BUNDLE] = original
        keyring.readFailures[BUNDLE] = "locked"
        val storage = storage()

        storage.setString("x", "y")
        storage.remove("k")
        assertTrue(storage.isLocked)
        assertEquals(emptyList(), keyring.writes)
        assertEquals(emptyList(), keyring.deletes)

        // Still locked after the window: the retry fails and the write is refused again.
        clock += 31.seconds
        storage.setString("x", "y")
        storage.remove("k")
        assertTrue(storage.isLocked)
        assertEquals(emptyList(), keyring.writes)

        keyring.readFailures.clear()
        clock += 31.seconds
        assertEquals("v", storage.getString("k", "default"))
        assertEquals(original, keyring.entries[BUNDLE])
        assertEquals(emptyList(), keyring.writes)
    }

    @Test
    fun legacyIndexUnreadable_isLocked_andLeavesTheOldEntriesAlone() {
        keyring.readFailures[INDEX] = "locked"
        keyring.entries["a"] = "1"
        val storage = storage()

        assertEquals("default", storage.getString("a", "default"))
        assertTrue(storage.isLocked)
        assertEquals("1", keyring.entries["a"])
        assertEquals(emptyList(), keyring.writes)
        assertEquals(emptyList(), keyring.deletes)
    }

    @Test
    fun legacyEntryUnreadable_isLocked_andLeavesEveryOldEntryInPlace() {
        keyring.entries[INDEX] = "a,b"
        keyring.entries["a"] = "1"
        keyring.entries["b"] = "2"
        keyring.readFailures["b"] = "locked"
        val storage = storage()

        assertEquals("default", storage.getString("a", "default"))
        assertTrue(storage.isLocked)
        assertEquals("1", keyring.entries["a"])
        assertEquals("2", keyring.entries["b"])
        assertEquals("a,b", keyring.entries[INDEX])
        assertNull(keyring.entries[BUNDLE])
        assertEquals(emptyList(), keyring.writes)
        assertEquals(emptyList(), keyring.deletes)
    }

    @Test
    fun clearWhileLocked_deletesTheBundleAndResetsTheLockedState() {
        keyring.entries[BUNDLE] = bundle("k" to "v")
        keyring.readFailures[BUNDLE] = "locked"
        val storage = storage()
        assertEquals("default", storage.getString("k", "default"))
        assertTrue(storage.isLocked)

        storage.clear()

        assertFalse(storage.isLocked)
        assertNull(keyring.entries[BUNDLE])
        val readsAfterClear = keyring.reads.size
        assertEquals("default", storage.getString("k", "default"))
        assertEquals(readsAfterClear, keyring.reads.size)
    }

    // ─── Preferences fallback (no keyring backend) ────────────────────────────────────────────

    @Test
    fun withoutKeyring_valuesLiveInPreferences() {
        val storage = DesktopSecureStorage(keyring = null, preferences = { node }, timeSource = clock)

        storage.setString("k", "v")
        assertEquals("v", storage.getString("k", "default"))
        assertEquals("v", node.get("k", null))
        assertEquals("default", storage.getString("other", "default"))
        assertFalse(storage.isLocked)

        storage.remove("k")
        assertEquals("default", storage.getString("k", "default"))
        assertNull(node.get("k", null))
    }

    @Test
    fun withoutKeyring_clearRemovesEverythingInTheNode() {
        val storage = DesktopSecureStorage(keyring = null, preferences = { node }, timeSource = clock)
        storage.setString("a", "1")
        storage.setString("b", "2")

        storage.clear()

        assertEquals(0, node.keys().size)
        assertEquals("default", storage.getString("a", "default"))
        assertFalse(storage.isLocked)
    }

    // ─── Delete failures are swallowed ────────────────────────────────────────────────────────

    @Test
    fun clear_whenTheBundleCannotBeDeleted_doesNotThrow() {
        keyring.entries[BUNDLE] = bundle("k" to "v")
        keyring.deleteFailure = "delete refused"
        val storage = storage()
        assertEquals("v", storage.getString("k", "default"))

        storage.clear()

        assertEquals(listOf(BUNDLE), keyring.deletes)
        assertEquals("default", storage.getString("k", "default"))
    }

    @Test
    fun remove_whenTheBundleCannotBeWritten_doesNotThrow() {
        keyring.entries[BUNDLE] = bundle("k" to "v")
        val storage = storage()
        assertEquals("v", storage.getString("k", "default"))
        keyring.writeFailure = "write refused"

        storage.remove("k")

        assertEquals("default", storage.getString("k", "default"))
    }

    // ─── Test doubles ─────────────────────────────────────────────────────────────────────────

    private fun bundle(vararg pairs: Pair<String, String>): String =
        Json.encodeToString<Map<String, String>>(mapOf(*pairs))

    private fun decodeBundle(text: String?): Map<String, String> =
        Json.decodeFromString<Map<String, String>>(requireNotNull(text))

    private companion object {
        const val BUNDLE = "credentials"
        const val INDEX = "_stored_keys"
    }
}

/**
 * Map-backed keyring. Every call is recorded by account; a read of an account in [readFailures]
 * throws until it is removed, and [writeFailure] / [deleteFailure] make writes and deletes throw.
 */
private class FakeKeyringAccess : KeyringAccess {
    val entries = mutableMapOf<String, String>()
    val readFailures = mutableMapOf<String, String>()
    var writeFailure: String? = null
    var deleteFailure: String? = null

    val reads = mutableListOf<String>()
    val writes = mutableListOf<String>()
    val deletes = mutableListOf<String>()

    override fun getPassword(service: String, account: String): String? {
        reads += account
        readFailures[account]?.let { throw PasswordAccessException(it) }
        return entries[account]
    }

    override fun setPassword(service: String, account: String, password: String) {
        writes += account
        writeFailure?.let { throw PasswordAccessException(it) }
        entries[account] = password
    }

    override fun deletePassword(service: String, account: String) {
        deletes += account
        deleteFailure?.let { throw PasswordAccessException(it) }
        entries.remove(account)
    }
}
