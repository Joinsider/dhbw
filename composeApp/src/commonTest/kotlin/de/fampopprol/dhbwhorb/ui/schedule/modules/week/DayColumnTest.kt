/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.schedule.modules.week

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.domain.model.Lecture
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DayColumnTest {

    private fun lecture(id: Long) = Lecture(
        id = id,
        shortName = "MATH$id",
        fullName = "Mathematik $id",
        start = LocalDateTime(2026, 3, 2, 8, 0),
        end = LocalDateTime(2026, 3, 2, 9, 30),
        location = "HOR-100",
        isTest = false,
    )

    @Test
    fun showsTheDayHeader() = runComposeUiTest {
        setContent { DayColumn(dayOfWeek = DayOfWeek.MONDAY, lectures = emptyList(), width = 150.dp) }

        onNodeWithTag("dayColumnHeader_MONDAY").assertIsDisplayed()
    }

    @Test
    fun rendersOneEventModulePerLecture() = runComposeUiTest {
        setContent {
            DayColumn(dayOfWeek = DayOfWeek.MONDAY, lectures = listOf(lecture(1), lecture(2)), width = 150.dp)
        }

        onNodeWithTag("dayColumnLecture_1").assertIsDisplayed()
        onNodeWithTag("dayColumnLecture_2").assertIsDisplayed()
    }

    @Test
    fun isSkeleton_rendersThreeSkeletonEvents_insteadOfLectures() = runComposeUiTest {
        setContent {
            DayColumn(
                dayOfWeek = DayOfWeek.MONDAY,
                lectures = listOf(lecture(1)),
                width = 150.dp,
                isSkeleton = true,
            )
        }

        assertEquals(3, onAllNodesWithTag("dayColumnSkeletonEvent").fetchSemanticsNodes().size)
    }

    @Test
    fun aNarrowColumn_stillRendersTheLecture_withTheSmallerFont() = runComposeUiTest {
        setContent {
            DayColumn(dayOfWeek = DayOfWeek.MONDAY, lectures = listOf(lecture(1)), width = 80.dp)
        }

        onNodeWithTag("dayColumnLecture_1").assertIsDisplayed()
    }

    @Test
    fun aNarrowColumn_stillRendersTheSkeleton_withTheSmallerFont() = runComposeUiTest {
        setContent {
            DayColumn(dayOfWeek = DayOfWeek.MONDAY, lectures = emptyList(), width = 80.dp, isSkeleton = true)
        }

        assertEquals(3, onAllNodesWithTag("dayColumnSkeletonEvent").fetchSemanticsNodes().size)
    }

    @Test
    fun clickingALecture_invokesOnLectureClickWithIt() = runComposeUiTest {
        var clicked: Lecture? = null
        setContent {
            DayColumn(
                dayOfWeek = DayOfWeek.MONDAY,
                lectures = listOf(lecture(1)),
                width = 150.dp,
                onLectureClick = { clicked = it },
            )
        }

        onNodeWithTag("eventModule_1").performTouchInput { click() }
        waitForIdle()

        assertTrue(clicked == lecture(1))
    }
}
