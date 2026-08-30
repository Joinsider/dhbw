/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.testutil.fakes.FakeTimetableRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TimetableUseCasesTest {

    @Test
    fun getWeekTimetable_returnsTheRepositorysWeekAndRecordsTheOffset() = runTest {
        val week = FakeTimetableRepository.emptyWeek(3)
        val repo = FakeTimetableRepository(week = Outcome.Ok(week))

        val result = GetWeekTimetable(repo)(3)

        assertEquals(week, assertIs<Outcome.Ok<TimetableWeek>>(result).value)
        assertEquals(listOf(3), repo.requestedWeeks)
    }

    @Test
    fun awaitFullWeekTimetable_prefersTheFullWeekOverTheSkeleton() = runTest {
        val skeleton = FakeTimetableRepository.emptyWeek(0).copy(isPartial = true)
        val full = FakeTimetableRepository.emptyWeek(0).copy(isPartial = false)
        val repo = FakeTimetableRepository(week = Outcome.Ok(skeleton), fullWeek = Outcome.Ok(full))

        val result = AwaitFullWeekTimetable(repo)(0)

        assertEquals(full, assertIs<Outcome.Ok<TimetableWeek>>(result).value)
        assertEquals(listOf(0), repo.awaitedWeeks)
    }

    @Test
    fun refreshTimetable_bypassesTheCacheAndRecordsTheOffset() = runTest {
        val refreshed = FakeTimetableRepository.emptyWeek(2)
        val repo = FakeTimetableRepository(refreshed = Outcome.Ok(refreshed))

        val result = RefreshTimetable(repo)(2)

        assertEquals(refreshed, assertIs<Outcome.Ok<TimetableWeek>>(result).value)
        assertEquals(listOf(2), repo.refreshedWeeks)
    }

    @Test
    fun getCachedLectures_readsStraightFromTheRepositoryCache() = runTest {
        val lecture = Lecture(
            id = 1,
            shortName = "MA3",
            fullName = null,
            start = LocalDateTime(2026, 3, 2, 8, 0),
            end = LocalDateTime(2026, 3, 2, 9, 30),
            location = "Room 101",
            isTest = false
        )
        val repo = FakeTimetableRepository(cached = Outcome.Ok(listOf(lecture)))

        val result = GetCachedLectures(repo)(
            LocalDateTime(2026, 3, 2, 0, 0),
            LocalDateTime(2026, 3, 8, 23, 59)
        )

        assertEquals(listOf(lecture), assertIs<Outcome.Ok<List<Lecture>>>(result).value)
    }
}
