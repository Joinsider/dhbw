// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.layouts

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetDayState
import de.fampopprol.dhbwhorb.widget.state.TimetableWidgetState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * 2×4 layout – "Week Summary (Tall)".
 *
 * Stacked rounded cards for every lecture of the first upcoming day with classes.
 */
@Composable
fun WeekSummaryTallLayout(state: TimetableWidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        when (state) {
            TimetableWidgetState.Loading -> LoadingContent()
            is TimetableWidgetState.Error -> ErrorContent(state.message)
            is TimetableWidgetState.Success -> {
                val day = state.day0
                if (day == null) {
                    NoLecturesContent()
                } else {
                    DayHeader(day.date)
                    Spacer(GlanceModifier.height(6.dp))
                    day.classes.forEach { cls ->
                        ClassCard(cls)
                        Spacer(GlanceModifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        text = formatDate(date),
        style = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
internal fun ClassCard(cls: WidgetClassState) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${cls.formattedStartTime}–${cls.formattedEndTime}",
                    style = TextStyle(
                        color = GlanceTheme.colors.secondary,
                        fontSize = 10.sp,
                    ),
                )
                if (cls.isTest) {
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = "TEST",
                        style = TextStyle(
                            color = GlanceTheme.colors.error,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
            Text(
                text = cls.shortName,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = cls.location,
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun NoLecturesContent() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Keine Vorlesungen\nin den nächsten 14 Tagen",
            style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 11.sp),
        )
    }
}

internal fun formatDate(date: LocalDate): String {
    val day = date.day.toString().padStart(2, '0')
    val month = date.month.number.toString().padStart(2, '0')
    return "$day.$month"
}

