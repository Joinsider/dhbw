/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import java.text.SimpleDateFormat
import java.util.*

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String
)

class CalendarSyncManager(private val context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val TAG = "CalendarSyncManager"
        private const val CALENDAR_EVENT_TITLE_PREFIX = "[DHBW] "
        private const val CALENDAR_EVENT_DESCRIPTION = "Automatisch von DHBW Horb App synchronisiert"
        private val GERMAN_TIMEZONE = TimeZone.getTimeZone("Europe/Berlin")
    }

    /**
     * Check if the app has calendar permissions
     */
    fun hasCalendarPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get all available calendars on the device
     */
    fun getAvailableCalendars(): List<DeviceCalendar> {
        if (!hasCalendarPermissions()) return emptyList()

        val calendars = mutableListOf<DeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val calendarId = it.getLong(0)
                val displayName = it.getString(1) ?: ""
                val accountName = it.getString(2) ?: ""
                val accountType = it.getString(3) ?: ""

                calendars.add(DeviceCalendar(calendarId, displayName, accountName, accountType))
            }
        }

        return calendars
    }

    /**
     * Sync timetable events to the selected calendar
     */
    fun syncTimetableToCalendar(timetableDays: List<TimetableDay>, calendarId: Long): Boolean {
        if (!hasCalendarPermissions()) return false

        try {
            val existingEvents = getExistingDHBWEvents(calendarId)
            val newEvents = mutableSetOf<String>()
            var addedCount = 0
            var skippedCount = 0

            // Add new events if they don't already exist
            for (timetableDay in timetableDays) {
                for (event in timetableDay.events) {
                    val eventKey = generateEventKey(timetableDay, event)
                    newEvents.add(eventKey)

                    if (!existingEvents.contains(eventKey)) {
                        addEventToCalendar(timetableDay, event, calendarId)
                        addedCount++
                    } else {
                        skippedCount++
                    }
                }
            }

            // Remove events that are no longer in the timetable
            val eventsToRemove = existingEvents.keys - newEvents
            for (eventKey in eventsToRemove) {
                existingEvents[eventKey]?.let { eventId ->
                    deleteEventById(eventId)
                }
            }

            Log.d(TAG, "Calendar sync completed: $addedCount added, $skippedCount skipped, ${eventsToRemove.size} removed")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing calendar", e)
            return false
        }
    }

    /**
     * Get all existing DHBW events from the calendar
     */
    private fun getExistingDHBWEvents(calendarId: Long): MutableMap<String, Long> {
        val events = mutableMapOf<String, Long>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} LIKE ?"
        val selectionArgs = arrayOf(calendarId.toString(), "$CALENDAR_EVENT_TITLE_PREFIX%")

        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val eventId = it.getLong(0)
                val title = it.getString(1) ?: ""
                val startTime = it.getLong(2)
                val endTime = it.getLong(3)
                val description = it.getString(4) ?: ""

                // Generate key from existing event data
                val eventKey = generateEventKeyFromCalendarEvent(title, startTime, endTime, description)
                events[eventKey] = eventId
            }
        }

        return events
    }

    /**
     * Sync a single event to the calendar
     */
    private fun addEventToCalendar(timetableDay: TimetableDay, event: TimetableEvent, calendarId: Long) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

        try {
            val eventDate = dateFormat.parse(timetableDay.date) ?: return
            val startTime = timeFormat.parse(event.startTime) ?: return
            val endTime = timeFormat.parse(event.endTime) ?: return

            // Extract time components properly
            val startCalendarTemp = Calendar.getInstance(GERMAN_TIMEZONE).apply {
                time = startTime
            }
            val endCalendarTemp = Calendar.getInstance(GERMAN_TIMEZONE).apply {
                time = endTime
            }

            // Create calendar instances with German timezone
            val startCalendar = Calendar.getInstance(GERMAN_TIMEZONE).apply {
                time = eventDate
                set(Calendar.HOUR_OF_DAY, startCalendarTemp.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, startCalendarTemp.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val endCalendar = Calendar.getInstance(GERMAN_TIMEZONE).apply {
                time = eventDate
                set(Calendar.HOUR_OF_DAY, endCalendarTemp.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, endCalendarTemp.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startCalendar.timeInMillis)
                put(CalendarContract.Events.DTEND, endCalendar.timeInMillis)
                put(CalendarContract.Events.TITLE, "$CALENDAR_EVENT_TITLE_PREFIX${event.title}")
                put(CalendarContract.Events.DESCRIPTION, buildEventDescription(event))
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, GERMAN_TIMEZONE.id)
                put(CalendarContract.Events.EVENT_END_TIMEZONE, GERMAN_TIMEZONE.id)
                put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            }

            contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Build a detailed description for the calendar event
     */
    private fun buildEventDescription(event: TimetableEvent): String {
        return buildString {
            append(CALENDAR_EVENT_DESCRIPTION)
            append("\n\n")
            append("Veranstaltung: ${event.title}")
            if (event.lecturer.isNotBlank()) {
                append("\nDozent: ${event.lecturer}")
            }
            if (event.room.isNotBlank()) {
                append("\nRaum: ${event.room}")
            }
            append("\nZeit: ${event.startTime} - ${event.endTime}")
        }
    }

    /**
     * Remove all DHBW events from a specific calendar
     */
    fun removeDHBWEventsFromCalendar(calendarId: Long): Boolean {
        if (!hasCalendarPermissions()) return false

        try {
            deleteExistingDHBWEvents(calendarId)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Delete all existing DHBW events from the calendar
     */
    private fun deleteExistingDHBWEvents(calendarId: Long) {
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} LIKE ?"
        val selectionArgs = arrayOf(calendarId.toString(), "$CALENDAR_EVENT_TITLE_PREFIX%")

        contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            selection,
            selectionArgs
        )
    }

    /**
     * Generate a unique key for an event to detect duplicates
     */
    private fun generateEventKey(timetableDay: TimetableDay, event: TimetableEvent): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

        try {
            val eventDate = dateFormat.parse(timetableDay.date) ?: return ""
            val startTime = timeFormat.parse(event.startTime) ?: return ""
            val endTime = timeFormat.parse(event.endTime) ?: return ""

            val startCalendarTemp = Calendar.getInstance(GERMAN_TIMEZONE).apply {
                time = startTime
            }
            val endCalendarTemp = Calendar.getInstance(GERMAN_TIMEZONE).apply {
                time = endTime
            }

            val startCalendar = Calendar.getInstance(GERMAN_TIMEZONE).apply {
                time = eventDate
                set(Calendar.HOUR_OF_DAY, startCalendarTemp.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, startCalendarTemp.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val endCalendar = Calendar.getInstance(GERMAN_TIMEZONE).apply {
                time = eventDate
                set(Calendar.HOUR_OF_DAY, endCalendarTemp.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, endCalendarTemp.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return "${event.title}|${startCalendar.timeInMillis}|${endCalendar.timeInMillis}|${event.lecturer}|${event.room}"
        } catch (e: Exception) {
            Log.e(TAG, "Error generating event key", e)
            return ""
        }
    }

    /**
     * Generate event key from calendar event data
     */
    private fun generateEventKeyFromCalendarEvent(title: String, startTime: Long, endTime: Long, description: String): String {
        // Extract original title (remove prefix)
        val originalTitle = title.removePrefix(CALENDAR_EVENT_TITLE_PREFIX)

        // Extract lecturer and room from description
        val lines = description.split("\n")
        var lecturer = ""
        var room = ""

        for (line in lines) {
            when {
                line.startsWith("Dozent: ") -> lecturer = line.removePrefix("Dozent: ")
                line.startsWith("Raum: ") -> room = line.removePrefix("Raum: ")
            }
        }

        return "$originalTitle|$startTime|$endTime|$lecturer|$room"
    }

    /**
     * Delete an event by its ID
     */
    private fun deleteEventById(eventId: Long) {
        contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events._ID} = ?",
            arrayOf(eventId.toString())
        )
    }
}
