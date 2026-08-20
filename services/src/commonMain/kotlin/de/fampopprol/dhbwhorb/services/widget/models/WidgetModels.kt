// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Platform-agnostic state for a single class/lecture shown inside a widget.
 *
 * @property name          Full subject name (falls back to short name when unavailable).
 * @property shortName     Abbreviated subject name for compact layouts.
 * @property formattedStartTime  Start time string in "HH:mm" format, e.g. "08:15".
 * @property formattedEndTime    End time string in "HH:mm" format, e.g. "11:30".
 * @property location      Room / building label, e.g. "HOR-231".
 * @property isTest        True when the entry is an exam / test rather than a regular lecture.
 * @property isOngoing     True when the current time lies between [startTime] and [endTime] (exclusive).
 * @property startTime     Raw start timestamp kept for time-until calculations in native layers.
 * @property endTime       Raw end timestamp kept for time-remaining calculations in native layers.
 */
data class WidgetClassState(
    val name: String,
    val shortName: String,
    val formattedStartTime: String,
    val formattedEndTime: String,
    val location: String,
    val isTest: Boolean,
    val isOngoing: Boolean,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
)

/**
 * One calendar day worth of widget data.
 *
 * @property date    The calendar date this summary belongs to.
 * @property classes Ordered list of classes for [date] (ascending by start time).
 *                   Never empty – the use case guarantees at least one entry.
 */
data class WidgetDayState(
    val date: LocalDate,
    val classes: List<WidgetClassState>,
)

/**
 * Result type for the "Up Next" widget variant.
 */
sealed class WidgetUpNextState {
    /** A lecture is currently in progress. */
    data class CurrentlyRunning(val lecture: WidgetClassState) : WidgetUpNextState()

    /** No lecture is running; [lecture] is the next one starting today. */
    data class ComingUp(val lecture: WidgetClassState) : WidgetUpNextState()

    /** All lectures for today are over (or there are none). */
    data object NoMoreClassesToday : WidgetUpNextState()
}

