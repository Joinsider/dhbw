package de.joinside.dhbw.data.credentials

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class SecureStorage() {
    fun setString(key: String, value: String)
    fun getString(key: String, defaultValue: String = ""): String
    fun remove(key: String)
    fun clear()
}
