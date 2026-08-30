/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.schedule.modules.dialogs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.domain.model.Lecture
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LectureDetailsDialogTest {

    private fun lecture(
        fullName: String? = "Mathematik 1",
        shortName: String = "MATH1",
        location: String = "HOR-100",
        isTest: Boolean = false,
        lecturers: List<String> = emptyList(),
    ) = Lecture(
        id = 1L,
        shortName = shortName,
        fullName = fullName,
        start = LocalDateTime(2026, 3, 2, 8, 0),
        end = LocalDateTime(2026, 3, 2, 9, 30),
        location = location,
        isTest = isTest,
        lecturers = lecturers,
    )

    @Test
    fun dialog_showsSubjectNameAndCanBeDismissed() = runComposeUiTest {
        var dismissed = false
        setContent { LectureDetailsDialog(lecture = lecture(), onDismiss = { dismissed = true }) }

        onNodeWithTag("lectureDetailsDialog").assertIsDisplayed()
        onNodeWithTag("lectureDetailsSubjectValue").assertTextEquals("Mathematik 1")

        onNodeWithTag("lectureDetailsCloseButton").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun dialog_fullNameMissing_fallsBackToShortName() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(fullName = null, shortName = "MATH1"), onDismiss = {}) }

        onNodeWithTag("lectureDetailsSubjectValue").assertTextEquals("MATH1")
    }

    @Test
    fun dialog_withLocation_showsLocationRow() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(location = "HOR-100"), onDismiss = {}) }

        onNodeWithTag("lectureLocationRow").assertIsDisplayed()
    }

    @Test
    fun dialog_withoutLocation_hidesLocationRow() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(location = ""), onDismiss = {}) }

        assertFailsWith<AssertionError> { onNodeWithTag("lectureLocationRow").assertIsDisplayed() }
    }

    @Test
    fun dialog_withLecturers_showsLecturersRow() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(lecturers = listOf("Schmidt")), onDismiss = {}) }

        onNodeWithTag("lectureLecturersRow").assertIsDisplayed()
    }

    @Test
    fun dialog_withOnlyUnknownLecturers_hidesLecturersRow() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(lecturers = listOf("Unknown")), onDismiss = {}) }

        assertFailsWith<AssertionError> { onNodeWithTag("lectureLecturersRow").assertIsDisplayed() }
    }

    @Test
    fun dialog_withoutLecturers_hidesLecturersRow() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(lecturers = emptyList()), onDismiss = {}) }

        assertFailsWith<AssertionError> { onNodeWithTag("lectureLecturersRow").assertIsDisplayed() }
    }

    @Test
    fun dialog_isTest_showsExamBanner() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(isTest = true), onDismiss = {}) }

        onNodeWithTag("lectureTestExamBanner").assertIsDisplayed()
    }

    @Test
    fun dialog_isNotTest_hidesExamBanner() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(isTest = false), onDismiss = {}) }

        assertFailsWith<AssertionError> { onNodeWithTag("lectureTestExamBanner").assertIsDisplayed() }
    }

    @Test
    fun dialog_multipleRooms_showsCombinedLocation() = runComposeUiTest {
        setContent { LectureDetailsDialog(lecture = lecture(location = "HOR-100, HOR-101"), onDismiss = {}) }

        onNodeWithTag("lectureLocationRow").assertIsDisplayed()
    }

    @Test
    fun dialog_multipleLecturers_showsThemJoinedByNewline() = runComposeUiTest {
        setContent {
            LectureDetailsDialog(
                lecture = lecture(lecturers = listOf("Schmidt", "Müller")),
                onDismiss = {},
            )
        }

        onNodeWithTag("lectureLecturersRow").assertIsDisplayed()
    }
}
