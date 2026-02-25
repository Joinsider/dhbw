package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.dark_mode
import de.fampopprol.dhbwhorb.resources.design
import de.fampopprol.dhbwhorb.resources.light_mode
import de.fampopprol.dhbwhorb.resources.material_you
import de.fampopprol.dhbwhorb.resources.material_you_description
import de.fampopprol.dhbwhorb.resources.system_default
import de.fampopprol.dhbwhorb.resources.theme
import de.fampopprol.dhbwhorb.util.PlatformType
import de.fampopprol.dhbwhorb.util.getPlatform
import org.jetbrains.compose.resources.stringResource

import androidx.compose.ui.graphics.Color
import de.fampopprol.dhbwhorb.ui.components.ColorPicker

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesignSelectionCard(
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    materialYouEnabled: Boolean = true,
    onMaterialYouChange: (Boolean) -> Unit = {},
    currentSeedColor: Color = Color(0xFF6650a4),
    onSeedColorChange: (Color) -> Unit = {}
){
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.design),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = stringResource(Res.string.theme),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.size(8.dp))

            val options = listOf(
                Triple(ThemeMode.LIGHT, Icons.Default.LightMode, stringResource(Res.string.light_mode)),
                Triple(ThemeMode.DARK, Icons.Default.DarkMode, stringResource(Res.string.dark_mode)),
                Triple(ThemeMode.SYSTEM, Icons.Default.SettingsBrightness, stringResource(Res.string.system_default))
            )

            val hapticFeedback = LocalHapticFeedback.current

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                options.forEachIndexed { index, (mode, icon, label) ->
                    ToggleButton(
                        checked = currentThemeMode == mode,
                        onCheckedChange = {
                            onThemeModeChange(mode)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("theme${label}Button"),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        Text(label)
                    }
                }
            }

            // Material You toggle - only shown on Android
            if (getPlatform() == PlatformType.ANDROID) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(Res.string.material_you),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(Res.string.material_you_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = materialYouEnabled,
                        onCheckedChange = {
                            onMaterialYouChange(it)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        },
                        modifier = Modifier.testTag("materialYouSwitch")
                    )
                }
            }
            
            // Color Picker - Show if NOT Android OR (Android AND Material You Disabled)
            if (getPlatform() != PlatformType.ANDROID || !materialYouEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                ColorPicker(
                    selectedColor = currentSeedColor,
                    onColorSelected = {
                        onSeedColorChange(it)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                )
            }
        }
    }
}