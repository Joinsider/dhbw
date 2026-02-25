/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.ui.schedule.models.LectureModel
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableUiState
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.WeekLabelData
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TimetablePageTest {

    @Test
    fun timetablePage_displaysWeeklyLecturesView() = runComposeUiTest {
        val mockViewModel = createMockViewModel()

        setContent {
            TimetablePage(
                viewModel = mockViewModel as? TimetableViewModel,
                isLoggedIn = true
            )
        }

        waitForIdle()

        // Verify page is rendered (check for sample lectures)
        onNodeWithText("Sample Lecture").assertIsDisplayed()
    }

    @Test
    fun timetablePage_displaysBottomNavigation_whenLoggedIn() = runComposeUiTest {
        val mockViewModel = createMockViewModel()

        setContent {
            TimetablePage(
                viewModel = mockViewModel as? TimetableViewModel,
                isLoggedIn = true
            )
        }

        waitForIdle()

        // Bottom navigation should be visible when logged in
        onNodeWithText("Timetable").assertIsDisplayed()
        onNodeWithText("Grades").assertIsDisplayed()
        onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun timetablePage_hidesBottomNavigation_whenNotLoggedIn() = runComposeUiTest {
        val mockViewModel = createMockViewModel()

        setContent {
            TimetablePage(
                viewModel = mockViewModel as? TimetableViewModel,
                isLoggedIn = false
            )
        }

        waitForIdle()

        // Bottom navigation should not be visible when not logged in
        onNodeWithText("Timetable").assertDoesNotExist()
        onNodeWithText("Grades").assertDoesNotExist()
        onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun timetablePage_displaysMultipleLectures() = runComposeUiTest {
        val mockViewModel = createMockViewModel()

        setContent {
            TimetablePage(
                viewModel = mockViewModel as? TimetableViewModel,
                isLoggedIn = true
            )
        }

        waitForIdle()

        // Check that multiple lectures are displayed
        onNodeWithText("Sample Lecture").assertIsDisplayed()
        onNodeWithText("Advanced Topics").assertIsDisplayed()
        onNodeWithText("Practical Session").assertIsDisplayed()
    }

    private fun createMockViewModel(): Any {
        val sampleLectures = listOf(
            LectureModel(
                name = "Sample Lecture",
                isTest = false,
                start = LocalDateTime(2024, 1, 15, 9, 0),
                end = LocalDateTime(2024, 1, 15, 10, 30),
                lecturers = listOf("Dr. Smith"),
                location = "Room 101"
            ),
            LectureModel(
                name = "Advanced Topics",
                isTest = false,
                start = LocalDateTime(2024, 1, 16, 11, 0),
                end = LocalDateTime(2024, 1, 16, 12, 30),
                lecturers = listOf("Prof. Johnson"),
                location = "Room 202"
            ),
            LectureModel(
                name = "Practical Session",
                isTest = false,
                start = LocalDateTime(2024, 1, 17, 14, 0),
                end = LocalDateTime(2024, 1, 17, 15, 30),
                lecturers = listOf("Ms. Wilson"),
                location = "Lab 303"
            )
        )

        return object {
            @Suppress("UNUSED")
            val uiState by mutableStateOf(
                TimetableUiState(
                    lectures = sampleLectures,
                    weekLabelData = WeekLabelData(
                        mondayDay = 15,
                        fridayDay = 19,
                        mondayMonth = Month.JANUARY,
                        fridayMonth = Month.JANUARY
                    )
                )
            )

            @Suppress("UNUSED")
            fun loadLecturesForCurrentWeek() {
                // Mock implementation - do nothing
            }

            @Suppress("UNUSED")
            fun goToPreviousWeek() {
                // Mock implementation - do nothing
            }

            @Suppress("UNUSED")
            fun goToNextWeek() {
                // Mock implementation - do nothing
            }

            @Suppress("UNUSED")
            fun refreshLectures() {
                // Mock implementation - do nothing
            }
        }
    }
}






