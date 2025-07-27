/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.calendar.eventPopup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import de.fampopprol.dhbwhorb.ui.components.utils.formatEventDate

@Composable
fun EventDetailPopup(
    event: TimetableEvent,
    eventDate: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp) // Increased padding for better spacing
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.popup_heading),
                        style = MaterialTheme.typography.headlineSmall, // Larger header text
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp)) // Increased spacing

                // Use full title if available, otherwise fall back to regular title
                Text(
                    text = event.fullTitle ?: event.title,
                    style = MaterialTheme.typography.titleMedium, // Larger title text
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(20.dp)) // Increased spacing

                EventDetailRow(
                    label = stringResource(R.string.popup_date),
                    value = formatEventDate(eventDate)
                )

                if (event.startTime.isNotEmpty() && event.endTime.isNotEmpty()) {
                    EventDetailRow(
                        label = stringResource(R.string.popup_time),
                        value = "${event.startTime} - ${event.endTime}"
                    )
                }

                if (event.room.isNotEmpty()) {
                    EventDetailRow(
                        label = stringResource(R.string.popup_location),
                        value = event.room
                    )
                }

                // Show lecturer if available
                if (event.lecturer.isNotEmpty()) {
                    EventDetailRow(
                        label = stringResource(R.string.popup_lecturer),
                        value = event.lecturer
                    )
                }
            }
        }
    }
}
