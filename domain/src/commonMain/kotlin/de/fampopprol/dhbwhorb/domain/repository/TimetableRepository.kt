/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import kotlinx.datetime.LocalDateTime

/**
 * The timetable, cache first.
 *
 * This interface holds the strategy that used to be spread over `LectureService` and
 * `DualisLectureService`: which week is asked for, whether the cache is fresh enough, and when a
 * background refresh is due.
 */
interface TimetableRepository {

    /**
     * The week [weekOffset] weeks from the current one (0 = this week, -1 = last week).
     *
     * Returns cached lectures when there are any, and refreshes in the background if they have
     * gone stale. With an empty cache it loads the weekly skeleton first — that result carries
     * [TimetableWeek.isPartial], and [awaitFullWeek] completes it.
     */
    suspend fun getWeek(weekOffset: Int): Outcome<TimetableWeek>

    /**
     * Wait for the complete version of a week whose [getWeek] result was partial.
     *
     * Separate call rather than a callback so the caller decides whether to keep waiting.
     */
    suspend fun awaitFullWeek(weekOffset: Int): Outcome<TimetableWeek>

    /** Refetch a week from Dualis, ignoring the cache. */
    suspend fun refreshWeek(weekOffset: Int): Outcome<TimetableWeek>

    /**
     * Lectures between [start] and [end] straight out of the local database.
     *
     * Never touches the network: the home-screen widget refreshes in the background, where there
     * may be no session and no connectivity, and it must not rebuild the auth stack to draw
     * itself.
     */
    suspend fun getCachedLectures(start: LocalDateTime, end: LocalDateTime): Outcome<List<Lecture>>
}
