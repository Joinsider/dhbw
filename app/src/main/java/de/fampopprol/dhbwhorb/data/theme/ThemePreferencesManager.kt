package de.fampopprol.dhbwhorb.data.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.fampopprol.dhbwhorb.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

class ThemePreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val MATERIAL_YOU_ENABLED = booleanPreferencesKey("material_you_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val materialYouEnabled: Flow<Boolean> = context.themeDataStore.data.map { preferences ->
        preferences[PreferencesKeys.MATERIAL_YOU_ENABLED] ?: true
    }

    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { preferences ->
        val themeModeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(themeModeString)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setMaterialYouEnabled(enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[PreferencesKeys.MATERIAL_YOU_ENABLED] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }
}
