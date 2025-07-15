/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class CalendarViewMode {
    WEEKLY,
    DAILY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewBottomBar(
    currentViewMode: CalendarViewMode,
    onViewModeChanged: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        CalendarViewMode.WEEKLY to "Weekly",
        CalendarViewMode.DAILY to "Daily"
    )

    val selectedTabIndex = when (currentViewMode) {
        CalendarViewMode.WEEKLY -> 0
        CalendarViewMode.DAILY -> 1
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            tabs.forEachIndexed { index, (mode, title) ->
                SegmentedButton(
                    selected = selectedTabIndex == index,
                    onClick = { onViewModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                    icon = {
                        Icon(
                            imageVector = when (mode) {
                                CalendarViewMode.WEEKLY -> Icons.Default.CalendarViewWeek
                                CalendarViewMode.DAILY -> Icons.Default.Today
                            },
                            contentDescription = "$title View",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }
    }
}
