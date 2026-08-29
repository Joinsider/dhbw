package de.fampopprol.dhbwhorb.data.storage.credentials

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import java.util.prefs.Preferences

private const val TAG = "SecureStorage"

/**
 * Credential storage backed by the platform keyring — on macOS, the Keychain.
 *
 * **Everything lives in one keyring entry.** macOS checks its access control list per *entry*, not
 * per application, so the previous layout of one entry per value meant one permission dialog per
 * value: eight on a cold start, and "Always allow" had to be granted eight separate times. One
 * entry means one dialog, and the values are held in memory afterwards so a second read within the
 * same run cannot ask again. That is also why `dualis_is_demo_mode` used to be read three times per
 * start and cost three dialogs.
 *
 * Installations from before this change are migrated on first access, using the `_stored_keys`
 * index the old layout maintained for exactly this kind of bookkeeping. That first start still
 * costs the old number of dialogs; every start after it costs one.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
class DesktopSecureStorage : SecureStorageInterface {

    private val keyring: Keyring? = try {
        val keyring = Keyring.create()
        Napier.d("Using native keyring backend: ${keyring::class.java.name}", tag = TAG)
        keyring
    } catch (e: BackendNotSupportedException) {
        Napier.w("No keyring backend available — falling back to Preferences.", tag = TAG)
        null
    }

    private val prefs: Preferences by lazy {
        // userNodeForPackage keys on the package, not the class, and this class sits in the same
        // package as the former SecureStorage — existing users keep their stored values.
        Preferences.userNodeForPackage(DesktopSecureStorage::class.java)
    }

    /** Loaded from the keyring at most once per run; `null` until the first access. */
    private var cache: MutableMap<String, String>? = null

    override fun setString(key: String, value: String) {
        if (keyring == null) {
            prefs.put(key, value)
            return
        }
        // Windows Credential Manager rejects empty strings, and an empty value has always meant
        // "absent" to every caller.
        if (value.isEmpty()) {
            remove(key)
            return
        }
        val values = load()
        values[key] = value
        persist(values)
    }

    override fun getString(key: String, defaultValue: String): String {
        if (keyring == null) return prefs.get(key, defaultValue)
        return load()[key] ?: defaultValue
    }

    override fun remove(key: String) {
        if (keyring == null) {
            prefs.remove(key)
            return
        }
        val values = load()
        if (values.remove(key) != null) persist(values)
    }

    override fun clear() {
        if (keyring == null) {
            prefs.clear()
            return
        }
        cache = mutableMapOf()
        deleteEntry(BUNDLE_ACCOUNT)
    }

    private fun load(): MutableMap<String, String> {
        cache?.let { return it }
        val keyring = requireNotNull(keyring)
        val stored = readEntry(keyring, BUNDLE_ACCOUNT)
        val values = if (stored != null) {
            try {
                Json.decodeFromString<Map<String, String>>(stored).toMutableMap()
            } catch (e: Exception) {
                // A corrupt bundle must not lock the user out of the app; they log in again.
                Napier.e("Credential bundle unreadable, starting empty", e, tag = TAG)
                mutableMapOf()
            }
        } else {
            migrateFromPerKeyEntries(keyring)
        }
        cache = values
        return values
    }

    /**
     * Moves a pre-bundle installation into the single entry and deletes the old ones.
     *
     * Returns an empty map for a fresh installation, which then costs no dialog at all until
     * something is stored.
     */
    private fun migrateFromPerKeyEntries(keyring: Keyring): MutableMap<String, String> {
        val trackedKeys = readEntry(keyring, LEGACY_KEY_INDEX)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: return mutableMapOf()

        Napier.i("Migrating ${trackedKeys.size} keyring entries into one bundle", tag = TAG)
        val values = mutableMapOf<String, String>()
        trackedKeys.forEach { key ->
            readEntry(keyring, key)?.let { values[key] = it }
        }
        // Delete only once the bundle is verifiably written. If persisting fails, the old entries
        // stay and the migration runs again next start — losing them would log the user out and
        // throw away credentials this app cannot recreate.
        if (values.isNotEmpty() && !persist(values)) {
            Napier.e("Keeping the old keyring entries: the bundle could not be written", tag = TAG)
            return values
        }
        trackedKeys.forEach { deleteEntry(it) }
        deleteEntry(LEGACY_KEY_INDEX)
        return values
    }

    /** @return whether the bundle is now stored, so callers can avoid deleting what it replaces. */
    private fun persist(values: Map<String, String>): Boolean {
        val keyring = keyring ?: return false
        return try {
            keyring.setPassword(SERVICE_NAME, BUNDLE_ACCOUNT, Json.encodeToString(values))
            true
        } catch (e: PasswordAccessException) {
            Napier.e("Could not write the credential bundle", e, tag = TAG)
            false
        }
    }

    private fun readEntry(keyring: Keyring, account: String): String? =
        try {
            keyring.getPassword(SERVICE_NAME, account)?.takeIf { it.isNotEmpty() }
        } catch (e: PasswordAccessException) {
            // "No stored credentials match" is the normal answer for a fresh installation.
            if (e.message?.contains("No stored credentials match", ignoreCase = true) != true) {
                Napier.e("Could not read keyring entry '$account'", e, tag = TAG)
            }
            null
        }

    private fun deleteEntry(account: String) {
        try {
            keyring?.deletePassword(SERVICE_NAME, account)
        } catch (e: PasswordAccessException) {
            // Already gone is the outcome we wanted.
            Napier.d("Nothing to delete for keyring entry '$account'", tag = TAG)
        }
    }

    private companion object {
        const val SERVICE_NAME = "DualisApp"

        /** The single entry everything is stored in. */
        const val BUNDLE_ACCOUNT = "credentials"

        /** Index of the pre-bundle layout, read once during migration and then deleted. */
        const val LEGACY_KEY_INDEX = "_stored_keys"
    }
}
