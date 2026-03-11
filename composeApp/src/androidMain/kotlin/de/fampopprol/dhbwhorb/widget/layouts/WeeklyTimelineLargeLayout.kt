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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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
                                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            )
                            Spacer(GlanceModifier.height(6.dp))
                            day.classes.forEach { cls ->
                                ClassCard(cls)
                                Spacer(GlanceModifier.height(4.dp))
                            }
                        } ?: Text("Keine Vorlesungen", style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 11.sp))
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    // Day 1
                    Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                        state.day1?.let { day ->
                            Text(
                                text = formatDate(day.date),
                                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
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

