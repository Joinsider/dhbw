/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.schedule.modules.week

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GetDayNameTest {

    private val longNames = mapOf(
        DayOfWeek.MONDAY to "Monday",
        DayOfWeek.TUESDAY to "Tuesday",
        DayOfWeek.WEDNESDAY to "Wednesday",
        DayOfWeek.THURSDAY to "Thursday",
        DayOfWeek.FRIDAY to "Friday",
        DayOfWeek.SATURDAY to "Saturday",
        DayOfWeek.SUNDAY to "Sunday",
    )

    private val shortNames = mapOf(
        DayOfWeek.MONDAY to "Mon",
        DayOfWeek.TUESDAY to "Tue",
        DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri",
        DayOfWeek.SATURDAY to "Sat",
        DayOfWeek.SUNDAY to "Sun",
    )

    @Test
    fun getDayName_long_returnsFullNameForEveryDay() = runComposeUiTest {
        setContent {
            Column {
                for (day in longNames.keys) {
                    Text(text = getDayName(day, short = false))
                }
            }
        }
        waitForIdle()

        for (expected in longNames.values) {
            onNodeWithText(expected).assertIsDisplayed()
        }
    }

    @Test
    fun getDayName_short_returnsAbbreviationForEveryDay() = runComposeUiTest {
        setContent {
            Column {
                for (day in shortNames.keys) {
                    Text(text = getDayName(day, short = true))
                }
            }
        }
        waitForIdle()

        for (expected in shortNames.values) {
            onNodeWithText(expected).assertIsDisplayed()
        }
    }
}
