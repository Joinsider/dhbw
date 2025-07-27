/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.settingsScreen.generalSettingsSection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.theme.ThemePreferencesManager
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.generalSettingsSection.themeMode.ThemeModeButtonRow
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.generalSettingsSection.themeMode.ThemeModeHeader
import de.fampopprol.dhbwhorb.ui.theme.ThemeMode

@Composable
fun ThemeModeSelector(
    themePreferencesManager: ThemePreferencesManager,
    themeMode: ThemeMode,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeModeHeader(currentThemeMode = themeMode)

            ThemeModeButtonRow(
                themePreferencesManager = themePreferencesManager,
                currentThemeMode = themeMode
            )
        }
    }
}
