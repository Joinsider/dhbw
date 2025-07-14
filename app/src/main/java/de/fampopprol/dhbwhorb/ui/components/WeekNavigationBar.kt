/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun WeekNavigationBar(
    currentWeekStart: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    isLoading: Boolean,
    lastUpdated: String? = null,
    modifier: Modifier = Modifier
) {
    val weekEnd = currentWeekStart.plusDays(6)
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
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
                IconButton(
                    onClick = onPreviousWeek,
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "Previous Week",
                        tint = if (isLoading) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Week range display and current week button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${dateFormatter.format(currentWeekStart)} - ${dateFormatter.format(weekEnd)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    if (!isCurrentWeek) {
                        TextButton(
                            onClick = onCurrentWeek,
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Today,
                                contentDescription = "Go to Current Week",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Current Week",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Next week button
                IconButton(
                    onClick = onNextWeek,
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Next Week",
                        tint = if (isLoading) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            // Last updated info
            lastUpdated?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Last updated: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}