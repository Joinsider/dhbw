// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.layouts

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState

/**
 * One lecture as a card: time (with a TEST marker for exams), name and room. Shared by the week
 * summary and the weekly timeline; the timeline also tints the room of an exam, the summary
 * keeps it secondary.
 */
@Composable
internal fun ClassCard(cls: WidgetClassState, tintLocationForTest: Boolean) {
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
                    color = if (cls.isTest && tintLocationForTest) GlanceTheme.colors.onErrorContainer
                    else GlanceTheme.colors.secondary,
                    fontSize = 10.sp,
                ),
            )
        }
    }
}
