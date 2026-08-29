package de.fampopprol.dhbwhorb.data.storage.settings

import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import io.github.aakira.napier.Napier

private const val TAG = "SettingsStorage"

/**
 * The settings storage the app uses, plus a one-time move out of secure storage.
 *
 * Installations from before the split hold these values in the secure store. Reading them there
 * once and writing them here keeps a deliberate user choice — a theme is not a cache — at the price
 * of one last round of Keychain dialogs on the first desktop start after the update. Afterwards the
 * legacy entry is deleted and nothing touches the secure store for settings again.
 *
 * The migration is read-through rather than a startup sweep: a key nobody asks for is a key nobody
 * pays a dialog for.
 */
class SettingsStorage(
    private val settings: PlatformSettings,
    private val legacy: SecureStorageInterface,
) {

    fun getString(key: String, defaultValue: String): String =
        settings.getStringOrNull(key) ?: migrateFromLegacy(key) ?: defaultValue

    fun setString(key: String, value: String) {
        settings.setString(key, value)
    }

    fun remove(key: String) {
        settings.remove(key)
    }

    private fun migrateFromLegacy(key: String): String? {
        // The legacy interface cannot say "absent", so an empty value counts as absent — which is
        // how every caller already treated it.
        val legacyValue = legacy.getString(key, "").takeIf { it.isNotEmpty() } ?: return null
        Napier.i("Moving setting '$key' out of secure storage", tag = TAG)
        settings.setString(key, legacyValue)
        legacy.remove(key)
        return legacyValue
    }
}
