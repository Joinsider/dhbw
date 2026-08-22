/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.timetable

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.domain.model.Lecture
import kotlinx.datetime.LocalDateTime

/**
 * One week of the pager.
 *
 * [isPartial] means the weekly skeleton arrived and the complete week — lecturers, full course
 * names — is still on its way. The UI shows the skeleton rather than a spinner.
 */
data class WeekState(
    val lectures: List<Lecture> = emptyList(),
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPartial: Boolean = false,
    val error: AppError? = null,
    /**
     * Whether a fetch for this week has ever come back.
     *
     * An explicit flag rather than `lectures.isNotEmpty()`: a semester-break week legitimately has
     * no lectures, and without this the pager would refetch it every time it settled.
     */
    val hasLoaded: Boolean = false
) {
    /** True before anything has been asked for this week. */
    val isUntouched: Boolean
        get() = !hasLoaded && !isLoading && !isRefreshing && error == null
}

/**
 * The timetable screen.
 *
 * Weeks are a map rather than a single "current week" plus a cache on the side: the pager can show
 * three of them at once, and each has its own loading state. Reading week -3's spinner while
 * week 0 refreshes used to require two collections that could disagree.
 */
data class TimetableState(
    val currentWeekOffset: Int = 0,
    val weeks: Map<Int, WeekState> = emptyMap(),
    /** The lecture whose detail dialog is open. */
    val selectedLecture: Lecture? = null
) {
    fun week(offset: Int): WeekState = weeks[offset] ?: WeekState()

    val currentWeek: WeekState get() = week(currentWeekOffset)
}

sealed interface TimetableIntent {
    /** The pager settled on a page. */
    data class WeekFocused(val offset: Int) : TimetableIntent

    /** Load a week if it is not loaded yet. Dispatched by the store itself after focusing. */
    data class LoadWeek(val offset: Int) : TimetableIntent

    /** Pull-to-refresh: fetch from Dualis regardless of the cache. */
    data class Refresh(val offset: Int) : TimetableIntent

    data class LectureOpened(val lecture: Lecture) : TimetableIntent
    data object LectureDismissed : TimetableIntent
}

sealed interface TimetableMsg {
    data class WeekFocused(
        val offset: Int,
        val start: LocalDateTime,
        val end: LocalDateTime
    ) : TimetableMsg

    data class LoadStarted(val offset: Int, val isRefresh: Boolean) : TimetableMsg
    data class WeekLoaded(
        val offset: Int,
        val lectures: List<Lecture>,
        val isPartial: Boolean
    ) : TimetableMsg
    data class LoadFailed(val offset: Int, val error: AppError) : TimetableMsg
    data class LoadFinished(val offset: Int) : TimetableMsg

    data class LectureSelected(val lecture: Lecture?) : TimetableMsg
}

sealed interface TimetableEffect {
    /**
     * A refresh the user asked for failed while cached lectures are still on screen.
     *
     * One-shot rather than state: the week is still readable, so this belongs in a snackbar and
     * must not reappear on the next recomposition.
     */
    data class RefreshFailed(val error: AppError) : TimetableEffect
}
