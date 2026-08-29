/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.grades.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
class SemesterGroupCardTest {

    private fun grade(
        moduleNumber: String = "T3INF1001",
        moduleName: String = "Mathematik I",
        grade: String? = "1,7",
        credits: Double = 6.0,
    ) = GradeEntry(
        semesterId = "sem-1",
        semesterName = "WiSe 2025/26",
        moduleNumber = moduleNumber,
        moduleName = moduleName,
        grade = grade,
        credits = credits,
        status = if (grade == null) "offen" else "bestanden",
    )

    @Test
    fun card_showsSemesterNameAndModuleSummary() = runComposeUiTest {
        setContent {
            SemesterGroupCard(
                semesterName = "WiSe 2025/26",
                grades = listOf(grade(), grade(moduleNumber = "T3INF1002", moduleName = "Programmierung 1")),
                semesterGpa = 1.85,
            )
        }

        onNodeWithTag("semesterGroupCard_WiSe 2025/26").assertIsDisplayed()
        onNodeWithText("WiSe 2025/26").assertIsDisplayed()
        onNodeWithText("2 modules • 12.0 credits").assertIsDisplayed()
        onNodeWithText("Ø 1.85").assertIsDisplayed()
    }

    @Test
    fun card_withoutGpa_showsNoAverage() = runComposeUiTest {
        setContent {
            SemesterGroupCard(semesterName = "WiSe 2025/26", grades = listOf(grade()), semesterGpa = null)
        }

        assertFailsWith<AssertionError> { onNodeWithText("Ø", substring = true).assertIsDisplayed() }
    }

    @Test
    fun card_startsCollapsed_gradesNotShown() = runComposeUiTest {
        setContent {
            SemesterGroupCard(semesterName = "WiSe 2025/26", grades = listOf(grade()), semesterGpa = 1.7)
        }

        assertFailsWith<AssertionError> { onNodeWithTag("gradeRow_T3INF1001").assertIsDisplayed() }
    }

    @Test
    fun card_clickingHeader_expandsAndShowsGrades() = runComposeUiTest {
        setContent {
            SemesterGroupCard(semesterName = "WiSe 2025/26", grades = listOf(grade()), semesterGpa = 1.7)
        }

        onNodeWithTag("semesterCardHeader").performClick()
        waitForIdle()

        onNodeWithTag("gradeRow_T3INF1001").assertIsDisplayed()
        onNodeWithText("Mathematik I").assertIsDisplayed()
        onNodeWithText("1,7").assertIsDisplayed()
    }

    @Test
    fun card_clickingHeaderTwice_collapsesAgain() = runComposeUiTest {
        setContent {
            SemesterGroupCard(semesterName = "WiSe 2025/26", grades = listOf(grade()), semesterGpa = 1.7)
        }

        onNodeWithTag("semesterCardHeader").performClick()
        waitForIdle()
        onNodeWithTag("semesterCardHeader").performClick()
        waitForIdle()

        assertFailsWith<AssertionError> { onNodeWithTag("gradeRow_T3INF1001").assertIsDisplayed() }
    }

    @Test
    fun card_ungradedModule_showsDash() = runComposeUiTest {
        setContent {
            SemesterGroupCard(
                semesterName = "WiSe 2025/26",
                grades = listOf(grade(grade = null)),
                semesterGpa = null,
            )
        }

        onNodeWithTag("semesterCardHeader").performClick()
        waitForIdle()

        onNodeWithText("-").assertIsDisplayed()
    }
}
