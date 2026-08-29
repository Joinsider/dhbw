package de.fampopprol.dhbwhorb.data.storage.settings

import platform.Foundation.NSUserDefaults

/** `NSUserDefaults` — the ordinary place for settings on Apple platforms. */
class ApplePlatformSettings : PlatformSettings {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getStringOrNull(key: String): String? = defaults.stringForKey(key)

    override fun setString(key: String, value: String) = defaults.setObject(value, forKey = key)

    override fun remove(key: String) = defaults.removeObjectForKey(key)
}
