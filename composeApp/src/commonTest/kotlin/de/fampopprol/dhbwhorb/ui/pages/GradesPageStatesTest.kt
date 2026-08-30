/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades
import de.fampopprol.dhbwhorb.domain.usecase.GetGradesForSemester
import de.fampopprol.dhbwhorb.domain.usecase.GetModuleDetails
import de.fampopprol.dhbwhorb.domain.usecase.GetSemesters
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import de.fampopprol.dhbwhorb.testutil.fakes.FakeGradeRepository
import de.fampopprol.dhbwhorb.testutil.fakes.FakeSessionRepository
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
class GradesPageStatesTest {

    private val wise2526 = Semester(id = "sem-1", name = "WiSe 2025/26")

    private fun grade(number: String = "T3INF1001", name: String = "Mathematik I", grade: String? = "1,7") =
        GradeEntry(
            semesterId = wise2526.id,
            semesterName = wise2526.name,
            moduleNumber = number,
            moduleName = name,
            grade = grade,
            credits = 6.0,
            status = if (grade == null) "offen" else "bestanden",
        )

    private fun store(
        semesters: Outcome<List<Semester>> = Outcome.Ok(listOf(wise2526)),
        grades: Outcome<List<GradeEntry>> = Outcome.Ok(emptyList()),
        canAuthenticate: Boolean = true,
    ): GradesStore {
        val repository = FakeGradeRepository(semesters = semesters, grades = grades)
        return GradesStore(
            getAllGrades = GetAllGrades(GetSemesters(repository), GetGradesForSemester(repository)),
            getModuleDetails = GetModuleDetails(repository),
            computeGpa = ComputeGpa(),
            sessionRepository = FakeSessionRepository(canAuthenticate = canAuthenticate),
            scope = TestScopes.immediate(),
        )
    }

    @Test
    fun requiresLogin_showsLoginRequiredMessage() = runComposeUiTest {
        setContent { WithTestKoin { GradesPage(store = store(canAuthenticate = false)) } }
        waitForIdle()

        onNodeWithTag("gradesLoginRequiredMessage").assertIsDisplayed()
    }

    @Test
    fun loadError_showsErrorAndRetryRefetches() = runComposeUiTest {
        val repository = FakeGradeRepository(
            semesters = Outcome.Err(AppError.Offline),
        )
        val gradesStore = GradesStore(
            getAllGrades = GetAllGrades(GetSemesters(repository), GetGradesForSemester(repository)),
            getModuleDetails = GetModuleDetails(repository),
            computeGpa = ComputeGpa(),
            sessionRepository = FakeSessionRepository(canAuthenticate = true),
            scope = TestScopes.immediate(),
        )
        setContent { WithTestKoin { GradesPage(store = gradesStore) } }
        waitForIdle()

        onNodeWithTag("gradesRetryButton").assertIsDisplayed()

        // Fix the underlying data before retrying, so the retry can be observed to succeed.
        repository.semesters = Outcome.Ok(listOf(wise2526))
        onNodeWithTag("gradesRetryButton").performClick()
        waitForIdle()

        assertFailsWith<AssertionError> { onNodeWithTag("gradesRetryButton").assertIsDisplayed() }
    }

    @Test
    fun withGrades_showsSemesterCardAndOverallStats() = runComposeUiTest {
        setContent { WithTestKoin { GradesPage(store = store(grades = Outcome.Ok(listOf(grade())))) } }
        waitForIdle()

        onNodeWithTag("gradesPageTitle").assertIsDisplayed()
        onNodeWithTag("semesterGroupCard_WiSe 2025/26").assertIsDisplayed()
        onNodeWithTag("overallStatsCard").assertIsDisplayed()
    }

    @Test
    fun withoutAnyGradedModule_hidesOverallStatsCard() = runComposeUiTest {
        // No grade has a numeric result and no credits are counted, so overallGpa is null and
        // totalCreditsEarned is 0 — the condition that gates OverallStatsCard.
        setContent { WithTestKoin { GradesPage(store = store(grades = Outcome.Ok(listOf(grade(grade = null))))) } }
        waitForIdle()

        assertFailsWith<AssertionError> { onNodeWithTag("overallStatsCard").assertIsDisplayed() }
    }

    // GradesSkeletonList is exercised directly rather than through GradesPage's loading state:
    // GradesStore runs on TestScopes.immediate() in these tests, so EnsureLoaded resolves within
    // the same dispatch and isLoading is never observably true through the public page.
    @Test
    fun gradesSkeletonList_rendersTitleAndSkeletonCards() = runComposeUiTest {
        setContent { GradesSkeletonList() }
        waitForIdle()

        onNodeWithTag("gradesPageTitle").assertIsDisplayed()
    }
}
