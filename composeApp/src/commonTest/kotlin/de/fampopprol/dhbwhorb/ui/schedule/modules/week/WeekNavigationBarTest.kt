/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.schedule.modules.week

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WeekNavigationBarTest {

    @Test
    fun clickingPreviousWeek_invokesTheCallback() = runComposeUiTest {
        var clicked = false
        setContent { WeekNavigationBar(onPreviousWeek = { clicked = true }) }

        onNodeWithTag("previousWeekButton").performClick()

        assertTrue(clicked)
    }

    @Test
    fun clickingNextWeek_invokesTheCallback() = runComposeUiTest {
        var clicked = false
        setContent { WeekNavigationBar(onNextWeek = { clicked = true }) }

        onNodeWithTag("nextWeekButton").performClick()

        assertTrue(clicked)
    }

    @Test
    fun isRefreshing_disablesNavigationButtons() = runComposeUiTest {
        setContent { WeekNavigationBar(isRefreshing = true) }

        onNodeWithTag("previousWeekButton").assertIsNotEnabled()
        onNodeWithTag("nextWeekButton").assertIsNotEnabled()
    }

    @Test
    fun notRefreshing_navigationButtonsAreEnabled() = runComposeUiTest {
        setContent { WeekNavigationBar(isRefreshing = false) }

        onNodeWithTag("previousWeekButton").assertIsEnabled()
        onNodeWithTag("nextWeekButton").assertIsEnabled()
    }

    @Test
    fun tappingWeekLabel_invokesOnWeekLabelClick() = runComposeUiTest {
        var clicked = false
        setContent { WeekNavigationBar(onWeekLabelClick = { clicked = true }) }

        onNodeWithTag("weekLabelButton").performTouchInput { click() }
        waitForIdle()

        assertTrue(clicked)
    }

    @Test
    fun longPressingWeekLabel_invokesOnRefresh() = runComposeUiTest {
        var refreshed = false
        setContent { WeekNavigationBar(onRefresh = { refreshed = true }) }

        onNodeWithTag("weekLabelButton").performTouchInput { longClick() }
        waitForIdle()

        assertTrue(refreshed)
    }

    @Test
    fun whileRefreshing_tappingWeekLabel_doesNothing() = runComposeUiTest {
        var clicked = false
        setContent { WeekNavigationBar(isRefreshing = true, onWeekLabelClick = { clicked = true }) }

        onNodeWithTag("weekLabelButton").performTouchInput { click() }
        waitForIdle()

        assertEquals(false, clicked)
    }

    @Test
    fun refreshButton_visibleOnDesktop_andClickInvokesOnRefresh() = runComposeUiTest {
        var refreshed = false
        setContent { WeekNavigationBar(onRefresh = { refreshed = true }) }

        onNodeWithTag("refreshButton").assertIsDisplayed()
        onNodeWithTag("refreshButton").performClick()

        assertTrue(refreshed)
    }

    @Test
    fun refreshButton_absentWhenNoCallbackProvided() = runComposeUiTest {
        setContent { WeekNavigationBar(onRefresh = null) }

        assertFailsWith<AssertionError> { onNodeWithTag("refreshButton").assertIsDisplayed() }
    }

    @Test
    fun refreshButton_disabledWhileRefreshing() = runComposeUiTest {
        setContent { WeekNavigationBar(isRefreshing = true, onRefresh = {}) }

        onNodeWithTag("refreshButton").assertIsNotEnabled()
    }

    @Test
    fun whileRefreshing_longPressingWeekLabel_doesNothing() = runComposeUiTest {
        var refreshed = false
        setContent { WeekNavigationBar(isRefreshing = true, onRefresh = { refreshed = true }) }

        onNodeWithTag("weekLabelButton").performTouchInput { longClick() }
        waitForIdle()

        assertEquals(false, refreshed)
    }

    @Test
    fun longPressingWeekLabel_withNoRefreshCallback_doesNothing() = runComposeUiTest {
        setContent { WeekNavigationBar(onRefresh = null, onWeekLabelClick = {}) }

        // Should not throw even though there is nothing to invoke.
        onNodeWithTag("weekLabelButton").performTouchInput { longClick() }
        waitForIdle()

        onNodeWithTag("weekLabelButton").assertIsDisplayed()
    }
}
