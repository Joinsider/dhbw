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
import androidx.glance.unit.ColorProvider
import de.fampopprol.dhbwhorb.MainActivity
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import de.fampopprol.dhbwhorb.widget.state.TimetableWidgetState

/**
 * 2×2 layout – "Up Next / Current Class".
 *
 * Shows a single prominent rounded card with the currently running or next lecture.
 */
@Composable
fun UpNextLayout(state: TimetableWidgetState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            TimetableWidgetState.Loading -> LoadingContent()
            is TimetableWidgetState.Error -> ErrorContent(state.message)
            is TimetableWidgetState.Success -> UpNextSuccessContent(state.upNext)
        }
    }
}

@Composable
private fun UpNextSuccessContent(upNext: WidgetUpNextState) {
    when (upNext) {
        WidgetUpNextState.NoMoreClassesToday -> {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "✓",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Keine weiteren\nVorlesungen heute",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 11.sp,
                    ),
                )
            }
        }

        is WidgetUpNextState.CurrentlyRunning -> {
            val lecture = upNext.lecture
            Column(modifier = GlanceModifier.fillMaxSize()) {
                StatusPill(
                    label = if (lecture.isTest) "KLAUSUR LÄUFT" else "Läuft jetzt",
                    isOngoing = true,
                    isTest = lecture.isTest
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = lecture.shortName,
                    style = TextStyle(
                        color = if (lecture.isTest) GlanceTheme.colors.error else GlanceTheme.colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 2,
                )

                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "${lecture.formattedStartTime} – ${lecture.formattedEndTime}",
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 11.sp),
                )
                Text(
                    text = lecture.location,
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 11.sp),
                )
            }
        }

        is WidgetUpNextState.ComingUp -> {
            val lecture = upNext.lecture
            Column(modifier = GlanceModifier.fillMaxSize()) {
                StatusPill(
                    label = if (lecture.isTest) "NÄCHSTE: KLAUSUR" else "Nächste",
                    isOngoing = false,
                    isTest = lecture.isTest
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = lecture.shortName,
                    style = TextStyle(
                        color = if (lecture.isTest) GlanceTheme.colors.error else GlanceTheme.colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "${lecture.formattedStartTime} – ${lecture.formattedEndTime}",
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 11.sp),
                )
                Text(
                    text = lecture.location,
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
internal fun StatusPill(label: String, isOngoing: Boolean, isTest: Boolean) {
    val bgColor = when {
        isTest -> GlanceTheme.colors.error
        isOngoing -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.secondary
    }
    val contentColor = when {
        isTest -> GlanceTheme.colors.onError
        isOngoing -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSecondary
    }

    Box(
        modifier = GlanceModifier
            .cornerRadius(12.dp)
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
internal fun LoadingContent() {
    Text(
        text = "Lädt…",
        style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 12.sp),
    )
}

@Composable
internal fun ErrorContent(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "⚠",
            style = TextStyle(color = GlanceTheme.colors.error, fontSize = 20.sp),
        )
        Text(
            text = message.take(60),
            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 10.sp),
            maxLines = 3,
        )
    }
}
