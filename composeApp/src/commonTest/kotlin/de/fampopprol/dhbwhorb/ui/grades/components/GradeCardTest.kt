/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.grades.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GradeCardTest {

    private fun grade(
        moduleNumber: String = "T3INF1001",
        moduleName: String = "Mathematik I",
        grade: String? = "1,7",
        credits: Double = 6.0,
        status: String? = "bestanden",
    ) = GradeEntry(
        semesterId = "sem-1",
        semesterName = "WiSe 2025/26",
        moduleNumber = moduleNumber,
        moduleName = moduleName,
        grade = grade,
        credits = credits,
        status = status,
    )

    @Test
    fun card_showsModuleNameNumberCreditsAndStatus() = runComposeUiTest {
        setContent {
            GradeCard(grade = grade())
        }

        onNodeWithText("Mathematik I").assertIsDisplayed()
        onNodeWithText("T3INF1001 • 6.0 Credits").assertIsDisplayed()
        onNodeWithText("bestanden").assertIsDisplayed()
        onNodeWithText("1,7").assertIsDisplayed()
    }

    @Test
    fun card_ungradedModule_showsDash() = runComposeUiTest {
        setContent {
            GradeCard(grade = grade(grade = null, status = "offen"))
        }

        onNodeWithText("-").assertIsDisplayed()
    }

    @Test
    fun card_nullStatus_showsUnknownStatusText() = runComposeUiTest {
        setContent {
            GradeCard(grade = grade(status = null))
        }
        waitForIdle()

        onNodeWithText("status unknown").assertIsDisplayed()
    }

    @Test
    fun card_failingGrade_isStillDisplayed() = runComposeUiTest {
        setContent {
            GradeCard(grade = grade(grade = "5,0", status = "nicht bestanden"))
        }

        onNodeWithText("5,0").assertIsDisplayed()
        onNodeWithText("nicht bestanden").assertIsDisplayed()
    }
}
