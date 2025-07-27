/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.settingsScreen.generalSettingsSection.themeMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.theme.ThemePreferencesManager
import de.fampopprol.dhbwhorb.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun ThemeModeButtonRow(
    themePreferencesManager: ThemePreferencesManager,
    currentThemeMode: ThemeMode,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            ThemeModeButton(
                themeMode = mode,
                isSelected = currentThemeMode == mode,
                onClick = {
                    scope.launch {
                        themePreferencesManager.setThemeMode(mode)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
