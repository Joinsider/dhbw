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
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Runs on desktopTest (not commonTest) because it pins [Locale] to make the `stringResource(...)`
 * day names deterministic regardless of the machine/CI's default locale — `java.util.Locale` is not
 * available on the Kotlin/Native macOS targets that also compile commonTest.
 */
@OptIn(ExperimentalTestApi::class)
class GetDayNameTest {

    private lateinit var originalLocale: Locale

    @BeforeTest
    fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

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
