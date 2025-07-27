/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.navigationBars.dayNavigationBar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DayRangeDisplay(
    currentDate: LocalDate,
    isToday: Boolean,
    onCurrentDay: () -> Unit,
    isLoading: Boolean
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.getDefault())

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
                    text = stringResource(R.string.today),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
