package de.fampopprol.dhbwhorb.data.storage.credentials

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
 * Accessibility is left at the Security framework default (`WhenUnlocked`) on purpose: changing it
 * would only affect newly written items, but would still be a change to what iOS users get.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
class AppleSecureStorage : SecureStorageInterface {
    private val serviceName = "DualisApp"
    private val keysKey = "_stored_keys"

    // Fallback storage for when Keychain is unavailable (e.g., in tests)
    private val fallbackStorage = mutableMapOf<String, String>()
    private var useKeychainFallback = false

    override fun setString(key: String, value: String) {
        if (useKeychainFallback) {
            fallbackStorage[key] = value
            return
        }

        // First, try to delete any existing value
        deleteFromKeychain(key)

        // Now add the new value
        val valueData = value.toNSData()
        val query = createKeychainQuery(key, includeData = true, data = valueData)

        val status = SecItemAdd(query, null)
        if (status == errSecNotAvailable) {
            // Keychain not available, switch to fallback
            useKeychainFallback = true
            fallbackStorage[key] = value
            return
        } else if (status != errSecSuccess) {
            println("Failed to save to keychain with status: $status")
        }

        addKeyToTracking(key)
    }

    override fun getString(key: String, defaultValue: String): String {
        if (useKeychainFallback) {
            return fallbackStorage[key] ?: defaultValue
        }

        val query = createKeychainQuery(key, returnData = true)

        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)

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

        deleteFromKeychain(key)
        removeKeyFromTracking(key)
    }

    override fun clear() {
        if (useKeychainFallback) {
            fallbackStorage.clear()
            return
        }

        val keys = getTrackedKeys()
        keys.forEach { key ->
            deleteFromKeychain(key)
        }
        deleteFromKeychain(keysKey)
    }

    private fun deleteFromKeychain(key: String) {
        val query = createKeychainQuery(key)
        SecItemDelete(query)
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
        SecItemAdd(query, null)
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
            SecItemAdd(query, null)
        }
    }

    private fun String.toNSData(): NSData {
        return NSString.create(this)?.dataUsingEncoding(NSUTF8StringEncoding)!!
    }

    private fun NSData.toKotlinString(): String {
        return NSString.create(this, NSUTF8StringEncoding)?.toString() ?: ""
    }
}

