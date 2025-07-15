/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.fampopprol.dhbwhorb.R

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
    val items = listOf(
        CalendarViewMode.WEEKLY to R.string.weekly_view,
        CalendarViewMode.DAILY to R.string.daily_view
    )

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
    ) {
        items.forEach { (mode, titleRes) ->
            val title = stringResource(titleRes)
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = when (mode) {
                            CalendarViewMode.WEEKLY -> Icons.Default.CalendarViewWeek
                            CalendarViewMode.DAILY -> Icons.Default.Today
                        },
                        contentDescription = title
                    )
                },
                label = { Text(title) },
                selected = currentViewMode == mode,
                onClick = { onViewModeChanged(mode) }
            )
        }
    }
}