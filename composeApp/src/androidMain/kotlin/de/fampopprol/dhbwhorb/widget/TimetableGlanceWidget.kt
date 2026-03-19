// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import de.fampopprol.dhbwhorb.widget.layouts.DailyScheduleWideLayout
import de.fampopprol.dhbwhorb.widget.layouts.UpNextLayout
import de.fampopprol.dhbwhorb.widget.layouts.WeekSummaryTallLayout
import de.fampopprol.dhbwhorb.widget.layouts.WeeklyTimelineLargeLayout
import de.fampopprol.dhbwhorb.widget.state.TimetableWidgetState
import de.fampopprol.dhbwhorb.widget.state.WidgetStateCodec
import de.fampopprol.dhbwhorb.widget.sync.WidgetSyncWorker
import de.fampopprol.dhbwhorb.widget.theme.TimetableWidgetTheme
import io.github.aakira.napier.Napier

/** Responsive size breakpoints (matches the 2x2 / 2x4 / 4x2 / 4x4 grid). */
private val SMALL_2x2 = DpSize(110.dp, 110.dp)
private val TALL_2x4  = DpSize(110.dp, 220.dp)
private val WIDE_4x2  = DpSize(220.dp, 110.dp)
private val LARGE_4x4 = DpSize(220.dp, 220.dp)

/**
 * Main Glance widget. Uses [SizeMode.Responsive] to pick among four layouts:
 *
 * | Size    | Layout                        |
 * |---------|-------------------------------|
 * | 2×2     | [UpNextLayout]                |
 * | 2×4     | [WeekSummaryTallLayout]       |
 * | 4×2     | [DailyScheduleWideLayout]     |
 * | 4×4     | [WeeklyTimelineLargeLayout]   |
 */
class TimetableGlanceWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "TimetableGlanceWidget"
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_2x2, TALL_2x4, WIDE_4x2, LARGE_4x4)
    )

    override suspend fun provideGlance(context: android.content.Context, id: GlanceId) {
        provideContent {
            TimetableWidgetTheme {
                val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
                val state = WidgetStateCodec.decode(prefs)
                ResponsiveContent(state)
            }
        }
    }

    @Composable
    private fun ResponsiveContent(state: TimetableWidgetState) {
        val size = LocalSize.current
        Napier.d("Widget size: ${size.width} x ${size.height}", tag = TAG)

        when {
            size.width >= LARGE_4x4.width && size.height >= LARGE_4x4.height ->
                WeeklyTimelineLargeLayout(state)

            size.width >= WIDE_4x2.width ->
                DailyScheduleWideLayout(state)

            size.height >= TALL_2x4.height ->
                WeekSummaryTallLayout(state)

            else ->
                UpNextLayout(state)
        }
    }
}

/** BroadcastReceiver entry-point for the widget system. */
class TimetableGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

    companion object {
        private const val TAG = "TimetableGlanceWidgetReceiver"
    }

    override val glanceAppWidget: GlanceAppWidget = TimetableGlanceWidget()

    override fun onUpdate(
        context: android.content.Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Napier.d("onUpdate triggered – enqueueing immediate sync", tag = TAG)
        WidgetSyncWorker.enqueueImmediate(context)
    }
}

