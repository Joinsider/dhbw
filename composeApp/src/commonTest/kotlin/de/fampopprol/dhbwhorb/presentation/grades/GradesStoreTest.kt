/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.grades

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades
import de.fampopprol.dhbwhorb.domain.usecase.GetGradesForSemester
import de.fampopprol.dhbwhorb.domain.usecase.GetSemesters
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.testutil.fakes.FakeGradeRepository
import de.fampopprol.dhbwhorb.testutil.fakes.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradesStoreTest {

    private val wise2526 = Semester(id = "000000015168000", name = "WiSe 2025/26")
    private val sose2026 = Semester(id = "000000015158000", name = "SoSe 2026")

    private fun grade(module: String, value: String?, credits: Double, semester: Semester) =
        GradeEntry(
            semesterId = semester.id,
            semesterName = semester.name,
            moduleNumber = module,
            moduleName = module,
            grade = value,
            credits = credits,
            status = "bestanden"
        )

    private fun store(
        grades: FakeGradeRepository,
        session: FakeSessionRepository = FakeSessionRepository(canAuthenticate = true)
    ): GradesStore {
        val getSemesters = GetSemesters(grades)
        val getForSemester = GetGradesForSemester(grades)
        return GradesStore(
            getSemesters = getSemesters,
            getGradesForSemester = getForSemester,
            getAllGrades = GetAllGrades(getSemesters, getForSemester),
            computeGpa = ComputeGpa(),
            sessionRepository = session,
            scope = TestScopes.immediate()
        )
    }

    @Test
    fun loading_startsWithTheCombinedView() = runTest {
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526, sose2026)),
            grades = Outcome.Ok(listOf(grade("T4INF", "1,3", 5.0, wise2526)))
        )
        val store = store(repository)

        store.dispatch(GradesIntent.Load)

        val state = store.state.value
        assertTrue(state.isShowingAllSemesters)
        assertEquals(listOf(wise2526, sose2026), state.semesters)
        assertFalse(state.isLoading)
        store.close()
    }

    @Test
    fun theCombinedView_averagesByCredits_andSetsOnlyTheOverallGpa() = runTest {
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526)),
            grades = Outcome.Ok(
                listOf(
                    grade("A", "1,0", 10.0, wise2526),
                    grade("B", "3,0", 10.0, wise2526)
                )
            )
        )
        val store = store(repository)

        store.dispatch(GradesIntent.Load)

        val state = store.state.value
        assertEquals(2.0, state.overallGpa)
        assertNull(state.semesterGpa, "Only one of the two averages may be set at a time")
        assertEquals(20.0, state.totalCreditsEarned)
        store.close()
    }

    @Test
    fun modulesWithoutANumericGrade_doNotCountTowardsTheAverage() = runTest {
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526)),
            grades = Outcome.Ok(
                listOf(
                    grade("A", "2,0", 5.0, wise2526),
                    // "b" is bestanden — passed, ungraded. It must not be read as a grade.
                    grade("B", "b", 5.0, wise2526),
                    grade("C", null, 5.0, wise2526)
                )
            )
        )
        val store = store(repository)

        store.dispatch(GradesIntent.Load)

        assertEquals(2.0, store.state.value.overallGpa)
        assertEquals(2, store.state.value.modulesCompleted, "'b' counts as completed, null does not")
        store.close()
    }

    @Test
    fun selectingASingleSemester_setsOnlyTheSemesterGpa() = runTest {
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526)),
            grades = Outcome.Ok(listOf(grade("A", "1,0", 5.0, wise2526)))
        )
        val store = store(repository)

        store.dispatch(GradesIntent.Load)
        store.dispatch(GradesIntent.SemesterSelected(wise2526))

        val state = store.state.value
        assertEquals(1.0, state.semesterGpa)
        assertNull(state.overallGpa)
        assertFalse(state.isShowingAllSemesters)
        store.close()
    }

    @Test
    fun refreshing_asksTheRepositoryToSkipItsCache() = runTest {
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526)),
            grades = Outcome.Ok(emptyList())
        )
        val store = store(repository)

        store.dispatch(GradesIntent.Load)
        repository.requests.clear()
        store.dispatch(GradesIntent.Refresh)

        assertTrue(
            repository.requests.all { it.second },
            "Pull-to-refresh has to bypass the one-hour cache, got ${repository.requests}"
        )
        store.close()
    }

    @Test
    fun reEnteringTheScreen_doesNotRefetch() = runTest {
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526)),
            grades = Outcome.Ok(listOf(grade("A", "1,0", 5.0, wise2526)))
        )
        val store = store(repository)

        store.dispatch(GradesIntent.EnsureLoaded)
        val afterFirst = repository.requests.size
        store.dispatch(GradesIntent.EnsureLoaded)

        // The screen re-enters the composition on every tab switch. Since the store outlives it,
        // coming back must cost nothing.
        assertEquals(afterFirst, repository.requests.size)
        store.close()
    }

    @Test
    fun reEnteringAfterAFailure_doesNotRetryOnItsOwn() = runTest {
        val repository = FakeGradeRepository(semesters = Outcome.Err(AppError.Offline))
        val store = store(repository)

        store.dispatch(GradesIntent.EnsureLoaded)
        store.dispatch(GradesIntent.EnsureLoaded)

        // Retrying belongs to the retry button, not to walking past the tab: an offline device
        // would otherwise hammer Dualis on every navigation.
        assertEquals(AppError.Offline, store.state.value.error)
        store.close()
    }

    @Test
    fun theRetryButton_loadsEvenAfterAFailure() = runTest {
        val repository = FakeGradeRepository(semesters = Outcome.Err(AppError.Offline))
        val store = store(repository)

        store.dispatch(GradesIntent.EnsureLoaded)
        repository.semesters = Outcome.Ok(listOf(wise2526))
        repository.grades = Outcome.Ok(listOf(grade("A", "1,0", 5.0, wise2526)))
        store.dispatch(GradesIntent.Load)

        assertNull(store.state.value.error)
        assertEquals(listOf(wise2526), store.state.value.semesters)
        store.close()
    }

    @Test
    fun withoutCredentials_itAsksForALoginRatherThanShowingAnError() = runTest {
        val repository = FakeGradeRepository()
        val store = store(repository, FakeSessionRepository(canAuthenticate = false))

        store.dispatch(GradesIntent.Load)

        val state = store.state.value
        assertTrue(state.requiresLogin)
        assertNull(state.error, "A missing session is not an error condition")
        assertFalse(state.isLoading, "Loading has to finish")
        store.close()
    }

    @Test
    fun anExpiredSessionThatCannotBeRenewed_sendsTheUserToTheLogin() = runTest {
        val repository = FakeGradeRepository(semesters = Outcome.Err(AppError.NoCredentials))
        val store = store(repository)

        store.dispatch(GradesIntent.Load)

        assertTrue(store.state.value.requiresLogin)
        store.close()
    }

    @Test
    fun beingOffline_isAnErrorWithARetry_notALoginPrompt() = runTest {
        val repository = FakeGradeRepository(semesters = Outcome.Err(AppError.Offline))
        val store = store(repository)

        store.dispatch(GradesIntent.Load)

        val state = store.state.value
        assertEquals(AppError.Offline, state.error)
        assertFalse(state.requiresLogin, "Logging in again would not help here")
        assertFalse(state.isLoading)
        store.close()
    }
}
