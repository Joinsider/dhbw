package de.fampopprol.dhbwhorb.data.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.TimeZone
import androidx.core.content.edit

/**
 * Service to export and synchronize timetable events with a chosen system calendar,
 * and to export all events to an ICS file for sharing.
 *
 * This class uses SharedPreferences to store:
 *  - the chosen calendarId
 *  - a mapping between local event IDs and calendar provider event IDs
 *
 * Unique event identifier: "app:{packageName}#local:{localEventId}"
 * We embed this tag in the event DESCRIPTION to detect/update without duplicates.
 */
class CalendarExportService(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    data class CalendarInfo(
        val id: Long,
        val name: String,
        val accountName: String?,
        val ownerName: String?,
        val accountType: String?
    )

    data class SyncReport(
        val inserted: Int,
        val updated: Int,
        val reinsertedMissing: Int,
        val deletedOrphans: Int,
        val errors: Int
    )

    // Local representation to compute unique IDs and time millis
    private data class LocalEvent(
        val localId: String,
        val title: String,
        val startMillis: Long,
        val endMillis: Long,
        val location: String?,
        val description: String?
    )

    enum class DeleteReason {
        SUCCESS,
        NO_PERMISSIONS,
        NO_CALENDAR_SELECTED,
        NO_MATCHING_EVENTS
    }

    data class DeleteResult(val deleted: Int, val reason: DeleteReason)

    companion object {
        private const val TAG = "CalendarExportService"
        private const val PREFS_NAME = "CalendarExportPrefs"
        private const val KEY_CALENDAR_ID = "chosen_calendar_id"
        private const val KEY_MAPPING = "local_to_calendar_id_map" // JSON map<String, Long>
        private const val ICS_FILE_NAME = "timetable_export.ics"
    }

    /**
        Contract
        - Requires READ/WRITE_CALENDAR permissions for queries/updates.
        - Uses CalendarContract provider; chosen calendar must be writable.
        - Mapping is best-effort; we also verify via description tag.
     */

    // Permissions
    fun hasReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    fun requiredPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    // Calendar selection
    fun listDeviceCalendars(): List<CalendarInfo> {
        if (!hasReadPermission()) return emptyList()
        val result = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.OWNER_ACCOUNT,
            Calendars.ACCOUNT_TYPE,
            Calendars.VISIBLE,
            Calendars.CAN_MODIFY_TIME_ZONE,
            Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${Calendars.VISIBLE}=1"
        context.contentResolver.query(
            Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Calendars._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
            val accIdx = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME)
            val ownerIdx = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT)
            val typeIdx = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val name = cursor.getString(nameIdx)
                val acc = cursor.getString(accIdx)
                val owner = cursor.getString(ownerIdx)
                val type = cursor.getString(typeIdx)
                result.add(CalendarInfo(id, name ?: "", acc, owner, type))
            }
        }
        return result
    }

    fun setChosenCalendarId(calendarId: Long) {
        prefs.edit().putLong(KEY_CALENDAR_ID, calendarId).apply()
    }

    fun getChosenCalendarId(): Long? {
        if (!prefs.contains(KEY_CALENDAR_ID)) return null
        val id = prefs.getLong(KEY_CALENDAR_ID, -1L)
        return if (id > 0) id else null
    }

    // Mapping store
    private fun loadMapping(): MutableMap<String, Long> {
        val json = prefs.getString(KEY_MAPPING, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, Long>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse mapping, resetting", e)
            mutableMapOf()
        }
    }

    private fun saveMapping(map: Map<String, Long>) {
        prefs.edit { putString(KEY_MAPPING, gson.toJson(map)) }
    }

    private fun removeMappingFor(localId: String) {
        val map = loadMapping()
        if (map.remove(localId) != null) saveMapping(map)
    }

    // Public API: Export/Sync/Delete
    fun syncWithLocalTimetable(): SyncReport {
        val calendarId = getChosenCalendarId()
        if (calendarId == null) {
            Log.w(TAG, "No chosen calendarId; aborting sync")
            return SyncReport(0, 0, 0, 0, 1)
        }
        if (!hasReadPermission() || !hasWritePermission()) {
            Log.w(TAG, "Missing calendar permissions; aborting sync")
            return SyncReport(0, 0, 0, 0, 1)
        }

        val cache = TimetableCacheManager(context)
        val weeks = cache.getAllCachedWeeks()
        val localEvents = mutableMapOf<String, LocalEvent>()

        weeks.forEach { weekStart ->
            val days = cache.loadTimetable(weekStart) ?: return@forEach
            extractLocalEvents(days).forEach { e -> localEvents[e.localId] = e }
        }

        // In case current/next weeks not yet cached but present today, also try current and next week validity
        if (weeks.isEmpty()) {
            val today = LocalDate.now()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            cache.loadTimetable(weekStart)?.let { extractLocalEvents(it).forEach { e -> localEvents[e.localId] = e } }
            val nextWeek = weekStart.plusWeeks(1)
            cache.loadTimetable(nextWeek)?.let { extractLocalEvents(it).forEach { e -> localEvents[e.localId] = e } }
        }

        val mapping = loadMapping().toMutableMap()
        var inserted = 0
        var updated = 0
        var reinserted = 0
        var deletedOrphans = 0
        var errors = 0

        // 1) Upsert for existing or missing entries
        localEvents.values.forEach { local ->
            try {
                val tag = buildUniqueTag(local.localId)
                val existingEventId = mapping[local.localId]
                if (existingEventId != null) {
                    // Verify the event exists; update if exists else reinsert
                    val existingUri = ContentUris.withAppendedId(Events.CONTENT_URI, existingEventId)
                    val exists = context.contentResolver.query(existingUri, arrayOf(Events._ID), null, null, null)?.use { c -> c.moveToFirst() } ?: false
                    if (exists) {
                        if (updateEventIfChanged(existingEventId, calendarId, local, tag)) {
                            updated++
                        }
                    } else {
                        // Reinsert and update mapping
                        val newId = insertEvent(calendarId, local, tag)
                        if (newId != null) {
                            mapping[local.localId] = newId
                            reinserted++
                        }
                    }
                } else {
                    // Try to locate by description tag within time range
                    val foundId = findExistingEventByTag(calendarId, local, tag)
                    if (foundId != null) {
                        mapping[local.localId] = foundId
                        if (updateEventIfChanged(foundId, calendarId, local, tag)) {
                            updated++
                        }
                    } else {
                        val newId = insertEvent(calendarId, local, tag)
                        if (newId != null) {
                            mapping[local.localId] = newId
                            inserted++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing event ${local.localId}", e)
                errors++
            }
        }

        // 2) Remove orphan events from calendar (present in mapping but no longer in local)
        val localIds = localEvents.keys.toSet()
        val orphanIds = mapping.keys.filter { it !in localIds }
        orphanIds.forEach { localId ->
            val eventId = mapping[localId]
            if (eventId != null) {
                try {
                    val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) {
                        deletedOrphans++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete orphan calendar event $eventId for $localId", e)
                }
            }
            mapping.remove(localId)
        }

        saveMapping(mapping)
        return SyncReport(inserted, updated, reinserted, deletedOrphans, errors)
    }

    fun deleteAllExportedEvents(): Int {
        return deleteAllExportedEventsDetailed().deleted
    }

    fun deleteAllExportedEventsDetailed(): DeleteResult {
        // Require both read and write to safely enumerate and delete
        if (!hasReadPermission() || !hasWritePermission()) return DeleteResult(0, DeleteReason.NO_PERMISSIONS)
        val chosenCalendarId = getChosenCalendarId() ?: return DeleteResult(0, DeleteReason.NO_CALENDAR_SELECTED)

        val tagPrefix = buildUniqueTagPrefix()
        val uidPrefix = buildUidPrefix()
        var totalDeleted = 0
        val accountInfo = getAccountInfo(chosenCalendarId)

        // Pass 0: mapping-based deletes (with sync-adapter fallback)
        runCatching {
            val map = loadMapping()
            var passDeleted = 0
            map.values.forEach { eventId ->
                if (tryDeleteEvent(eventId, accountInfo)) passDeleted++
            }
            Log.d(TAG, "Delete Pass0 (mapping): deleted=$passDeleted")
            totalDeleted += passDeleted
        }.onFailure { Log.w(TAG, "Delete Pass0 failed", it) }

        val deletedFilter = "(${Events.DELETED} IS NULL OR ${Events.DELETED}!=1)"

        // Pass 1: bulk LIKE on chosen calendar (by DESC or UID)
        try {
            val selection = "${Events.CALENDAR_ID}=? AND $deletedFilter AND ((${Events.DESCRIPTION} LIKE ?) OR (${Events.UID_2445} LIKE ?) OR (${Events.CUSTOM_APP_PACKAGE}=?))"
            val args = arrayOf(chosenCalendarId.toString(), "%$tagPrefix%", "%$uidPrefix%", context.packageName)
            val rows = context.contentResolver.delete(Events.CONTENT_URI, selection, args)
            Log.d(TAG, "Delete Pass1 (bulk LIKE): rows=$rows")
            if (rows > 0) totalDeleted += rows
        } catch (e: Exception) {
            Log.w(TAG, "Bulk delete via LIKE failed; will fall back to manual enumeration", e)
        }

        // Pass 2: enumerate chosen calendar and delete tagged events (DESC or UID or CUSTOM_APP_PACKAGE)
        runCatching {
            val projection = arrayOf(Events._ID, Events.DESCRIPTION, Events.UID_2445, Events.CUSTOM_APP_PACKAGE, Events.DELETED)
            val selection = "${Events.CALENDAR_ID}=? AND $deletedFilter"
            val args = arrayOf(chosenCalendarId.toString())
            var passDeleted = 0
            context.contentResolver.query(Events.CONTENT_URI, projection, selection, args, null)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(Events._ID)
                val descIdx = c.getColumnIndexOrThrow(Events.DESCRIPTION)
                val uidIdx = c.getColumnIndexOrThrow(Events.UID_2445)
                val pkgIdx = c.getColumnIndexOrThrow(Events.CUSTOM_APP_PACKAGE)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val desc = c.getString(descIdx) ?: ""
                    val uid = c.getString(uidIdx) ?: ""
                    val pkg = c.getString(pkgIdx) ?: ""
                    if (desc.contains(tagPrefix) || uid.startsWith(uidPrefix) || pkg == context.packageName) {
                        if (tryDeleteEvent(id, accountInfo)) passDeleted++
                    }
                }
            }
            Log.d(TAG, "Delete Pass2 (enumerate chosen): deleted=$passDeleted")
            totalDeleted += passDeleted
        }.onFailure { Log.w(TAG, "Chosen calendar enumeration failed", it) }

        // Pass 3: enumerate all calendars fallback – always run to ensure full cleanup
        runCatching {
            val allCals = listDeviceCalendars()
            var passDeleted = 0
            allCals.forEach { cal ->
                val acc = getAccountInfo(cal.id)
                val projection = arrayOf(Events._ID, Events.DESCRIPTION, Events.UID_2445, Events.CUSTOM_APP_PACKAGE)
                val selection = "${Events.CALENDAR_ID}=? AND $deletedFilter"
                val args = arrayOf(cal.id.toString())
                context.contentResolver.query(Events.CONTENT_URI, projection, selection, args, null)?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(Events._ID)
                    val descIdx = c.getColumnIndexOrThrow(Events.DESCRIPTION)
                    val uidIdx = c.getColumnIndexOrThrow(Events.UID_2445)
                    val pkgIdx = c.getColumnIndexOrThrow(Events.CUSTOM_APP_PACKAGE)
                    while (c.moveToNext()) {
                        val id = c.getLong(idIdx)
                        val desc = c.getString(descIdx) ?: ""
                        val uid = c.getString(uidIdx) ?: ""
                        val pkg = c.getString(pkgIdx) ?: ""
                        if (desc.contains(tagPrefix) || uid.startsWith(uidPrefix) || pkg == context.packageName) {
                            if (tryDeleteEvent(id, acc)) passDeleted++
                        }
                    }
                }
            }
            Log.d(TAG, "Delete Pass3 (enumerate all): deleted=$passDeleted")
            totalDeleted += passDeleted
        }.onFailure { Log.w(TAG, "Delete Pass3 failed", it) }

        // Verification: only count non-deleted rows
        var remaining = 0
        runCatching {
            val allCals = listDeviceCalendars()
            allCals.forEach { cal ->
                val projection = arrayOf(Events.DESCRIPTION, Events.UID_2445, Events.CUSTOM_APP_PACKAGE, Events.DELETED)
                val selection = "${Events.CALENDAR_ID}=? AND $deletedFilter"
                val args = arrayOf(cal.id.toString())
                context.contentResolver.query(Events.CONTENT_URI, projection, selection, args, null)?.use { c ->
                    val descIdx = c.getColumnIndexOrThrow(Events.DESCRIPTION)
                    val uidIdx = c.getColumnIndexOrThrow(Events.UID_2445)
                    val pkgIdx = c.getColumnIndexOrThrow(Events.CUSTOM_APP_PACKAGE)
                    while (c.moveToNext()) {
                        val desc = c.getString(descIdx) ?: ""
                        val uid = c.getString(uidIdx) ?: ""
                        val pkg = c.getString(pkgIdx) ?: ""
                        if (desc.contains(tagPrefix) || uid.startsWith(uidPrefix) || pkg == context.packageName) remaining++
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Verification pass failed", it) }

        return if (totalDeleted > 0 && remaining == 0) {
            saveMapping(emptyMap())
            requestCalendarSyncFor(chosenCalendarId)
            Log.d(TAG, "Delete complete: totalDeleted=$totalDeleted (verified none remaining)")
            DeleteResult(totalDeleted, DeleteReason.SUCCESS)
        } else if (totalDeleted > 0 && remaining > 0) {
            Log.w(TAG, "Delete partial: deleted=$totalDeleted, remainingTagged=$remaining")
            saveMapping(emptyMap())
            requestCalendarSyncFor(chosenCalendarId)
            DeleteResult(totalDeleted, DeleteReason.SUCCESS)
        } else {
            Log.d(TAG, "Delete complete: no matching events found (remaining=$remaining)")
            DeleteResult(0, DeleteReason.NO_MATCHING_EVENTS)
        }
    }

    // ICS export
    fun buildIcsFileUri(): Uri? {
        val cache = TimetableCacheManager(context)
        val weeks = cache.getAllCachedWeeks()
        val allDays = mutableListOf<TimetableDay>()
        weeks.forEach { weekStart ->
            cache.loadTimetable(weekStart)?.let { allDays.addAll(it) }
        }
        if (allDays.isEmpty()) return null
        val events = extractLocalEvents(allDays)
        if (events.isEmpty()) return null

        val ics = buildIcsContent(events)
        return try {
            val outFile = File(context.cacheDir, ICS_FILE_NAME)
            FileOutputStream(outFile).use { it.write(ics.encodeToByteArray()) }
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                outFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ICS file", e)
            null
        }
    }

    fun buildShareIcsIntent(icsUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, icsUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // Helpers
    private fun extractLocalEvents(days: List<TimetableDay>): List<LocalEvent> {
        val out = mutableListOf<LocalEvent>()
        days.forEach { day ->
            val date = parseDate(day.date) ?: return@forEach
            day.events.forEach { ev ->
                val start = parseTime(ev.startTime)
                val end = parseTime(ev.endTime)
                if (start == null || end == null) return@forEach
                val startMillis = LocalDateTime.of(date, start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMillis = LocalDateTime.of(date, end).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val localId = computeLocalEventId(date, ev)
                val desc = buildString {
                    if (!ev.fullTitle.isNullOrBlank()) appendLine(ev.fullTitle)
                    if (!ev.courseCode.isNullOrBlank()) appendLine("Course: ${ev.courseCode}")
                    if (ev.lecturer.isNotBlank()) appendLine("Lecturer: ${ev.lecturer}")
                    appendLine(buildUniqueTag(localId))
                }.trim()
                out.add(
                    LocalEvent(
                        localId = localId,
                        title = ev.title,
                        startMillis = startMillis,
                        endMillis = endMillis,
                        location = ev.room.takeIf { it.isNotBlank() },
                        description = desc
                    )
                )
            }
        }
        return out
    }

    private fun parseDate(input: String): LocalDate? {
        val candidates = listOf("yyyy-MM-dd", "dd.MM.yyyy")
        for (p in candidates) {
            try {
                return LocalDate.parse(input, DateTimeFormatter.ofPattern(p))
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseTime(input: String): LocalTime? {
        return try { LocalTime.parse(input, DateTimeFormatter.ofPattern("HH:mm")) } catch (_: Exception) { null }
    }

    private fun computeLocalEventId(date: LocalDate, ev: TimetableEvent): String {
        // Stable ID: date|start|end|title|room (lowercased, trimmed)
        val norm = fun(s: String) = s.trim().lowercase()
        return listOf(
            date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            ev.startTime,
            ev.endTime,
            norm(ev.title),
            norm(ev.room)
        ).joinToString("|")
    }

    private fun buildUniqueTagPrefix(): String = "app:" + context.packageName + "#local:"
    private fun buildUniqueTag(localId: String): String = buildUniqueTagPrefix() + localId
    private fun buildUid(localId: String): String = context.packageName + ":" + localId
    private fun buildUidPrefix(): String = context.packageName + ":"

    private fun insertEvent(calendarId: Long, local: LocalEvent, tag: String): Long? {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, local.title)
            put(Events.DTSTART, local.startMillis)
            put(Events.DTEND, local.endMillis)
            put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(Events.DESCRIPTION, mergeDescriptionTag(local.description, tag))
            if (!local.location.isNullOrBlank()) put(Events.EVENT_LOCATION, local.location)
            put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
            // Additional tags for robust identification
            put(Events.UID_2445, buildUid(local.localId))
            put(Events.CUSTOM_APP_PACKAGE, context.packageName)
        }
        return try {
            val uri = context.contentResolver.insert(Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing calendar permission on insert", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Insert event failed", e)
            null
        }
    }

    private fun updateEventIfChanged(eventId: Long, calendarId: Long, local: LocalEvent, tag: String): Boolean {
        // Fetch current values
        val projection = arrayOf(
            Events.TITLE, Events.DTSTART, Events.DTEND, Events.EVENT_LOCATION, Events.DESCRIPTION
        )
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val current = context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                mapOf(
                    Events.TITLE to c.getString(0),
                    Events.DTSTART to c.getLong(1),
                    Events.DTEND to c.getLong(2),
                    Events.EVENT_LOCATION to c.getString(3),
                    Events.DESCRIPTION to c.getString(4)
                )
            } else null
        }
        if (current == null) return false

        val newDesc = mergeDescriptionTag(local.description, tag)
        val needsUpdate =
            (current[Events.TITLE] as String? != local.title) ||
            ((current[Events.DTSTART] as Long?) != local.startMillis) ||
            ((current[Events.DTEND] as Long?) != local.endMillis) ||
            ((current[Events.EVENT_LOCATION] as String?) != local.location) ||
            (current[Events.DESCRIPTION] as String? != newDesc)

        if (!needsUpdate) return false

        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, local.title)
            put(Events.DTSTART, local.startMillis)
            put(Events.DTEND, local.endMillis)
            put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(Events.DESCRIPTION, newDesc)
            if (!local.location.isNullOrBlank()) put(Events.EVENT_LOCATION, local.location) else putNull(Events.EVENT_LOCATION)
            // Ensure our tags remain on updates
            put(Events.UID_2445, buildUid(local.localId))
            put(Events.CUSTOM_APP_PACKAGE, context.packageName)
        }
        return try {
            val rows = context.contentResolver.update(uri, values, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Update failed for $eventId", e)
            false
        }
    }

    private fun mergeDescriptionTag(description: String?, tag: String): String {
        if (description.isNullOrBlank()) return tag
        return if (description.contains(tag)) description else description.trim() + "\n" + tag
    }

    private fun findExistingEventByTag(calendarId: Long, local: LocalEvent, tag: String): Long? {
        // Query instances around the event time window and scan description on Events table
        val start = local.startMillis - 15 * 60 * 1000 // 15 min margin
        val end = local.endMillis + 15 * 60 * 1000
        val builder = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, start)
        ContentUris.appendId(builder, end)
        val projection = arrayOf(Instances.EVENT_ID, Instances.CALENDAR_ID)
        val selection = "${Instances.CALENDAR_ID}=?"
        val args = arrayOf(calendarId.toString())
        return try {
            context.contentResolver.query(builder.build(), projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val evId = c.getLong(0)
                    // Fetch description from Events
                    val eUri = ContentUris.withAppendedId(Events.CONTENT_URI, evId)
                    context.contentResolver.query(eUri, arrayOf(Events.DESCRIPTION), null, null, null)?.use { ec ->
                        if (ec.moveToFirst()) {
                            val desc = ec.getString(0) ?: ""
                            if (desc.contains(tag)) return evId
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "findExistingEventByTag failed", e)
            null
        }
    }

    private fun buildIcsContent(events: List<LocalEvent>): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//${context.packageName}//Timetable Export//EN")
        val utcFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneId.of("UTC"))
        events.sortedBy { it.startMillis }.forEach { e ->
            val uid = context.packageName + ":" + e.localId
            val dtStart = utcFmt.format(java.time.Instant.ofEpochMilli(e.startMillis))
            val dtEnd = utcFmt.format(java.time.Instant.ofEpochMilli(e.endMillis))
            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:$uid")
            sb.appendLine("DTSTAMP:${utcFmt.format(java.time.Instant.now())}")
            sb.appendLine("DTSTART:$dtStart")
            sb.appendLine("DTEND:$dtEnd")
            sb.appendLine("SUMMARY:${escapeIcs(e.title)}")
            if (!e.location.isNullOrBlank()) sb.appendLine("LOCATION:${escapeIcs(e.location)}")
            if (!e.description.isNullOrBlank()) sb.appendLine("DESCRIPTION:${escapeIcs(e.description)}")
            sb.appendLine("END:VEVENT")
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun escapeIcs(text: String): String {
        // Escape commas, semicolons, and backslashes; replace newlines with \n
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }

    private fun getAccountInfo(calendarId: Long): Pair<String, String>? {
        val projection = arrayOf(Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE)
        val sel = "${Calendars._ID}=?"
        val args = arrayOf(calendarId.toString())
        return context.contentResolver.query(Calendars.CONTENT_URI, projection, sel, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                val type = c.getString(1)
                if (!name.isNullOrEmpty() && !type.isNullOrEmpty()) name to type else null
            } else null
        }
    }

    private fun buildSyncAdapterEventUri(baseId: Long, accountName: String, accountType: String): Uri {
        val base = ContentUris.withAppendedId(Events.CONTENT_URI, baseId).buildUpon()
        base.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        base.appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
        base.appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType)
        return base.build()
    }

    private fun tryDeleteEvent(eventId: Long, account: Pair<String, String>?): Boolean {
        // Strategy 1: Mark event as canceled instead of deleting (Google Calendar approach)
        try {
            val values = ContentValues().apply {
                put(Events.STATUS, Events.STATUS_CANCELED)
                put(Events.TITLE, "[CANCELED] " + (getEventTitle(eventId) ?: "Event"))
            }
            val uri = if (account != null) {
                val (accName, accType) = account
                buildSyncAdapterEventUri(eventId, accName, accType)
            } else {
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
            }
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) {
                Log.d(TAG, "Canceled event $eventId: rows=$rows")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cancel event failed for $eventId", e)
        }

        // Strategy 2: Try sync-adapter deletion
        if (account != null) {
            try {
                val (accName, accType) = account
                val uri = buildSyncAdapterEventUri(eventId, accName, accType)
                val rows = context.contentResolver.delete(uri, null, null)
                Log.d(TAG, "Sync-adapter delete for eventId=$eventId: rows=$rows")
                if (rows > 0) return true
            } catch (e: Exception) {
                Log.w(TAG, "Sync-adapter delete failed for $eventId", e)
            }
        }

        // Strategy 3: Direct deletion fallback
        try {
            val rows = context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
            Log.d(TAG, "Direct delete for eventId=$eventId: rows=$rows")
            return rows > 0
        } catch (e: Exception) {
            Log.w(TAG, "Direct delete failed for $eventId", e)
            return false
        }
    }

    private fun getEventTitle(eventId: Long): String? {
        return try {
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
            context.contentResolver.query(uri, arrayOf(Events.TITLE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun requestCalendarSyncFor(calendarId: Long) {
        val acc = getAccountInfo(calendarId) ?: return
        val (name, type) = acc
        try {
            val account = Account(name, type)
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
            Log.d(TAG, "Requested calendar sync for account=$name/$type")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request calendar sync", e)
        }
    }
}
