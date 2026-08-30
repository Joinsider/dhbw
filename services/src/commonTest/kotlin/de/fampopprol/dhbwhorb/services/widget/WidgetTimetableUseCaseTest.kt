// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.domain.repository.TimetableRepository
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Fake repository
// ---------------------------------------------------------------------------

/**
 * In-memory [TimetableRepository] for tests.
 *
 * Only [getCachedLectures] is implemented: the widget must never take any other route, and a
 * fetch here would fail the test loudly rather than quietly hitting the network.
 */
private class FakeWidgetLectureRepository(
    private val lectures: List<Lecture> = emptyList(),
) : TimetableRepository {

    override suspend fun getCachedLectures(
        start: LocalDateTime,
        end: LocalDateTime,
    ): Outcome<List<Lecture>> =
        Outcome.Ok(lectures.filter { it.start >= start && it.start <= end })

    override suspend fun getWeek(weekOffset: Int): Outcome<TimetableWeek> =
        error("The widget must not fetch a week")

    override suspend fun awaitFullWeek(weekOffset: Int): Outcome<TimetableWeek> =
        error("The widget must not fetch a week")

    override suspend fun refreshWeek(weekOffset: Int): Outcome<TimetableWeek> =
        error("The widget must not refresh a week")
}

// ---------------------------------------------------------------------------
// Builder helpers
// ---------------------------------------------------------------------------

private fun lecture(
    id: Long,
    date: LocalDate,
    startHour: Int,
    startMinute: Int = 0,
    endHour: Int,
    endMinute: Int = 0,
    shortName: String = "SUBJ",
    fullName: String? = null,
    location: String = "HOR-101",
    isTest: Boolean = false,
): Lecture = Lecture(
    id = id,
    shortName = shortName,
    fullName = fullName,
    start = LocalDateTime(date.year, date.month, date.day, startHour, startMinute),
    end = LocalDateTime(date.year, date.month, date.day, endHour, endMinute),
    location = location,
    isTest = isTest,
)

private fun fixedClock(dt: LocalDateTime): () -> LocalDateTime = { dt }

// Convenience dates – these are all Wednesdays / weekdays so real weeks align
private val TODAY = LocalDate(2026, 3, 11)   // Wednesday
private val TOMORROW = LocalDate(2026, 3, 12)
private val DAY_AFTER = LocalDate(2026, 3, 13)

// ---------------------------------------------------------------------------
// getUpNextState
// ---------------------------------------------------------------------------

class UpNextStateTest {

