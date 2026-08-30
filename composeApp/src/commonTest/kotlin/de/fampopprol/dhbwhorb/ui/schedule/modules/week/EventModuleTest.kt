/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.schedule.modules.week

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.domain.model.Lecture
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EventModuleTest {

    private fun lecture(
        isTest: Boolean = false,
        location: String = "",
        lecturers: List<String> = emptyList(),
        fullName: String? = "Mathematik 1",
    ) = Lecture(
        id = 1L,
        shortName = "MATH1",
        fullName = fullName,
        start = LocalDateTime(2026, 3, 2, 8, 0),
        end = LocalDateTime(2026, 3, 2, 9, 30),
        location = location,
        isTest = isTest,
        lecturers = lecturers,
    )

    @Test
    fun normalFont_showsTimeRangeAndFullName() = runComposeUiTest {
        setContent { EventModule(lecture = lecture()) }

        onNodeWithText("08:00 - 09:30").assertIsDisplayed()
        onNodeWithText("Mathematik 1").assertIsDisplayed()
    }

    @Test
    fun smallFont_hidesTimeRange_showsShortName() = runComposeUiTest {
        setContent { EventModule(lecture = lecture(), smallFont = true) }

        assertFailsWith<AssertionError> { onNodeWithText("08:00 - 09:30").assertIsDisplayed() }
        onNodeWithText("MATH1").assertIsDisplayed()
    }

    @Test
    fun withLecturers_normalFont_showsThem() = runComposeUiTest {
        setContent { EventModule(lecture = lecture(lecturers = listOf("Schmidt", "Müller"))) }

        onNodeWithText("Schmidt, Müller", substring = true).assertIsDisplayed()
    }

    @Test
    fun withLecturers_smallFont_hidesThem() = runComposeUiTest {
        setContent { EventModule(lecture = lecture(lecturers = listOf("Schmidt")), smallFont = true) }

        assertFailsWith<AssertionError> { onNodeWithText("Schmidt", substring = true).assertIsDisplayed() }
    }

    @Test
    fun withLocation_showsIt() = runComposeUiTest {
        setContent { EventModule(lecture = lecture(location = "HOR-100")) }

        onNodeWithText("HOR-100", substring = true).assertIsDisplayed()
    }

    @Test
    fun tapping_invokesOnClick() = runComposeUiTest {
        var clicked = false
        setContent { EventModule(lecture = lecture(), onClick = { clicked = true }) }

        onNodeWithTag("eventModule_1").performTouchInput { click() }
        waitForIdle()

        assertTrue(clicked)
    }
}
