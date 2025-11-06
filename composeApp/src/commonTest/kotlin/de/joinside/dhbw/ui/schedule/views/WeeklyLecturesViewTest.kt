/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.schedule.views

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import de.joinside.dhbw.ui.schedule.modules.LectureModel
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WeeklyLecturesViewTest {

    @Test
    fun weeklyLecturesView_displaysNoLecturesMessage_whenEmpty() = runComposeUiTest {
        setContent {
            WeeklyLecturesView(lectures = emptyList())
        }

        waitForIdle()

        // Should display "no lectures" message when list is empty (using test tag)
        onNodeWithTag("noLecturesMessage").assertIsDisplayed()
    }

    @Test
    fun weeklyLecturesView_displaysLectures_whenNotEmpty() = runComposeUiTest {
        val lecture = LectureModel(
            name = "Test Lecture",
            color = androidx.compose.ui.graphics.Color.Blue,
            start = LocalDateTime(2024, 6, 10, 9, 0),
            end = LocalDateTime(2024, 6, 10, 10, 30),
            lecturer = "Dr. Test",
            location = "Room 101"
        )

        setContent {
            MaterialTheme {
                WeeklyLecturesView(lectures = listOf(lecture))
            }
        }

        waitForIdle()

        // Should display lecture name
        onNodeWithText("Test Lecture").assertIsDisplayed()
        // Lecturer and room are displayed but might have prefixes from resources
        onNodeWithText("Room 101", substring = true).assertIsDisplayed()
    }

    @Test
    fun weeklyLecturesView_displaysMultipleLectures() = runComposeUiTest {
        val lectures = listOf(
            LectureModel(
                name = "Lecture 1",
                color = androidx.compose.ui.graphics.Color.Blue,
                start = LocalDateTime(2024, 6, 10, 9, 0),
                end = LocalDateTime(2024, 6, 10, 10, 30),
                lecturer = "Dr. One",
                location = "Room 101"
            ),
            LectureModel(
                name = "Lecture 2",
                color = androidx.compose.ui.graphics.Color.Green,
                start = LocalDateTime(2024, 6, 10, 11, 0),
                end = LocalDateTime(2024, 6, 10, 12, 30),
                lecturer = "Dr. Two",
                location = "Room 102"
            ),
            LectureModel(
                name = "Lecture 3",
                color = androidx.compose.ui.graphics.Color.Red,
                start = LocalDateTime(2024, 6, 11, 14, 0),
                end = LocalDateTime(2024, 6, 11, 15, 30),
                lecturer = "Dr. Three",
                location = "Room 103"
            )
        )

        setContent {
            MaterialTheme {
                WeeklyLecturesView(lectures = lectures)
            }
        }

        waitForIdle()

        // Should display all lectures
        onNodeWithText("Lecture 1").assertIsDisplayed()
        onNodeWithText("Lecture 2").assertIsDisplayed()
        onNodeWithText("Lecture 3").assertIsDisplayed()
    }

    @Test
    fun weeklyLecturesView_displaysTimelineForLectures() = runComposeUiTest {
        val lecture = LectureModel(
            name = "Morning Lecture",
            color = androidx.compose.ui.graphics.Color.Blue,
            start = LocalDateTime(2024, 6, 10, 9, 0),
            end = LocalDateTime(2024, 6, 10, 10, 30),
            lecturer = "Dr. Morning",
            location = "Room 201"
        )

        setContent {
            MaterialTheme {
                WeeklyLecturesView(lectures = listOf(lecture))
            }
        }

        waitForIdle()

        // Timeline hours should be displayed
        onNodeWithText("08:00").assertIsDisplayed()
        onNodeWithText("09:00").assertIsDisplayed()
    }

    @Test
    fun weeklyLecturesView_groupsLecturesByDay() = runComposeUiTest {
        val lecturesOnDifferentDays = listOf(
            LectureModel(
                name = "Monday Lecture",
                color = androidx.compose.ui.graphics.Color.Blue,
                start = LocalDateTime(2024, 6, 10, 9, 0), // Monday
                end = LocalDateTime(2024, 6, 10, 10, 30),
                lecturer = "Dr. Monday",
                location = "Room 101"
            ),
            LectureModel(
                name = "Wednesday Lecture",
                color = androidx.compose.ui.graphics.Color.Green,
                start = LocalDateTime(2024, 6, 12, 9, 0), // Wednesday
                end = LocalDateTime(2024, 6, 12, 10, 30),
                lecturer = "Dr. Wednesday",
                location = "Room 102"
            )
        )

        setContent {
            MaterialTheme {
                WeeklyLecturesView(lectures = lecturesOnDifferentDays)
            }
        }

        waitForIdle()

        // Both lectures should be displayed
        onNodeWithText("Monday Lecture").assertIsDisplayed()
        onNodeWithText("Wednesday Lecture").assertIsDisplayed()
    }
}