    @Test
    fun `no classes today returns NoMoreClassesToday`() = runTest {
        val useCase = WidgetTimetableUseCase(
            repository = FakeWidgetLectureRepository(),
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 10, 0)),
        )
        assertIs<WidgetUpNextState.NoMoreClassesToday>(useCase.getUpNextState())
    }

    @Test
    fun `all classes already finished returns NoMoreClassesToday`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 8, endHour = 10))
        )
        // Current time is 11:00 – after the lecture ended at 10:00
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 11, 0)),
        )
        assertIs<WidgetUpNextState.NoMoreClassesToday>(useCase.getUpNextState())
    }

    @Test
    fun `class currently running returns CurrentlyRunning`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 8, endHour = 10))
        )
        // Current time is 09:00 – inside the 08:00–10:00 block
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 9, 0)),
        )
        val state = useCase.getUpNextState()
        assertIs<WidgetUpNextState.CurrentlyRunning>(state)
        assertTrue(state.lecture.isOngoing)
        assertEquals("08:00", state.lecture.formattedStartTime)
        assertEquals("10:00", state.lecture.formattedEndTime)
    }

    @Test
    fun `class starting exactly now is treated as running`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 10, endHour = 12))
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 10, 0)),
        )
        assertIs<WidgetUpNextState.CurrentlyRunning>(useCase.getUpNextState())
    }

    @Test
    fun `next class not yet started returns ComingUp`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 14, endHour = 16))
        )
        // Current time is 13:00 – before the lecture
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 13, 0)),
        )
        val state = useCase.getUpNextState()
        assertIs<WidgetUpNextState.ComingUp>(state)
        assertEquals("14:00", state.lecture.formattedStartTime)
    }

    @Test
    fun `running class preferred over upcoming when both exist`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TODAY, startHour = 8, endHour = 10, shortName = "RUNNING"),
                lecture(2, TODAY, startHour = 11, endHour = 13, shortName = "UPCOMING"),
            )
        )
        // Now = 09:00 – first lecture is running
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 9, 0)),
        )
        val state = useCase.getUpNextState()
        assertIs<WidgetUpNextState.CurrentlyRunning>(state)
        assertEquals("RUNNING", state.lecture.shortName)
    }

    @Test
    fun `class ending exactly now is not treated as running`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 8, endHour = 10))
        )
        // endTime == now → lecture is over (endTime > now is false)
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 10, 0)),
        )
        assertIs<WidgetUpNextState.NoMoreClassesToday>(useCase.getUpNextState())
    }

    @Test
    fun `formatted time zero-pads single digit hours and minutes`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 8, startMinute = 5, endHour = 9, endMinute = 30))
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 8, 10)),
        )
        val state = useCase.getUpNextState() as WidgetUpNextState.CurrentlyRunning
        assertEquals("08:05", state.lecture.formattedStartTime)
        assertEquals("09:30", state.lecture.formattedEndTime)
    }

    @Test
    fun `full subject name preferred over short name`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 10, endHour = 12, shortName = "MATH", fullName = "Mathematik 1"))
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 10, 30)),
        )
        val state = useCase.getUpNextState() as WidgetUpNextState.CurrentlyRunning
        assertEquals("Mathematik 1", state.lecture.name)
        assertEquals("MATH", state.lecture.shortName)
    }
}

// ---------------------------------------------------------------------------
// getDaySummaryState
// ---------------------------------------------------------------------------

class DaySummaryStateTest {

    @Test
    fun `today has classes returns today`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 8, endHour = 10))
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val state = assertNotNull(useCase.getDaySummaryState())
        assertEquals(TODAY, state.date)
        assertEquals(1, state.classes.size)
    }

    @Test
    fun `today empty fallback to tomorrow`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TOMORROW, startHour = 10, endHour = 12))
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val state = assertNotNull(useCase.getDaySummaryState())
        assertEquals(TOMORROW, state.date)
    }

    @Test
    fun `today and tomorrow empty falls back to day after`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, DAY_AFTER, startHour = 8, endHour = 10))
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val state = assertNotNull(useCase.getDaySummaryState())
        assertEquals(DAY_AFTER, state.date)
    }

    @Test
    fun `no classes within look-ahead returns null`() = runTest {
        val useCase = WidgetTimetableUseCase(
            repository = FakeWidgetLectureRepository(),
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        assertNull(useCase.getDaySummaryState())
    }

    @Test
    fun `classes are sorted ascending by start time`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TODAY, startHour = 14, endHour = 16, shortName = "LATE"),
                lecture(2, TODAY, startHour = 8, endHour = 10, shortName = "EARLY"),
            )
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val state = assertNotNull(useCase.getDaySummaryState())
        assertEquals("EARLY", state.classes[0].shortName)
        assertEquals("LATE", state.classes[1].shortName)
    }

    @Test
    fun `multiple classes on fallback day all returned`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TOMORROW, startHour = 8, endHour = 10, shortName = "A"),
                lecture(2, TOMORROW, startHour = 11, endHour = 13, shortName = "B"),
                lecture(3, TOMORROW, startHour = 14, endHour = 16, shortName = "C"),
            )
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val state = assertNotNull(useCase.getDaySummaryState())
        assertEquals(3, state.classes.size)
    }

    @Test
    fun `today has only finished classes, skips to tomorrow`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TODAY, startHour = 8, endHour = 10),
                lecture(2, TOMORROW, startHour = 9, endHour = 11),
            )
        )
        // Now = 12:00 – today's only class ended two hours ago.
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 12, 0)),
        )
        val state = assertNotNull(useCase.getDaySummaryState())
        assertEquals(TOMORROW, state.date)
    }

    @Test
    fun `a cache read failure is treated as an empty day rather than crashing`() = runTest {
        val repo = object : TimetableRepository {
            override suspend fun getCachedLectures(start: LocalDateTime, end: LocalDateTime) =
                Outcome.Err(de.fampopprol.dhbwhorb.core.error.AppError.Storage("cache unavailable"))
            override suspend fun getWeek(weekOffset: Int) = error("must not be called")
            override suspend fun awaitFullWeek(weekOffset: Int) = error("must not be called")
            override suspend fun refreshWeek(weekOffset: Int) = error("must not be called")
        }
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )

        assertNull(useCase.getDaySummaryState())
    }

    @Test
    fun `isOngoing flag set correctly for running class in day summary`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TODAY, startHour = 8, endHour = 10, shortName = "RUNNING"),
                lecture(2, TODAY, startHour = 11, endHour = 13, shortName = "FUTURE"),
            )
        )
        // Now = 09:00, inside the first block
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 9, 0)),
        )
        val state = assertNotNull(useCase.getDaySummaryState())
        assertTrue(state.classes[0].isOngoing)
        assertTrue(!state.classes[1].isOngoing)
    }
}

