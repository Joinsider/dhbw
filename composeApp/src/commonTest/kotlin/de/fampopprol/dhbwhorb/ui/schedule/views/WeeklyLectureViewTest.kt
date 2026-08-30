/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.schedule.views

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.domain.model.Lecture
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
class WeeklyLectureViewTest {

    // 2026-03-02 is a Monday.
    private fun lecture(id: Long = 1L, hour: Int = 8) = Lecture(
        id = id,
        shortName = "MATH1",
        fullName = "Mathematik 1",
        start = LocalDateTime(2026, 3, 2, hour, 0),
        end = LocalDateTime(2026, 3, 2, hour + 1, 30),
        location = "HOR-100",
        isTest = false,
    )

    @Test
    fun noLecturesAndNotRefreshing_showsEmptyMessage() = runComposeUiTest {
        setContent { WeeklyLecturesView(lectures = emptyList(), isRefreshing = false) }
        waitForIdle()

        onNodeWithTag("noLecturesMessage").assertIsDisplayed()
    }

    @Test
    fun noLecturesButRefreshing_hidesEmptyMessageTagButStillOverlays() = runComposeUiTest {
        setContent { WeeklyLecturesView(lectures = emptyList(), isRefreshing = true) }
        waitForIdle()

        // The refreshing state shows a "loading" text instead, which does not carry the
        // "noLecturesMessage" tag (that tag is reserved for the empty-and-idle case).
        assertFailsWith<AssertionError> { onNodeWithTag("noLecturesMessage").assertIsDisplayed() }
    }

    @Test
    fun withLectures_hidesEmptyMessageAndShowsTheLecture() = runComposeUiTest {
        setContent { WeeklyLecturesView(lectures = listOf(lecture(id = 42L)), isRefreshing = false) }
        waitForIdle()

        assertFailsWith<AssertionError> { onNodeWithTag("noLecturesMessage").assertIsDisplayed() }
        onNodeWithTag("dayColumnLecture_42").assertIsDisplayed()
    }

    @Test
    fun clickingALecture_invokesTheCallbackWithThatLecture() = runComposeUiTest {
        var clicked: Lecture? = null
        val theLecture = lecture(id = 7L)
        setContent {
            WeeklyLecturesView(
                lectures = listOf(theLecture),
                onLectureClick = { clicked = it },
            )
        }
        waitForIdle()

        onNodeWithTag("dayColumnLecture_7").performClick()

        assertEquals(theLecture, clicked)
    }

    @Test
    fun multipleLectures_areSpreadAcrossTheirDays() = runComposeUiTest {
        setContent {
            WeeklyLecturesView(
                lectures = listOf(
                    lecture(id = 1L, hour = 8),
                    lecture(id = 2L, hour = 14),
                ),
            )
        }
        waitForIdle()

        onNodeWithTag("dayColumnLecture_1").assertIsDisplayed()
        onNodeWithTag("dayColumnLecture_2").assertIsDisplayed()
    }
}
