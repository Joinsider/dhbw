/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.navigationBars.dayNavigationBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.ui.components.navigationBars.weekNavigationBar.LastUpdatedInfo
import de.fampopprol.dhbwhorb.ui.components.navigationBars.weekNavigationBar.WeekNavigationButton
import java.time.LocalDate

@Composable
fun DayNavigationBar(
    currentDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onCurrentDay: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    lastUpdated: String? = null
) {
    val isToday = currentDate == LocalDate.now()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous day button
                WeekNavigationButton(
                    onClick = onPreviousDay,
                    icon = Icons.Default.ChevronLeft,
                    contentDescription = "Previous Day",
                    enabled = !isLoading
                )

                // Current date display and today button
                DayRangeDisplay(
                    currentDate = currentDate,
                    isToday = isToday,
                    onCurrentDay = onCurrentDay,
                    isLoading = isLoading
                )

                // Next day button
                WeekNavigationButton(
                    onClick = onNextDay,
                    icon = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.next_week),
                    enabled = !isLoading
                )
            }

            // Last updated info
            lastUpdated?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LastUpdatedInfo(lastUpdated = it)
                }
            }
        }
    }
}
