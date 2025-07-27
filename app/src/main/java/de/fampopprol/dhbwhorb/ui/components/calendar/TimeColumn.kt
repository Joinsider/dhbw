/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun TimeColumn(
    startHour: Int,
    endHour: Int,
    hourHeight: Dp,
    timeColumnWidth: Dp
) {
    val fontSize = 10.sp
    val padding = 4.dp

    Column(
        modifier = Modifier.width(timeColumnWidth)
    ) {
        for (hour in startHour until endHour) {
            Box(
                modifier = Modifier.height(hourHeight),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d:00", hour),
                    fontSize = fontSize,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = padding, top = 2.dp)
                )
            }
        }
    }
}
