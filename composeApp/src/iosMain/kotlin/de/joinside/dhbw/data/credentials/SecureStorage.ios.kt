package de.joinside.dhbw.data.credentials

// iosMain/SecureStorage.ios.kt
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SecureStorage {
    private val serviceName = "de.joinside.dhbw"

    actual fun setString(key: String, value: String) {
        val query = createQuery(key)
        SecItemDelete(query.toCFDictionary()) // Erst löschen falls vorhanden

        val addQuery = query.toMutableMap().apply {
            put(kSecValueData, value.toNSData())
            // WICHTIG: AfterFirstUnlock für Background-Zugriff!
            put(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
        }

        SecItemAdd(addQuery.toCFDictionary(), null)
    }

    actual fun getString(key: String, defaultValue: String): String {
        val query = createQuery(key).toMutableMap().apply {
            put(kSecReturnData, true)
            put(kSecMatchLimit, kSecMatchLimitOne)
        }

        val result = memScoped {
            val resultPtr = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(
                query.toCFDictionary(),
                resultPtr.ptr
            )

            if (status == errSecSuccess) {
                val data = CFBridgingRelease(resultPtr.value) as? NSData
                data?.let {
                    NSString.create(it, NSUTF8StringEncoding)?.toString()
                }
            } else {
                null
            }
        }

        return result ?: defaultValue
    }

    actual fun remove(key: String) {
        val query = createQuery(key)
        SecItemDelete(query.toCFDictionary())
    }

    actual fun clear() {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName
        )
        SecItemDelete(query.toCFDictionary())
    }

    private fun createQuery(key: String) = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to serviceName,
        kSecAttrAccount to key
    )

    private fun String.toNSData(): NSData {
        return NSString.create(string = this)
            .dataUsingEncoding(NSUTF8StringEncoding)!!
    }

    private fun Map<*, *>.toCFDictionary(): CFDictionaryRef? {
        return memScoped {
            val keysList = this@toCFDictionary.keys.map { CFBridgingRetain(it) }
            val valuesList = this@toCFDictionary.values.map { CFBridgingRetain(it) }

            val keysArray = allocArrayOf(keysList)
            val valuesArray = allocArrayOf(valuesList)

            CFDictionaryCreate(
                null,
                keysArray,
                valuesArray,
                this@toCFDictionary.size.toLong(),
                null,
                null
            )
        }
    }
}

