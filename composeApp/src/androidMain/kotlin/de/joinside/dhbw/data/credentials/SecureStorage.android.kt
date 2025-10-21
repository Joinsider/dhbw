package de.joinside.dhbw.data.credentials

// androidMain/SecureStorage.android.kt
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SecureStorage {
    companion object {
        private var applicationContext: Context? = null

        fun initialize(context: Context) {
            applicationContext = context.applicationContext
        }
    }

    private val context: Context
        get() = applicationContext ?: throw IllegalStateException(
            "SecureStorage must be initialized. Call SecureStorage.initialize(context) in your Application class."
        )

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "dualis_secure_storage",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual fun setString(key: String, value: String) {
        sharedPreferences.edit { putString(key, value) }
    }

    actual fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    actual fun remove(key: String) {
        sharedPreferences.edit { remove(key) }
    }

    actual fun clear() {
        sharedPreferences.edit { clear() }
    }
}
