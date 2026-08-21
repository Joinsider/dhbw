/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.domain.repository.TimetableRepository
import kotlinx.datetime.LocalDateTime

/**
 * The timetable for one week, cache first.
 *
 * A partial result ([TimetableWeek.isPartial]) means the skeleton arrived and the full week is
 * still loading; follow it with [AwaitFullWeekTimetable].
 */
class GetWeekTimetable(private val repository: TimetableRepository) {
    suspend operator fun invoke(weekOffset: Int): Outcome<TimetableWeek> =
        repository.getWeek(weekOffset)
}

/** Wait for the complete week after [GetWeekTimetable] returned a skeleton. */
class AwaitFullWeekTimetable(private val repository: TimetableRepository) {
    suspend operator fun invoke(weekOffset: Int): Outcome<TimetableWeek> =
        repository.awaitFullWeek(weekOffset)
}

/** Pull-to-refresh: fetch the week from Dualis and ignore the cache. */
class RefreshTimetable(private val repository: TimetableRepository) {
    suspend operator fun invoke(weekOffset: Int): Outcome<TimetableWeek> =
        repository.refreshWeek(weekOffset)
}

/**
 * The lectures the home-screen widget draws, read straight from the local cache.
 *
 * The widget refreshes from a background worker with no guarantee of a session or a network, so
 * this deliberately never triggers a fetch. It replaces the arrangement where the widget's
 * repository interface was implemented by the network-backed lecture service.
 */
class GetCachedLectures(private val repository: TimetableRepository) {
    suspend operator fun invoke(start: LocalDateTime, end: LocalDateTime): Outcome<List<Lecture>> =
        repository.getCachedLectures(start, end)
}
