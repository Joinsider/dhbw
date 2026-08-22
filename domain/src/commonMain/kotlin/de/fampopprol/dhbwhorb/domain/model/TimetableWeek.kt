/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * One week of the timetable, together with what the caller needs to know about it.
 *
 * [isPartial] replaces the untyped `Pair<List<…>, Boolean>` the old staged fetch returned: true
 * means these lectures come from the weekly skeleton and lack lecturers and full course names,
 * so a second, complete load is still on its way.
 *
 * [fromCache] says the data came out of the local database rather than off the network — the UI
 * uses it to distinguish "nothing scheduled" from "we could not reach Dualis".
 */
data class TimetableWeek(
    val weekOffset: Int,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val lectures: List<Lecture>,
    val isPartial: Boolean = false,
    val fromCache: Boolean = false
)
