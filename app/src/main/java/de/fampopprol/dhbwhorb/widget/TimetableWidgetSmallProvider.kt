/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import de.fampopprol.dhbwhorb.MainActivity
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class TimetableWidgetSmallProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("TimetableWidgetSmall", "onUpdate called for ${appWidgetIds.size} widgets")

        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("TimetableWidgetSmall", "Small widget enabled")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("TimetableWidgetSmall", "Small widget disabled")
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d("TimetableWidgetSmall", "Updating small widget $appWidgetId")

            val views = RemoteViews(context.packageName, R.layout.widget_timetable_small)

            try {
                val today = LocalDate.now()
                val currentTime = LocalTime.now()

                // Get today and tomorrow events
                val (todayEvents, tomorrowEvents) = getTodayAndTomorrowEvents(context, today)

                Log.d(
                    "TimetableWidgetSmall",
                    "Found ${todayEvents.size} events for today, ${tomorrowEvents.size} events for tomorrow"
                )

                // Get relevant events to display (up to 2)
                val eventsToShow = getRelevantEventsForSmallWidget(todayEvents, tomorrowEvents, currentTime)

                Log.d("TimetableWidgetSmall", "Selected ${eventsToShow.size} events to display")
                eventsToShow.forEachIndexed { index, (event, dayLabel, status) ->
                    Log.d("TimetableWidgetSmall", "Event $index: ${event.title} ($dayLabel - $status)")
                }

                // Update widget content with selected events
                updateSmallWidgetWithMultipleEvents(views, eventsToShow)

                // Set up click intent to open the app
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container_small, pendingIntent)

            } catch (e: Exception) {
                Log.e("TimetableWidgetSmall", "Error updating small widget", e)
                // Show error state
                updateSmallWidgetWithMultipleEvents(views, emptyList())
            }

            // Tell the AppWidgetManager to perform an update on the current widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d("TimetableWidgetSmall", "Small widget $appWidgetId updated successfully")
        }

        private fun getTodayAndTomorrowEvents(
            context: Context,
            today: LocalDate
        ): Pair<List<TimetableEvent>, List<TimetableEvent>> {
            val cacheManager = TimetableCacheManager(context)

            // Calculate week starts for today and tomorrow
            val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val tomorrow = today.plusDays(1)
            val tomorrowWeekStart =
                tomorrow.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

            Log.d("TimetableWidgetSmall", "Today: $today (week start: $weekStart)")
            Log.d("TimetableWidgetSmall", "Tomorrow: $tomorrow (week start: $tomorrowWeekStart)")

            // Load current week timetable
            val timetableWeek = cacheManager.loadTimetable(weekStart)
            Log.d(
                "TimetableWidgetSmall",
                "Loaded timetable for current week: ${timetableWeek?.size ?: 0} days"
            )

            // Load next week timetable if tomorrow is in next week
            val tomorrowTimetableWeek = if (tomorrowWeekStart != weekStart) {
                Log.d(
                    "TimetableWidgetSmall",
                    "Tomorrow is in next week, loading next week timetable"
                )
                cacheManager.loadTimetable(tomorrowWeekStart)
            } else {
                timetableWeek
            }
            Log.d(
                "TimetableWidgetSmall",
                "Loaded timetable for tomorrow's week: ${tomorrowTimetableWeek?.size ?: 0} days"
            )

            // Find today's events - check both date formats
            val todayISO = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val todayGerman = today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val todayEvents = timetableWeek?.find { day ->
                Log.d(
                    "TimetableWidgetSmall",
                    "Checking day: ${day.date} against today: $todayISO or $todayGerman"
                )
                day.date == todayISO || day.date == todayGerman
            }?.events ?: emptyList()

            // Find tomorrow's events - check both date formats
            val tomorrowISO = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val tomorrowGerman = tomorrow.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val tomorrowEvents = tomorrowTimetableWeek?.find { day ->
                Log.d(
                    "TimetableWidgetSmall",
                    "Checking day: ${day.date} against tomorrow: $tomorrowISO or $tomorrowGerman"
                )
                day.date == tomorrowISO || day.date == tomorrowGerman
            }?.events ?: emptyList()

            Log.d(
                "TimetableWidgetSmall",
                "Final result - Today events: ${todayEvents.size}, Tomorrow events: ${tomorrowEvents.size}"
            )

            return Pair(todayEvents, tomorrowEvents)
        }

        private fun getRelevantEventsForSmallWidget(
            todayEvents: List<TimetableEvent>,
            tomorrowEvents: List<TimetableEvent>,
            currentTime: LocalTime
        ): List<Triple<TimetableEvent, String, String>> {
            val result = mutableListOf<Triple<TimetableEvent, String, String>>()

            if (todayEvents.isNotEmpty()) {
                // Prioritize today's events
                val sortedTodayEvents = todayEvents.sortedBy { it.startTime }
                val (currentEvent, nextEvents) = findCurrentAndRelevantEvents(sortedTodayEvents, currentTime)

                // Add current event first if exists
                currentEvent?.let {
                    result.add(Triple(it, "Today", "Now"))
                    Log.d("TimetableWidgetSmall", "Added current event: ${it.title}")
                }

                // Add next/future events from today (up to 2 total)
                nextEvents.take(2 - result.size).forEach { event ->
                    val status = if (result.isEmpty()) "Next" else "Later"
                    result.add(Triple(event, "Today", status))
                    Log.d("TimetableWidgetSmall", "Added today event: ${event.title} ($status)")
                }

            } else if (tomorrowEvents.isNotEmpty()) {
                // Only show tomorrow if no events today
                val firstTomorrowEvent = tomorrowEvents.minByOrNull { it.startTime }
                firstTomorrowEvent?.let {
                    result.add(Triple(it, "Tomorrow", "Next"))
                    Log.d("TimetableWidgetSmall", "Added tomorrow event: ${it.title}")
                }
            }

            return result
        }

        private fun findCurrentAndRelevantEvents(
            events: List<TimetableEvent>,
            currentTime: LocalTime
        ): Pair<TimetableEvent?, List<TimetableEvent>> {
            var currentEvent: TimetableEvent? = null
            val relevantEvents = mutableListOf<TimetableEvent>()

            for (event in events) {
                try {
                    val startTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
                    val endTime = LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))

                    when {
                        // Current event (happening now)
                        currentTime.isAfter(startTime) && currentTime.isBefore(endTime) -> {
                            currentEvent = event
                        }
                        // Future events or recent past events (if no current event)
                        currentTime.isBefore(startTime) || (currentEvent == null && currentTime.isAfter(endTime)) -> {
                            relevantEvents.add(event)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TimetableWidgetSmall", "Error parsing time for event: ${event.title}", e)
                    // Add to relevant events if time parsing fails
                    relevantEvents.add(event)
                }
            }

            return Pair(currentEvent, relevantEvents)
        }

        private fun updateSmallWidgetWithMultipleEvents(
            views: RemoteViews,
            events: List<Triple<TimetableEvent, String, String>>
        ) {
            when (events.size) {
                0 -> {
                    // No events
                    views.setTextViewText(R.id.widget_small_date, "Today")
                    views.setTextViewText(R.id.widget_small_status, "Free")
                    updateSmallWidgetEvent(views, 1, null)
                    views.setViewVisibility(R.id.widget_small_event2_container, View.GONE)
                }
                1 -> {
                    // One event
                    val (event, dayLabel, status) = events[0]
                    views.setTextViewText(R.id.widget_small_date, dayLabel)
                    views.setTextViewText(R.id.widget_small_status, status)
                    updateSmallWidgetEvent(views, 1, event)
                    views.setViewVisibility(R.id.widget_small_event2_container, View.GONE)
                }
                else -> {
                    // Two events
                    val (event1, dayLabel, status) = events[0]
                    val (event2, _, _) = events[1]

                    views.setTextViewText(R.id.widget_small_date, dayLabel)
                    views.setTextViewText(R.id.widget_small_status, status)
                    updateSmallWidgetEvent(views, 1, event1)
                    updateSmallWidgetEvent(views, 2, event2)
                    views.setViewVisibility(R.id.widget_small_event2_container, View.VISIBLE)
                }
            }
        }

        private fun updateSmallWidgetEvent(
            views: RemoteViews,
            eventNumber: Int,
            event: TimetableEvent?
        ) {
            val titleId = if (eventNumber == 1) R.id.widget_small_title1 else R.id.widget_small_title2
            val timeId = if (eventNumber == 1) R.id.widget_small_time1 else R.id.widget_small_time2
            val locationId = if (eventNumber == 1) R.id.widget_small_location1 else R.id.widget_small_location2

            if (event != null) {
                views.setTextViewText(titleId, event.title)
                views.setTextViewText(timeId, "${event.startTime} - ${event.endTime}")
                views.setTextViewText(locationId, event.room)
            } else {
                views.setTextViewText(titleId, "No classes")
                views.setTextViewText(timeId, "Free time!")
                views.setTextViewText(locationId, "")
            }
        }
    }
}
