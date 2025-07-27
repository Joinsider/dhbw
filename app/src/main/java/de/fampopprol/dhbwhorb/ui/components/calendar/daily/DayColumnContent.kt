/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.calendar.daily

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import de.fampopprol.dhbwhorb.ui.components.calendar.eventPopup.TimeBasedEventItem
import de.fampopprol.dhbwhorb.ui.components.utils.EventPosition
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun DayColumnContent(
    day: LocalDate,
    timetableDay: TimetableDay?,
    startHour: Int,
    endHour: Int,
    hourHeight: Dp,
    onEventClick: (TimetableEvent) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Hour grid lines
        for (hour in startHour until endHour) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = ((hour - startHour) * hourHeight.value).dp)
            )
        }

        // Events positioned by time
        timetableDay?.events?.forEach { event ->
            val eventPosition = remember(event, startHour, hourHeight) {
                calculateEventPosition(event, startHour, hourHeight)
            }

            if (eventPosition != null) {
                TimeBasedEventItem(
                    event = event,
                    onClick = { onEventClick(event) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(eventPosition.height)
                        .offset(y = eventPosition.topOffset)
                        .padding(horizontal = 2.dp)
                        .zIndex(1f)
                )
            }
        }

        // Show "No classes" only if no events for the day
        if (timetableDay?.events?.isEmpty() != false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No classes",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun calculateEventPosition(
    event: TimetableEvent,
    startHour: Int,
    hourHeight: Dp
): EventPosition? {
    return try {
        val startTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
        val endTime = LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))

        // Calculate position based on hours
        val startMinutesFromStart = (startTime.hour - startHour) * 60 + startTime.minute
        val endMinutesFromStart = (endTime.hour - startHour) * 60 + endTime.minute
        val durationMinutes =
            max(endMinutesFromStart - startMinutesFromStart, 30) // Minimum 30 minutes height

        val pixelsPerMinute = hourHeight.value / 60f
        val topOffset = (startMinutesFromStart * pixelsPerMinute).dp
        val height = (durationMinutes * pixelsPerMinute).dp

        EventPosition(topOffset, height)
    } catch (e: Exception) {
        Log.e("DayColumnContent", "Error calculating position for event: ${event.title}", e)
        null
    }
}
