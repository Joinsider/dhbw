package de.fampopprol.dhbwhorb.data.storage.settings

import java.util.prefs.Preferences

/**
 * `java.util.prefs`, which asks nobody for permission — the whole point of moving settings out of
 * the Keychain.
 */
class DesktopPlatformSettings : PlatformSettings {
    private val preferences: Preferences by lazy {
        Preferences.userNodeForPackage(DesktopPlatformSettings::class.java)
    }

    override fun getStringOrNull(key: String): String? = preferences.get(key, null)

    override fun setString(key: String, value: String) = preferences.put(key, value)

    override fun remove(key: String) = preferences.remove(key)
}
