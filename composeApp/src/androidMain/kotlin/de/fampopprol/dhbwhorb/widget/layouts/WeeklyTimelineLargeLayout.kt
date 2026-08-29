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
import androidx.glance.layout.fillMaxHeight
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

/**
 * 4×4 layout – "Weekly Timeline (Large)".
 *
 * Two columns showing rounded cards for two upcoming days side-by-side.
 */
@Composable
fun WeeklyTimelineLargeLayout(state: TimetableWidgetState) {
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
            is TimetableWidgetState.Success -> {
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    // Day 0
                    Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                        state.day0?.let { day ->
                            Text(
                                text = formatDate(day.date),
                                style = TextStyle(
                                    color = GlanceTheme.colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                            )
                            Spacer(GlanceModifier.height(6.dp))
                            day.classes.forEach { cls ->
                                ClassCard(cls)
                                Spacer(GlanceModifier.height(4.dp))
                            }
                        } ?: Text(
                            "Keine Vorlesungen",
                            style = TextStyle(
                                color = GlanceTheme.colors.secondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    // Day 1
                    Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                        state.day1?.let { day ->
                            Text(
                                text = formatDate(day.date),
                                style = TextStyle(
                                    color = GlanceTheme.colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                            )
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
    }
}

@Composable
private fun ClassCard(cls: WidgetClassState) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(
                if (cls.isTest) GlanceTheme.colors.errorContainer
                else GlanceTheme.colors.surface
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${cls.formattedStartTime}–${cls.formattedEndTime}",
                    style = TextStyle(
                        color = if (cls.isTest) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.secondary,
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
                    color = if (cls.isTest) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = cls.location,
                style = TextStyle(
                    color = if (cls.isTest) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.secondary,
                    fontSize = 10.sp,
                ),
            )
        }
    }
}
