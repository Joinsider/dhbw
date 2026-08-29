package de.fampopprol.dhbwhorb.data.storage.settings

/**
 * Plain key-value storage for things that are **not** secrets: theme, colour, notification toggles.
 *
 * Separate from `SecureStorageInterface` on purpose. Both used to be the same interface, and the
 * consequence was that the desktop build asked the user's permission — one macOS Keychain dialog
 * per entry, eight on a cold start — to read the colour scheme. On Android and iOS the same mistake
 * was invisible, which is why it survived: encrypted preferences and the Keychain never prompt
 * there. The kind of storage a value needs is a property of the value, not of the platform.
 *
 * There is deliberately no `clear()`. Nothing needs one — logging out clears credentials, not the
 * user's theme — and on Apple platforms a naive one would empty the whole `NSUserDefaults` domain,
 * system keys included. Add it when something actually needs it, and scope it then.
 */
interface PlatformSettings {
    fun getStringOrNull(key: String): String?
    fun setString(key: String, value: String)
    fun remove(key: String)
}
