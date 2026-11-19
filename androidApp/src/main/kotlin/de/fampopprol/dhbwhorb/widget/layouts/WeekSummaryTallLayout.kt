// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.layouts

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import de.fampopprol.dhbwhorb.MainActivity
import de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState
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
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)))
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
                        ClassCard(cls, tintLocationForTest = false)
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
