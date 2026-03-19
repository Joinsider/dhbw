// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.layouts

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.fampopprol.dhbwhorb.MainActivity
import de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState
import de.fampopprol.dhbwhorb.widget.state.TimetableWidgetState

/** 4×2 layout – "Daily Schedule (Wide)". */
@Composable
fun DailyScheduleWideLayout(state: TimetableWidgetState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)))
            .padding(12.dp),
    ) {
        when (state) {
            TimetableWidgetState.Loading -> LoadingContent()
            is TimetableWidgetState.Error -> ErrorContent(state.message)
            is TimetableWidgetState.Success -> WideSuccessContent(state)
        }
    }
}

@Composable
private fun WideSuccessContent(state: TimetableWidgetState.Success) {
    Row(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            state.day0?.let { day ->
                Text(
                    formatDate(day.date),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.height(4.dp))
                day.classes.take(4)
                    .forEach { cls -> WideRow(cls); Spacer(GlanceModifier.height(3.dp)) }
                if (day.classes.size > 4) Text(
                    "+${day.classes.size - 4} weitere",
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 10.sp)
                )
            } ?: Text(
                "Keine Vorlesungen",
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 11.sp)
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            state.day1?.let { day ->
                Text(
                    formatDate(day.date),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.height(4.dp))
                day.classes.take(4)
                    .forEach { cls -> WideRow(cls); Spacer(GlanceModifier.height(3.dp)) }
                if (day.classes.size > 4) Text(
                    "+${day.classes.size - 4} weitere",
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 10.sp)
                )
            }
        }
    }
}

@Composable
private fun WideRow(cls: WidgetClassState) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "•",
            style = TextStyle(
                color = if (cls.isTest) GlanceTheme.colors.error else GlanceTheme.colors.secondary,
                fontSize = 11.sp,
            ),
        )
        Spacer(GlanceModifier.width(3.dp))
        Column {
            Text(
                text = cls.shortName,
                style = TextStyle(
                    color = if (cls.isTest) GlanceTheme.colors.error else GlanceTheme.colors.onBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = "${cls.formattedStartTime}-${cls.formattedEndTime} · ${cls.location}",
                style = TextStyle(
                    color = if (cls.isTest) GlanceTheme.colors.error else GlanceTheme.colors.secondary,
                    fontSize = 9.sp,
                ),
            )
        }
    }
}
