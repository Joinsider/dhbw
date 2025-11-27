/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.choose_color
import de.joinside.dhbw.resources.choose_color_description
import org.jetbrains.compose.resources.stringResource

@Composable
fun ColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color(0xFFFF91FF), // pink
        Color(0xFF6650a4), // Purple40
        Color(0xFF1976D2), // Blue
        Color(0xFF039BE5), // Light Blue
        Color(0xFF00ACC1), // Cyan
        Color(0xFF00897B), // Teal
        Color(0xFF43A047), // Green
        Color(0xFF7CB342), // Light Green
        Color(0xFFC0CA33), // Lime
        Color(0xFFFDD835), // Yellow
        Color(0xFFFFB300), // Amber
        Color(0xFFFB8C00), // Orange
        Color(0xFFF4511E), // Deep Orange
        Color(0xFFE53935), // Red
        Color(0xFFD81B60), // Pink
        Color(0xFF8E24AA), // Purple
        Color(0xFF5E35B1), // Deep Purple
        Color(0xFF3949AB), // Indigo
        Color(0xFF546E7A), // Blue Grey
    )

    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.choose_color),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(Res.string.choose_color_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(colors) { color ->
                val isSelected = selectedColor == color
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onColorSelected(color) }
                        .then(
                            if (isSelected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White, // Assuming these are dark enough, or calculate contrast
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
