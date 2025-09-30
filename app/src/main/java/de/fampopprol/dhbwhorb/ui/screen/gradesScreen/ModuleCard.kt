/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.gradesScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.dualis.models.ExamState
import de.fampopprol.dhbwhorb.data.dualis.models.Module
import java.util.Locale

/**
 * Formats a grade string to display with exactly 2 decimal places if it's a numerical grade
 */
private fun formatModuleGrade(grade: String): String {
    return if (grade == "noch nicht gesetzt") {
        grade // Keep as-is for "not set" grades
    } else {
        // Try to parse as double and format
        grade.replace(",", ".").toDoubleOrNull()?.let { gradeValue ->
            String.format(Locale.getDefault(), "%.2f", gradeValue)
        } ?: grade // If parsing fails, return original string
    }
}

@Composable
fun ModuleCard(
    module: Module,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (module.state) {
                ExamState.PASSED -> MaterialTheme.colorScheme.primaryContainer
                ExamState.FAILED -> MaterialTheme.colorScheme.errorContainer
                ExamState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when (module.state) {
                ExamState.PASSED -> MaterialTheme.colorScheme.onPrimaryContainer
                ExamState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                ExamState.PENDING -> MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = module.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (module.state) {
                            ExamState.PASSED -> MaterialTheme.colorScheme.onPrimaryContainer
                            ExamState.FAILED -> MaterialTheme.colorScheme.error
                            ExamState.PENDING -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatModuleGrade(module.grade),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (module.state) {
                            ExamState.PASSED -> MaterialTheme.colorScheme.onPrimaryContainer
                            ExamState.FAILED -> MaterialTheme.colorScheme.error
                            ExamState.PENDING -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = "${module.credits} ${stringResource(R.string.credit_points_short)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (module.state) {
                            ExamState.PASSED -> MaterialTheme.colorScheme.onPrimaryContainer
                            ExamState.FAILED -> MaterialTheme.colorScheme.error
                            ExamState.PENDING -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            // Status indicator
            val statusText = when (module.state) {
                ExamState.PASSED -> stringResource(R.string.passed)
                ExamState.FAILED -> stringResource(R.string.failed)
                ExamState.PENDING -> stringResource(R.string.pending)
            }

            val statusIcon = when (module.state) {
                ExamState.PASSED -> "✓"
                ExamState.FAILED -> "✗"
                ExamState.PENDING -> "⏱"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusIcon,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (module.state) {
                        ExamState.PASSED -> MaterialTheme.colorScheme.onPrimaryContainer
                        ExamState.FAILED -> MaterialTheme.colorScheme.error
                        ExamState.PENDING -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
