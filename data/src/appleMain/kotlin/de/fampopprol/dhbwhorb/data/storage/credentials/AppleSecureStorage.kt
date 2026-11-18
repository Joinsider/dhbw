// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.data.storage.credentials

import io.github.aakira.napier.Napier
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import platform.CoreFoundation.*

/**
 * Keychain-backed credential storage for every Apple platform (iOS, the iOS widget extension and
 * the native macOS app).
 *
 * Every query sets `kSecUseDataProtectionKeychain`. On iOS that is the only keychain there is, so
 * the flag changes nothing and the existing items are read exactly as before. On macOS it moves the
 * items out of the legacy file-based login keychain. There, each item carries an access list bound
 * to the code signature of the app that created it, so every rebuild or re-signing triggered a
 * password prompt per item (the "eight prompts at launch" problem). The data protection keychain
 * uses the app's keychain-access-groups entitlement instead and does not prompt.
 *
 * Consequence on macOS: items written by earlier builds in the legacy keychain are **not** read and
 * **not** deleted. Reading them is what prompts, and deleting can too. Users log in once more.
 *
 * The data protection keychain needs the `keychain-access-groups` entitlement at runtime. Without it
 * the calls fail with `errSecMissingEntitlement` (-34018), and this class then reports "no value",
 * so an unsigned or ad-hoc build cannot log in. That is the expected behaviour, not a bug.
 *
 * No access group is named in any query on purpose. The keychain files an item into the first
 * entry of the writing process's `keychain-access-groups` entitlement and searches the same list
 * on the way back, so app and extension see the same items as long as both entitlement files
 * start with the same group — which they do since P8. Naming the group in code would mean
 * knowing the team prefix that completes it, and that only exists at signing time.
 *
 * Item layout (unchanged, iOS users stay logged in): generic password, service `DualisApp`,
 * account = the key, plus an index item `_stored_keys` listing the tracked keys.
 *
 * Accessibility is `kSecAttrAccessibleAfterFirstUnlock`, not the `WhenUnlocked` default. iOS starts
 * the app while the device is still locked — prewarming, the BGTaskScheduler background check, the
 * widget extension — and with `WhenUnlocked` every read in that state fails with
 * `errSecInteractionNotAllowed` (-25308). The credentials then look absent, and the app shows the
 * login screen, which stays up after the user unlocks. `AfterFirstUnlock` keeps the items readable
 * from the first unlock after a reboot onwards, which is the state every background launch runs in.
 * Items written before this change are moved to the new accessibility by [migrateAccessibility],
 * once per process, before the first keychain access. A migration that hits the locked device is
 * retried on the next access; [isLocked] tells the rest of the app that a miss means "not yet
 * readable", not "absent".
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
class AppleSecureStorage : SecureStorageInterface {
    private companion object {
        const val TAG = "AppleSecureStorage"
    }

    private val serviceName = "DualisApp"
    private val keysKey = "_stored_keys"

    // Fallback storage for when Keychain is unavailable (e.g., in tests)
    private val fallbackStorage = mutableMapOf<String, String>()
    private var useKeychainFallback = false

    private var accessibilityMigrated = false
    private var locked = false

    override val isLocked: Boolean get() = locked

    override fun setString(key: String, value: String) {
        if (useKeychainFallback) {
            fallbackStorage[key] = value
            return
        }
        ensureAccessibilityMigrated()

        // First, try to delete any existing value
        deleteFromKeychain(key)

        // Now add the new value
        val valueData = value.toNSData()
        val query = createKeychainQuery(key, includeData = true, data = valueData)

        val status = SecItemAdd(query, null)
        recordStatus(status)
        if (status == errSecNotAvailable) {
            // Keychain not available, switch to fallback
            useKeychainFallback = true
            fallbackStorage[key] = value
            return
        } else if (status != errSecSuccess) {
            Napier.e("Failed to save '$key' to keychain with status: $status", tag = TAG)
        }

        addKeyToTracking(key)
    }

    override fun getString(key: String, defaultValue: String): String {
        if (useKeychainFallback) {
            return fallbackStorage[key] ?: defaultValue
        }
        ensureAccessibilityMigrated()

        val query = createKeychainQuery(key, returnData = true)

        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            recordStatus(status, isRead = true)

            if (status == errSecNotAvailable) {
                // Keychain not available, switch to fallback
                useKeychainFallback = true
                return fallbackStorage[key] ?: defaultValue
            }

            if (status == errSecSuccess && result.value != null) {
                val data = CFBridgingRelease(result.value) as? NSData
                return data?.toKotlinString() ?: defaultValue
            }
        }

        return defaultValue
    }

    override fun remove(key: String) {
        if (useKeychainFallback) {
            fallbackStorage.remove(key)
            return
        }
        ensureAccessibilityMigrated()

        deleteFromKeychain(key)
        removeKeyFromTracking(key)
    }

    override fun clear() {
        if (useKeychainFallback) {
            fallbackStorage.clear()
            return
        }
        ensureAccessibilityMigrated()

        val keys = getTrackedKeys()
        keys.forEach { key ->
            deleteFromKeychain(key)
        }
        deleteFromKeychain(keysKey)
    }

    /**
     * Moves every item of this service to `kSecAttrAccessibleAfterFirstUnlock`. Runs at most once
     * per process, and only after it has succeeded (or found nothing to move).
     */
    private fun ensureAccessibilityMigrated() {
        if (accessibilityMigrated) return
        migrateAccessibility()
    }

    private fun migrateAccessibility() {
        val query = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        ).apply {
            CFDictionarySetValue(this, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(this, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionarySetValue(this, kSecAttrService, CFBridgingRetain(NSString.create(serviceName)))
        }
        val attributes = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        ).apply {
            CFDictionarySetValue(this, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
        }

        val status = SecItemUpdate(query, attributes)
        recordStatus(status)
        when (status) {
            errSecSuccess, errSecItemNotFound -> {
                accessibilityMigrated = true
                Napier.d("Keychain accessibility is AfterFirstUnlock (status $status)", tag = TAG)
            }
            errSecInteractionNotAllowed ->
                // Device locked: leave the flag unset so the next keychain access tries again.
                Napier.w("Keychain accessibility migration deferred: device locked", tag = TAG)
            else -> Napier.e("Keychain accessibility migration failed with status: $status", tag = TAG)
        }
    }

    /**
     * Remembers whether the keychain refused access because the device is locked. A successful
     * read, or a read that found nothing, proves the store is readable again.
     */
    private fun recordStatus(status: Int, isRead: Boolean = false) {
        when {
            status == errSecInteractionNotAllowed -> locked = true
            isRead && (status == errSecSuccess || status == errSecItemNotFound) -> locked = false
        }
    }

    private fun deleteFromKeychain(key: String) {
        val query = createKeychainQuery(key)
        recordStatus(SecItemDelete(query))
    }

    private fun createKeychainQuery(
        key: String,
        returnData: Boolean = false,
        includeData: Boolean = false,
        data: NSData? = null
    ): CFDictionaryRef? {
        return CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        ).apply {
            CFDictionarySetValue(this, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(this, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionarySetValue(this, kSecAttrService, CFBridgingRetain(NSString.create(serviceName)))
            CFDictionarySetValue(this, kSecAttrAccount, CFBridgingRetain(NSString.create(key)))

            if (returnData) {
                CFDictionarySetValue(this, kSecReturnData, kCFBooleanTrue)
                CFDictionarySetValue(this, kSecMatchLimit, kSecMatchLimitOne)
            }

            if (includeData && data != null) {
                // Every item this class adds is readable after the first unlock, see the class KDoc.
                CFDictionarySetValue(this, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
                CFDictionarySetValue(this, kSecValueData, CFBridgingRetain(data))
            }
        }
    }

    private fun getTrackedKeys(): Set<String> {
        val keysString = getString(keysKey, "")
        return if (keysString.isEmpty()) emptySet() else keysString.split(",").toSet()
    }

    private fun addKeyToTracking(key: String) {
        if (key == keysKey) return
        val keys = getTrackedKeys().toMutableSet()
        keys.add(key)

        // Save without tracking to avoid recursion
        deleteFromKeychain(keysKey)
        val valueData = keys.joinToString(",").toNSData()
        val query = createKeychainQuery(keysKey, includeData = true, data = valueData)
        recordStatus(SecItemAdd(query, null))
    }

    private fun removeKeyFromTracking(key: String) {
        if (key == keysKey) return
        val keys = getTrackedKeys().toMutableSet()
        keys.remove(key)

        // Save without tracking to avoid recursion
        deleteFromKeychain(keysKey)
        if (keys.isNotEmpty()) {
            val valueData = keys.joinToString(",").toNSData()
            val query = createKeychainQuery(keysKey, includeData = true, data = valueData)
            recordStatus(SecItemAdd(query, null))
        }
    }

    private fun String.toNSData(): NSData {
        return NSString.create(this)?.dataUsingEncoding(NSUTF8StringEncoding)!!
    }

    private fun NSData.toKotlinString(): String {
        return NSString.create(this, NSUTF8StringEncoding)?.toString() ?: ""
    }
}
