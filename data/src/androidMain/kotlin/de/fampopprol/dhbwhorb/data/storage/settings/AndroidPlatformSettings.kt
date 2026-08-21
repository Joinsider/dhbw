package de.fampopprol.dhbwhorb.data.storage.settings

import android.content.Context
import androidx.core.content.edit

/** Ordinary shared preferences — these values are settings, not secrets. */
class AndroidPlatformSettings(context: Context) : PlatformSettings {
    private val preferences =
        context.applicationContext.getSharedPreferences("dualis_settings", Context.MODE_PRIVATE)

    override fun getStringOrNull(key: String): String? = preferences.getString(key, null)

    override fun setString(key: String, value: String) = preferences.edit { putString(key, value) }

    override fun remove(key: String) = preferences.edit { remove(key) }
}
