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

class TimetableWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("TimetableWidget", "onUpdate called for ${appWidgetIds.size} widgets")

        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("TimetableWidget", "Widget enabled")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("TimetableWidget", "Widget disabled")
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d("TimetableWidget", "Updating widget $appWidgetId")

            val views = RemoteViews(context.packageName, R.layout.widget_timetable_enhanced)

            try {
                val today = LocalDate.now()
                val currentTime = LocalTime.now()

                Log.d("TimetableWidget", "Current date: $today, time: $currentTime")

                // Get today and tomorrow events
                val (todayEvents, tomorrowEvents) = getTodayAndTomorrowEvents(context, today)

                Log.d("TimetableWidget", "Found ${todayEvents.size} events for today, ${tomorrowEvents.size} events for tomorrow")

                // Get relevant events to display (up to 4)
                val eventsToShow = getRelevantEventsForEnhancedWidget(todayEvents, tomorrowEvents, currentTime)

                Log.d("TimetableWidget", "Selected ${eventsToShow.size} events to display")
                eventsToShow.forEachIndexed { index, (event, dayLabel, status) ->
                    Log.d("TimetableWidget", "Event ${index + 1}: ${event.title} ($dayLabel - $status)")
                }

                // Update enhanced widget content with selected events
                updateEnhancedWidgetWithMultipleEvents(views, eventsToShow, today)

                // Set up click intent to open the app
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            } catch (e: Exception) {
                Log.e("TimetableWidget", "Error updating widget", e)
                // Show error state - pass empty list for error case
                updateEnhancedWidgetWithMultipleEvents(views, emptyList(), LocalDate.now())
            }

            // Tell the AppWidgetManager to perform an update on the current widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d("TimetableWidget", "Widget $appWidgetId updated successfully")
        }

        private fun getRelevantEventsForEnhancedWidget(
            todayEvents: List<TimetableEvent>,
            tomorrowEvents: List<TimetableEvent>,
            currentTime: LocalTime
        ): List<Triple<TimetableEvent, String, String>> {
            val result = mutableListOf<Triple<TimetableEvent, String, String>>()

            // Process today's events first
            if (todayEvents.isNotEmpty()) {
                val sortedTodayEvents = todayEvents.sortedBy { it.startTime }
                val (currentEvent, relevantTodayEvents) = findCurrentAndRelevantEvents(sortedTodayEvents, currentTime)

                // Add current event first if exists
                currentEvent?.let {
                    result.add(Triple(it, "Today", "Now"))
                    Log.d("TimetableWidget", "Added current event: ${it.title}")
                }

                // Add other today events (prioritize upcoming ones)
                relevantTodayEvents.take(4 - result.size).forEach { event ->
                    val status = if (result.isEmpty()) "Next" else "Later"
                    result.add(Triple(event, "Today", status))
                    Log.d("TimetableWidget", "Added today event: ${event.title} ($status)")
                }
            }

            // Fill remaining slots with tomorrow's events (up to 4 total)
            if (result.size < 4 && tomorrowEvents.isNotEmpty()) {
                val sortedTomorrowEvents = tomorrowEvents.sortedBy { it.startTime }
                sortedTomorrowEvents.take(4 - result.size).forEach { event ->
                    val status = if (result.isEmpty()) "Next" else "Tomorrow"
                    result.add(Triple(event, "Tomorrow", status))
                    Log.d("TimetableWidget", "Added tomorrow event: ${event.title}")
                }
            }

            return result
        }

        private fun findCurrentAndNextEvents(
            events: List<TimetableEvent>,
            currentTime: LocalTime
        ): Pair<TimetableEvent?, TimetableEvent?> {
            val sortedEvents = events.sortedBy { it.startTime }

            var currentEvent: TimetableEvent? = null
            var nextEvent: TimetableEvent? = null

            for (event in sortedEvents) {
                try {
                    val startTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
                    val endTime = LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))

                    Log.d("TimetableWidget", "Checking event: ${event.title} from $startTime to $endTime (current: $currentTime)")

                    when {
                        currentTime.isAfter(startTime) && currentTime.isBefore(endTime) -> {
                            currentEvent = event
                            Log.d("TimetableWidget", "Found current event: ${event.title}")
                        }

                        currentTime.isBefore(startTime) && nextEvent == null -> {
                            nextEvent = event
                            Log.d("TimetableWidget", "Found next event: ${event.title}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TimetableWidget", "Error parsing time for event: ${event.title} (${event.startTime}-${event.endTime})", e)
                }
            }

            return Pair(currentEvent, nextEvent)
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
                    Log.w("TimetableWidget", "Error parsing time for event: ${event.title}", e)
                    // Add to relevant events if time parsing fails
                    relevantEvents.add(event)
                }
            }

            return Pair(currentEvent, relevantEvents)
        }

        private fun getTodayAndTomorrowEvents(
            context: Context,
            today: LocalDate
        ): Pair<List<TimetableEvent>, List<TimetableEvent>> {
            val cacheManager = TimetableCacheManager(context)

            // Calculate week starts for today and tomorrow
            val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val tomorrow = today.plusDays(1)
            val tomorrowWeekStart = tomorrow.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

            Log.d("TimetableWidget", "Today: $today (week start: $weekStart)")
            Log.d("TimetableWidget", "Tomorrow: $tomorrow (week start: $tomorrowWeekStart)")

            // Load current week timetable
            val timetableWeek = cacheManager.loadTimetable(weekStart)
            Log.d("TimetableWidget", "Loaded timetable for current week: ${timetableWeek?.size ?: 0} days")

            // Load next week timetable if tomorrow is in next week
            val tomorrowTimetableWeek = if (tomorrowWeekStart != weekStart) {
                Log.d("TimetableWidget", "Tomorrow is in next week, loading next week timetable")
                cacheManager.loadTimetable(tomorrowWeekStart)
            } else {
                timetableWeek
            }
            Log.d("TimetableWidget", "Loaded timetable for tomorrow's week: ${tomorrowTimetableWeek?.size ?: 0} days")

            // Find today's events - check both date formats
            val todayISO = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val todayGerman = today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val todayEvents = timetableWeek?.find { day ->
                Log.d("TimetableWidget", "Checking day: ${day.date} against today: $todayISO or $todayGerman")
                day.date == todayISO || day.date == todayGerman
            }?.events ?: emptyList()

            // Find tomorrow's events - check both date formats
            val tomorrowISO = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val tomorrowGerman = tomorrow.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val tomorrowEvents = tomorrowTimetableWeek?.find { day ->
                Log.d("TimetableWidget", "Checking day: ${day.date} against tomorrow: $tomorrowISO or $tomorrowGerman")
                day.date == tomorrowISO || day.date == tomorrowGerman
            }?.events ?: emptyList()

            Log.d("TimetableWidget", "Final result - Today events: ${todayEvents.size}, Tomorrow events: ${tomorrowEvents.size}")

            return Pair(todayEvents, tomorrowEvents)
        }

        private fun updateEnhancedWidgetWithMultipleEvents(
            views: RemoteViews,
            events: List<Triple<TimetableEvent, String, String>>,
            today: LocalDate
        ) {
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

            // Update header
            views.setTextViewText(R.id.widget_date, today.format(dateFormatter))

            // Set status based on first event
            val status = if (events.isNotEmpty()) events[0].third else "Free"
            views.setTextViewText(R.id.widget_status, status)

            // Update each event slot (up to 4)
            val eventContainerIds = listOf(
                R.id.widget_event1_container,
                R.id.widget_event2_container,
                R.id.widget_event3_container,
                R.id.widget_event4_container
            )

            for (i in 0 until 4) {
                val containerId = eventContainerIds[i]

                if (i < events.size) {
                    val (event, dayLabel, _) = events[i]
                    updateEnhancedWidgetEvent(views, i + 1, event, dayLabel)
                    views.setViewVisibility(containerId, View.VISIBLE)
                } else {
                    views.setViewVisibility(containerId, View.GONE)
                }
            }

            // If no events at all, show the first container with "no classes" message
            if (events.isEmpty()) {
                updateEnhancedWidgetEvent(views, 1, null, "Today")
                views.setViewVisibility(R.id.widget_event1_container, View.VISIBLE)
            }
        }

        private fun updateEnhancedWidgetEvent(
            views: RemoteViews,
            eventNumber: Int,
            event: TimetableEvent?,
            dayLabel: String
        ) {
            val dayId = when (eventNumber) {
                1 -> R.id.widget_event1_day
                2 -> R.id.widget_event2_day
                3 -> R.id.widget_event3_day
                4 -> R.id.widget_event4_day
                else -> R.id.widget_event1_day
            }

            val titleId = when (eventNumber) {
                1 -> R.id.widget_event1_title
                2 -> R.id.widget_event2_title
                3 -> R.id.widget_event3_title
                4 -> R.id.widget_event4_title
                else -> R.id.widget_event1_title
            }

            val timeId = when (eventNumber) {
                1 -> R.id.widget_event1_time
                2 -> R.id.widget_event2_time
                3 -> R.id.widget_event3_time
                4 -> R.id.widget_event4_time
                else -> R.id.widget_event1_time
            }

            val locationId = when (eventNumber) {
                1 -> R.id.widget_event1_location
                2 -> R.id.widget_event2_location
                3 -> R.id.widget_event3_location
                4 -> R.id.widget_event4_location
                else -> R.id.widget_event1_location
            }

            views.setTextViewText(dayId, dayLabel)

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
