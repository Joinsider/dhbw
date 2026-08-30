/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.grades

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.ExamResult
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.ModuleAttempt
import de.fampopprol.dhbwhorb.domain.model.ModuleResultDetails
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades
import de.fampopprol.dhbwhorb.domain.usecase.GetGradesForSemester
import de.fampopprol.dhbwhorb.domain.usecase.GetModuleDetails
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

    private fun grade(
        module: String,
        value: String?,
        credits: Double,
        semester: Semester,
        status: String = "bestanden",
        resultId: String? = null
    ) = GradeEntry(
        semesterId = semester.id,
        semesterName = semester.name,
        moduleNumber = module,
        moduleName = module,
        grade = value,
        credits = credits,
        status = status,
        resultId = resultId
    )

    private fun store(
        grades: FakeGradeRepository,
        session: FakeSessionRepository = FakeSessionRepository(canAuthenticate = true)
    ): GradesStore {
        val getSemesters = GetSemesters(grades)
        return GradesStore(
            getAllGrades = GetAllGrades(getSemesters, GetGradesForSemester(grades)),
            getModuleDetails = GetModuleDetails(grades),
            computeGpa = ComputeGpa(),
            sessionRepository = session,
            scope = TestScopes.immediate()
        )
    }

    @Test
    fun loading_bringsEverySemestersGrades() = runTest {
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526, sose2026)),
            grades = Outcome.Ok(listOf(grade("T4INF", "1,3", 5.0, wise2526)))
        )
        val store = store(repository)

        store.dispatch(GradesIntent.Load)

        val state = store.state.value
        assertTrue(state.hasLoaded)
        assertFalse(state.isLoading)
        assertEquals(2, repository.requests.size, "One request per semester")
        store.close()
    }

    @Test
    fun semesters_areOrderedByWhenTheyHappened_notByName() = runTest {
        // Alphabetically this is WiSe 2024/25, WiSe 2025/26, SoSe 2025 — which is how the screen
        // used to read, with the summer term at the bottom.
        val wise2425 = Semester(id = "1", name = "WiSe 2024/25")
        val sose2025 = Semester(id = "2", name = "SoSe 2025")
        val wise2526 = Semester(id = "3", name = "WiSe 2025/26")
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526, wise2425, sose2025)),
            gradesBySemester = mapOf(
                wise2425.id to listOf(grade("B", "1,0", 5.0, wise2425)),
                sose2025.id to listOf(grade("A", "2,0", 5.0, sose2025)),
                wise2526.id to listOf(grade("C", "3,0", 5.0, wise2526))
            )
        )
        val store = store(repository)

        store.dispatch(GradesIntent.Load)

        assertEquals(
            listOf("WiSe 2024/25", "SoSe 2025", "WiSe 2025/26"),
            store.state.value.sections.map { it.semesterName }
        )
        store.close()
    }

    @Test
    fun theOverallAverage_isWeightedByCredits() = runTest {
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
    fun aRepeatedModuleIsCountedOnce() = runTest {
        // Dualis lists each attempt under the semester it happened in, so a module that was
        // failed and repeated arrives twice. Counting both credited the failed attempt's credits
        // and pulled the average towards a grade the transcript no longer holds.
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526)),
            grades = Outcome.Ok(
                listOf(
                    grade("T4INF2001", "4,6", 6.0, wise2526, status = "unvollständig"),
                    grade("T4INF2001", "3,2", 6.0, sose2026, status = "bestanden (Wh.)")
                )
            )
        )
        val store = store(repository)

        store.dispatch(GradesIntent.Load)

        val state = store.state.value
        assertEquals(3.2, state.overallGpa!!, absoluteTolerance = 1e-9)
        assertEquals(6.0, state.totalCreditsEarned)
        assertEquals(1, state.modulesCompleted)
        assertEquals(2, state.grades.size, "both attempts stay visible in the semester list")
        store.close()
    }

    @Test
    fun openingAModule_loadsTheExamsBehindIt() = runTest {
        val entry = grade("T4INF2001", "3,2", 6.0, sose2026, resultId = "394485214191519")
        val details = ModuleResultDetails(
            moduleNumber = "T4INF2001",
            moduleName = "Mathematik III",
            semesterName = "WiSe 2025/26",
            attempts = listOf(
                ModuleAttempt(
                    number = 2,
                    exams = listOf(
                        ExamResult("T4INF2001.1 Angewandte Mathematik", "WiSe 2025/26", "Klausur", 100.0, null, "3,6"),
                        ExamResult("T4INF2001.2 Statistik", "SoSe 2026", "Klausur", 100.0, null, "2,8")
                    ),
                    result = "3,2 bestanden (Wh.)"
                )
            ),
            units = emptyList()
        )
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(sose2026)),
            grades = Outcome.Ok(listOf(entry)),
            moduleDetails = Outcome.Ok(details)
        )
        val store = store(repository)
        store.dispatch(GradesIntent.Load)

        store.dispatch(GradesIntent.ModuleOpened(entry))

        val state = store.state.value
        assertEquals(listOf("394485214191519"), repository.detailRequests)
        assertEquals(details, state.moduleDetails)
        assertFalse(state.isLoadingDetails)
        assertEquals(listOf("3,6", "2,8"), state.moduleDetails?.attempts?.single()?.exams?.map { it.grade })
        store.close()
    }

    @Test
    fun aModuleWithoutADetailsLinkOpensWithoutWaiting() = runTest {
        // No id means no page to fetch; the sheet still opens, on what the list already knows.
        val entry = grade("T4INF2006", null, 5.0, sose2026, status = "offen")
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(sose2026)),
            grades = Outcome.Ok(listOf(entry))
        )
        val store = store(repository)
        store.dispatch(GradesIntent.Load)

        store.dispatch(GradesIntent.ModuleOpened(entry))

        val state = store.state.value
        assertEquals(entry, state.selectedModule)
        assertFalse(state.isLoadingDetails, "nothing is being fetched")
        assertTrue(repository.detailRequests.isEmpty())
        store.close()
    }

    @Test
    fun theSheetOffersEveryRowOfTheModuleWhileItLoads() = runTest {
        // A repeated module has one row per semester, and both belong in the sheet.
        val failed = grade("T4INF2001", "4,6", 6.0, wise2526, status = "unvollständig", resultId = "1")
        val passed = grade("T4INF2001", "3,2", 6.0, sose2026, status = "bestanden (Wh.)", resultId = "1")
        val other = grade("T4INF2004", "2,9", 6.0, wise2526)
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526)),
            grades = Outcome.Ok(listOf(failed, passed, other)),
            moduleDetails = Outcome.Err(AppError.Offline)
        )
        val store = store(repository)
        store.dispatch(GradesIntent.Load)

        store.dispatch(GradesIntent.ModuleOpened(passed))

        val state = store.state.value
        assertEquals(listOf("4,6", "3,2"), state.selectedModuleEntries.map { it.grade })
        assertEquals(AppError.Offline, state.detailsError, "a failed fetch is not a failed screen")
        store.close()
    }

    @Test
    fun closingTheSheet_forgetsWhatWasInIt() = runTest {
        val entry = grade("T4INF4211", "1,0", 5.0, sose2026, resultId = "396314694963893")
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(sose2026)),
            grades = Outcome.Ok(listOf(entry)),
            moduleDetails = Outcome.Ok(
                ModuleResultDetails("T4INF4211", "Compilerbau", "SoSe 2026", emptyList(), emptyList())
            )
        )
        val store = store(repository)
        store.dispatch(GradesIntent.Load)
        store.dispatch(GradesIntent.ModuleOpened(entry))

        store.dispatch(GradesIntent.ModuleClosed)

        val state = store.state.value
        assertNull(state.selectedModule)
        assertNull(state.moduleDetails)
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
        assertEquals(1, store.state.value.grades.size)
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
