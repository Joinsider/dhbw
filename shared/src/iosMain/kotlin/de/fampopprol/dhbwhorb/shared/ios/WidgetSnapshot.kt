/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import de.fampopprol.dhbwhorb.services.widget.WidgetTimetableUseCase
import de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetDayState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970

private const val TAG = "WidgetSnapshot"

/**
 * One lecture, in types Objective-C can carry.
 *
 * The point of this file is that it is the *only* description of the widget's data. Until P8 the
 * same nine fields existed twice — once as `WidgetClassDto` in Kotlin and once as
 * `WidgetClassInfo` in Swift, kept in step by hand across a JSON round-trip through
 * `NSUserDefaults`. Renaming a field on one side left the other decoding nothing, and a widget
 * that decodes nothing looks exactly like a widget with no lectures. Since P6 the database lives
 * in the App Group container, so the extension can read it directly and the copy can go.
 *
 * The constructor is public only so the SwiftUI previews in `TimetableWidget.swift` can build
 * sample lectures; nothing in the app calls it.
 *
 * @property start Wall-clock start read in the device's time zone, for WidgetKit's refresh timing.
 */
class WidgetLecture(
    val name: String,
    val shortName: String,
    val startText: String,
    val endText: String,
    val location: String,
    val isTest: Boolean,
    val isOngoing: Boolean,
    val start: NSDate,
    val end: NSDate,
)

/** One day of the timetable. [date] is midnight, so `Calendar.isDateInToday` works on it. */
class WidgetDay(
    val date: NSDate,
    val lectures: List<WidgetLecture>,
)

/**
 * Everything the three widget sizes render.
 *
 * [upNext] is `null` when today is over or empty — the third case of `WidgetUpNextState`, which
 * carries no lecture. [upNextIsRunning] separates the other two.
 */
class WidgetSnapshot(
    val upNext: WidgetLecture?,
    val upNextIsRunning: Boolean,
    val days: List<WidgetDay>,
) {
    val isEmpty: Boolean get() = upNext == null && days.isEmpty()
}

/**
 * The widget extension's door into Kotlin — the counterpart of [SharedApp] for a process that has
 * no UI and no session.
 *
 * The extension starts the same object graph the app does. That is more than it needs, but the
 * alternative is a second, smaller graph that would have to be kept in step with the real one,
 * and P3 is a long lesson in what happens to two things that know the same thing. Reads go
 * through [WidgetTimetableUseCase], which only touches the local cache: no network, no login.
 */
object WidgetSnapshotProvider {

    private val scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    /**
     * Reads the current snapshot and hands it to [onResult].
     *
     * The callback arrives on a background thread. WidgetKit's `getTimeline` completion accepts
     * that, and depending on the extension's run loop instead would mean trusting that something
     * pumps it — the reason this does not use `Dispatchers.Main` the way the in-app bridges do.
     *
     * A failure yields an empty snapshot rather than an error: a widget has no way to show one,
     * which is the same reason [WidgetTimetableUseCase] turns a cache error into an empty day.
     */
    fun load(onResult: (WidgetSnapshot) -> Unit) {
        scope.launch {
            val snapshot = try {
                val useCase: WidgetTimetableUseCase = startSharedKoin().get()
                buildSnapshot(useCase.getUpNextState(), useCase.getMultiDaySummaryState())
            } catch (e: Exception) {
                Napier.e("Widget snapshot failed: ${e.message}", e, tag = TAG)
                WidgetSnapshot(upNext = null, upNextIsRunning = false, days = emptyList())
            }
            onResult(snapshot)
        }
    }
}

private fun buildSnapshot(upNext: WidgetUpNextState, days: List<WidgetDayState>) = WidgetSnapshot(
    upNext = when (upNext) {
        is WidgetUpNextState.CurrentlyRunning -> upNext.lecture.toWidgetLecture()
        is WidgetUpNextState.ComingUp -> upNext.lecture.toWidgetLecture()
        WidgetUpNextState.NoMoreClassesToday -> null
    },
    upNextIsRunning = upNext is WidgetUpNextState.CurrentlyRunning,
    days = days.map { day ->
        WidgetDay(
            date = day.date.atMidnight().toNSDate(),
            lectures = day.classes.map { it.toWidgetLecture() },
        )
    },
)

private fun WidgetClassState.toWidgetLecture() = WidgetLecture(
    name = name,
    shortName = shortName,
    startText = formattedStartTime,
    endText = formattedEndTime,
    location = location,
    isTest = isTest,
    isOngoing = isOngoing,
    start = startTime.toNSDate(),
    end = endTime.toNSDate(),
)

private fun LocalDate.atMidnight(): LocalDateTime = atTime(LocalTime(0, 0))

/**
 * A wall-clock time read in the device's time zone.
 *
 * Dualis states lecture times in local time and the phone is in that zone, so this is the same
 * "10:00" the portal shows — the same reasoning as `Kotlinx_datetimeLocalDateTime.foundationDate`
 * on the app side.
 */
private fun LocalDateTime.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(
    toInstant(TimeZone.currentSystemDefault()).epochSeconds.toDouble(),
)
