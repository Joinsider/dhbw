/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class CalendarViewMode {
    WEEKLY,
    DAILY
}

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
        Column {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                tabs.forEachIndexed { index, (mode, title) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { onViewModeChanged(mode) },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = when (mode) {
                                    CalendarViewMode.WEEKLY -> Icons.Default.CalendarViewWeek
                                    CalendarViewMode.DAILY -> Icons.Default.Today
                                },
                                contentDescription = "$title View",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