// ---------------------------------------------------------------------------
// getMultiDaySummaryState
// ---------------------------------------------------------------------------

class MultiDaySummaryStateTest {

    @Test
    fun `today and tomorrow both have classes returns both`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TODAY, startHour = 8, endHour = 10),
                lecture(2, TOMORROW, startHour = 10, endHour = 12),
            )
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val states = useCase.getMultiDaySummaryState()
        assertEquals(2, states.size)
        assertEquals(TODAY, states[0].date)
        assertEquals(TOMORROW, states[1].date)
    }

    @Test
    fun `today empty first day falls back tomorrow second day falls back day after`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TOMORROW, startHour = 8, endHour = 10),
                lecture(2, DAY_AFTER, startHour = 10, endHour = 12),
            )
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val states = useCase.getMultiDaySummaryState()
        assertEquals(2, states.size)
        assertEquals(TOMORROW, states[0].date)
        assertEquals(DAY_AFTER, states[1].date)
    }

    @Test
    fun `only one day with classes returns single entry`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 8, endHour = 10))
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val states = useCase.getMultiDaySummaryState()
        assertEquals(1, states.size)
        assertEquals(TODAY, states[0].date)
    }

    @Test
    fun `no classes at all returns empty list`() = runTest {
        val useCase = WidgetTimetableUseCase(
            repository = FakeWidgetLectureRepository(),
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        assertTrue(useCase.getMultiDaySummaryState().isEmpty())
    }

    @Test
    fun `second day is strictly after first day`() = runTest {
        // Both today and tomorrow have lectures – second entry must be tomorrow, not today again
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TODAY, startHour = 8, endHour = 10),
                lecture(2, TODAY, startHour = 14, endHour = 16),
                lecture(3, TOMORROW, startHour = 9, endHour = 11),
            )
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val states = useCase.getMultiDaySummaryState()
        assertEquals(2, states.size)
        assertTrue(states[1].date > states[0].date)
    }

    @Test
    fun `each day contains correct number of classes`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(
                lecture(1, TODAY, startHour = 8, endHour = 10),
                lecture(2, TODAY, startHour = 11, endHour = 13),
                lecture(3, TOMORROW, startHour = 14, endHour = 16),
            )
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val states = useCase.getMultiDaySummaryState()
        assertEquals(2, states[0].classes.size)
        assertEquals(1, states[1].classes.size)
    }

    @Test
    fun `exam flag propagated correctly into WidgetClassState`() = runTest {
        val repo = FakeWidgetLectureRepository(
            listOf(lecture(1, TODAY, startHour = 8, endHour = 10, isTest = true))
        )
        val useCase = WidgetTimetableUseCase(
            repository = repo,
            clock = fixedClock(LocalDateTime(TODAY.year, TODAY.month, TODAY.day, 7, 0)),
        )
        val state = useCase.getMultiDaySummaryState()
        assertTrue(state.first().classes.first().isTest)
    }
}

