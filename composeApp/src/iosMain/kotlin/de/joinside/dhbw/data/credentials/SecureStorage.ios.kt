package de.joinside.dhbw.data.credentials

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import platform.CoreFoundation.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SecureStorage {
    private val serviceName = "DualisApp"
    private val keysKey = "_stored_keys"

    // Fallback storage for when Keychain is unavailable (e.g., in tests)
    private val fallbackStorage = mutableMapOf<String, String>()
    private var useKeychainFallback = false

    actual fun setString(key: String, value: String) {
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

    actual fun getString(key: String, defaultValue: String): String {
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

    actual fun remove(key: String) {
        if (useKeychainFallback) {
            fallbackStorage.remove(key)
            return
        }

        deleteFromKeychain(key)
        removeKeyFromTracking(key)
    }

    actual fun clear() {
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

