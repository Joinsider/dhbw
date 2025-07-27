/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.navigationBars.weekNavigationBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun WeekNavigationBar(
    modifier: Modifier = Modifier,
    currentWeekStart: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    isLoading: Boolean,
    lastUpdated: String? = null
) {
    val isCurrentWeek = currentWeekStart == LocalDate.now().with(
        TemporalAdjusters.previousOrSame(
            DayOfWeek.MONDAY
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous week button
                WeekNavigationButton(
                    onClick = onPreviousWeek,
                    icon = Icons.Filled.ChevronLeft,
                    contentDescription = "Previous Week",
                    enabled = !isLoading
                )

                // Week range display and current week button
                WeekRangeDisplay(
                    currentWeekStart = currentWeekStart,
                    isCurrentWeek = isCurrentWeek,
                    onCurrentWeek = onCurrentWeek,
                    isLoading = isLoading
                )

                // Next week button
                WeekNavigationButton(
                    onClick = onNextWeek,
                    icon = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.next_week),
                    enabled = !isLoading
                )
            }

            // Last updated info
            lastUpdated?.let {
                Spacer(modifier = Modifier.height(8.dp))
                LastUpdatedInfo(lastUpdated = it)
            }
        }
    }
}