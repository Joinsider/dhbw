/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades
import de.fampopprol.dhbwhorb.domain.usecase.GetGradesForSemester
import de.fampopprol.dhbwhorb.domain.usecase.GetSemesters
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.navItemTestTag
import de.fampopprol.dhbwhorb.testutil.fakes.FakeGradeRepository
import de.fampopprol.dhbwhorb.testutil.fakes.FakeSessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The page renders inside the logged-in graph, so there is no "not logged in" variant of it any
 * more — the root shows the login screen instead. `AppRoutingTest` covers that.
 */
@OptIn(ExperimentalTestApi::class)
class GradesPageTest {

    private val wise2526 = Semester(id = "000000015168000", name = "WiSe 2025/26")

    private fun store(): GradesStore {
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(wise2526)),
            grades = Outcome.Ok(emptyList())
        )
        val getSemesters = GetSemesters(repository)
        val getForSemester = GetGradesForSemester(repository)
        return GradesStore(
            getSemesters = getSemesters,
            getGradesForSemester = getForSemester,
            getAllGrades = GetAllGrades(getSemesters, getForSemester),
            computeGpa = ComputeGpa(),
            sessionRepository = FakeSessionRepository(canAuthenticate = true),
            scope = TestScopes.immediate()
        )
    }

    @Test
    fun gradesPage_displaysBottomNavigation() = runComposeUiTest {
        val store = store()
        setContent { WithTestKoin { GradesPage(store = store) } }
        waitForIdle()

        onNodeWithTag("gradesPageTitle").assertIsDisplayed()
        // Tags instead of labels: nav captions are localised string resources.
        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertIsDisplayed()
        onNodeWithTag(navItemTestTag(BottomNavItem.SETTINGS)).assertIsDisplayed()
        store.close()
    }

    @Test
    fun gradesPage_preselectsTheSemesterADeepLinkNames() = runComposeUiTest {
        val store = store()

        // dhbw://grades/000000015168000 lands here as initialSemesterId.
        setContent { WithTestKoin { GradesPage(initialSemesterId = wise2526.id, store = store) } }
        waitForIdle()

        assertEquals(wise2526.id, store.state.value.selectedSemester?.id)
        store.close()
    }
}
