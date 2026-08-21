package de.fampopprol.dhbwhorb.data.storage.settings

import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.testutil.TestPlatformSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settings used to share the secure storage with credentials. On desktop that meant one macOS
 * Keychain dialog per value, because macOS checks its access list per entry — reading the colour
 * scheme asked the user for permission. These tests hold the two halves of the fix: settings go
 * somewhere else now, and the values people already chose come along once.
 */
class SettingsStorageTest {

    private class CountingSecureStorage : SecureStorageInterface {
        val values = mutableMapOf<String, String>()
        var reads = 0
            private set

        override fun setString(key: String, value: String) {
            values[key] = value
        }

        override fun getString(key: String, defaultValue: String): String {
            reads++
            return values[key] ?: defaultValue
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun clear() = values.clear()
    }

    @Test
    fun `an existing setting is moved out of secure storage on first read`() {
        val legacy = CountingSecureStorage().apply { values["theme_mode_preference"] = "DARK" }
        val settings = TestPlatformSettings()
        val storage = SettingsStorage(settings, legacy)

        assertEquals("DARK", storage.getString("theme_mode_preference", "SYSTEM"))

        assertEquals("DARK", settings.getStringOrNull("theme_mode_preference"))
        assertNull(
            legacy.values["theme_mode_preference"],
            "the old entry must go, otherwise the dialog it costs comes back every start",
        )
    }

    @Test
    fun `secure storage is read once per key and never again`() {
        val legacy = CountingSecureStorage().apply { values["theme_mode_preference"] = "DARK" }
        val storage = SettingsStorage(TestPlatformSettings(), legacy)

        repeat(5) { storage.getString("theme_mode_preference", "SYSTEM") }

        assertEquals(
            1,
            legacy.reads,
            "each read of the legacy store is a permission dialog on macOS",
        )
    }

    @Test
    fun `a fresh installation gets the default without writing anything`() {
        val legacy = CountingSecureStorage()
        val settings = TestPlatformSettings()
        val storage = SettingsStorage(settings, legacy)

        assertEquals("SYSTEM", storage.getString("theme_mode_preference", "SYSTEM"))
        assertNull(settings.getStringOrNull("theme_mode_preference"))
    }

    @Test
    fun `an empty legacy value counts as absent`() {
        // Windows Credential Manager cannot store empty strings, so the old code wrote "" to mean
        // "removed". Migrating that would resurrect a value the user cleared.
        val legacy = CountingSecureStorage().apply { values["theme_mode_preference"] = "" }
        val storage = SettingsStorage(TestPlatformSettings(), legacy)

        assertEquals("SYSTEM", storage.getString("theme_mode_preference", "SYSTEM"))
    }

    @Test
    fun `preferences write to settings, never to secure storage`() {
        val legacy = CountingSecureStorage()
        val settings = TestPlatformSettings()
        val preferences = ThemePreferences(SettingsStorage(settings, legacy))

        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setMaterialYouEnabled(false)
        preferences.setCustomColor(42L)

        assertTrue(
            legacy.values.isEmpty(),
            "a setting in the secure store is a permission dialog on desktop",
        )
        assertEquals("DARK", settings.getStringOrNull("theme_mode_preference"))
        assertEquals("false", settings.getStringOrNull("material_you_preference"))
        assertEquals("42", settings.getStringOrNull("custom_color_preference"))
    }
}
