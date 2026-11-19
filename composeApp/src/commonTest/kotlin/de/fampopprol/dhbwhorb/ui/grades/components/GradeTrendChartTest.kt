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
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.presentation.grades.SemesterAverage
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GradeTrendChartTest {

    @Test
    fun shortenSemesterLabel_abbreviatesWinterSemester() {
        assertEquals("WS 25/26", shortenSemesterLabel("WiSe 2025/26"))
    }

    @Test
    fun shortenSemesterLabel_abbreviatesSummerSemester() {
        assertEquals("SS 26", shortenSemesterLabel("SoSe 2026"))
    }

    @Test
    fun shortenSemesterLabel_unknownNameIsUnchanged() {
        assertEquals("Praxissemester", shortenSemesterLabel("Praxissemester"))
    }

    @Test
    fun chart_rendersWithTagAndTitle_forTwoPoints() = runComposeUiTest {
        setContent {
            GradeTrendChart(
                points = listOf(
                    SemesterAverage(semesterName = "WiSe 2025/26", average = 2.4),
                    SemesterAverage(semesterName = "SoSe 2026", average = 2.1),
                ),
                overallGpa = 2.2,
            )
        }

        onNodeWithTag("gradeTrendChart").assertIsDisplayed()
        onNodeWithText("Grade trend").assertIsDisplayed()
    }

    @Test
    fun gridlinesSitOnRoundGrades() {
        assertEquals(listOf(2.0, 2.5, 3.0), gridValues(1.93, 3.1))
        // Too narrow for two 0,5 steps: falls back to 0,2.
        assertEquals(2, gridValues(2.1, 2.5).size)
        gridValues(2.1, 2.5).forEach { assertEquals(it, (it * 10).roundToInt() / 10.0, 1e-9) }
    }
}
