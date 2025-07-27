/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.settingsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.theme.ThemePreferencesManager
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.generalSettingsSection.GeneralSettingsHeader
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.generalSettingsSection.MaterialYouToggle
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.generalSettingsSection.ThemeModeSelector
import de.fampopprol.dhbwhorb.ui.theme.ThemeMode

@Composable
fun GeneralSettingsSection(
    themePreferencesManager: ThemePreferencesManager,
    modifier: Modifier = Modifier
) {
    val materialYouEnabled by themePreferencesManager.materialYouEnabled.collectAsState(initial = true)
    val themeMode by themePreferencesManager.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GeneralSettingsHeader()

        Spacer(modifier = Modifier.height(8.dp))

        MaterialYouToggle(
            themePreferencesManager = themePreferencesManager,
            materialYouEnabled = materialYouEnabled
        )

        ThemeModeSelector(
            themePreferencesManager = themePreferencesManager,
            themeMode = themeMode
        )
    }
}
