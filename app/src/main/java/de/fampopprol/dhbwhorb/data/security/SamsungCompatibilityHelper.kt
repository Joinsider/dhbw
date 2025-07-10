package de.fampopprol.dhbwhorb.data.security

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException

/**
 * Helper class to handle Samsung device compatibility issues with encrypted storage
 */
object SamsungCompatibilityHelper {

    private const val TAG = "SamsungCompatibility"

    /**
     * Check if the device is a Samsung device
     */
    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    /**
     * Check if the device has Knox security enabled
     */
    fun hasKnoxSecurity(): Boolean {
        return try {
            val knoxClass = Class.forName("com.samsung.android.knox.SemPersonaManager")
            knoxClass != null
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Test if encrypted storage is working properly on this device
     */
    fun testEncryptedStorage(context: Context): Boolean {
        return try {
            val testMasterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(false)
                .build()

            val testPrefs = EncryptedSharedPreferences.create(
                context,
                "test_encrypted_prefs",
                testMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Test write and read
            testPrefs.edit().putString("test_key", "test_value").apply()
            val testValue = testPrefs.getString("test_key", null)

            // Clean up
            testPrefs.edit().clear().apply()

            testValue == "test_value"
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "EncryptedSharedPreferences test failed with GeneralSecurityException", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences test failed", e)
            false
        }
    }

    /**
     * Get device info for debugging Samsung-specific issues
     */
    fun getDeviceInfo(): String {
        return buildString {
            appendLine("Device Info:")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Android Version: ${Build.VERSION.RELEASE}")
            appendLine("API Level: ${Build.VERSION.SDK_INT}")
            appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Is Samsung: ${isSamsungDevice()}")
            appendLine("Has Knox: ${hasKnoxSecurity()}")
        }
    }

    /**
     * Log device-specific information for debugging
     */
    fun logDeviceInfo(context: Context) {
        Log.i(TAG, getDeviceInfo())
        Log.i(TAG, "Encrypted Storage Test: ${testEncryptedStorage(context)}")
    }
}
