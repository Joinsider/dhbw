/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */


package de.fampopprol.dhbwhorb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.getDefault())
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
                IconButton(
                    onClick = onPreviousDay,
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Day",
                        tint = if (isLoading) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Current date display and today button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentDate.format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!isToday) {
                        TextButton(
                            onClick = onCurrentDay,
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = "Go to Today",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Next day button
                IconButton(
                    onClick = onNextDay,
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Day",
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
