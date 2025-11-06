
/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BottomNavigationBarTest {

    @Test
    fun bottomNavItem_hasCorrectEnumValues() {
        val values = BottomNavItem.entries
        assertEquals(3, values.size, "Should have exactly 3 navigation items")
        assertTrue(values.contains(BottomNavItem.TIMETABLE))
        assertTrue(values.contains(BottomNavItem.GRADES))
        assertTrue(values.contains(BottomNavItem.SETTINGS))
    }

    @Test
    fun bottomNavItem_hasCorrectIcons() {
        assertEquals(Icons.Default.DateRange, BottomNavItem.TIMETABLE.icon)
        assertEquals(Icons.Default.Star, BottomNavItem.GRADES.icon)
        assertEquals(Icons.Default.Settings, BottomNavItem.SETTINGS.icon)
    }

    @Test
    fun bottomNavigationBar_displaysAllItems() = runComposeUiTest {
        setContent {
            BottomNavigationBar(
                currentItem = BottomNavItem.TIMETABLE,
                onItemSelected = {}
            )
        }

        waitForIdle()

        // Verify navigation items are displayed (using the actual resource strings)
        onNodeWithText("Timetable").assertIsDisplayed()
        onNodeWithText("Grades").assertIsDisplayed()
        onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationBar_callsOnItemSelected_whenItemClicked() = runComposeUiTest {
        var selectedItem: BottomNavItem? = null

        setContent {
            BottomNavigationBar(
                currentItem = BottomNavItem.TIMETABLE,
                onItemSelected = { selectedItem = it }
            )
        }

        waitForIdle()

        // Click on grades item
        onNodeWithText("Grades").performClick()
        waitForIdle()

        // Verify callback was called
        assertNotNull(selectedItem, "Selected item should not be null after click")
        assertEquals(BottomNavItem.GRADES, selectedItem)
    }

    @Test
    fun bottomNavigationBar_switchesBetweenItems() = runComposeUiTest {
        var currentItem = BottomNavItem.TIMETABLE

        setContent {
            BottomNavigationBar(
                currentItem = currentItem,
                onItemSelected = { currentItem = it }
            )
        }

        waitForIdle()

        // Click on settings
        onNodeWithText("Settings").performClick()
        waitForIdle()

        // Verify callback updated
        assertEquals(BottomNavItem.SETTINGS, currentItem)
    }

    @Test
    fun bottomNavigationBar_rendersCorrectly() = runComposeUiTest {
        setContent {
            BottomNavigationBar(
                currentItem = BottomNavItem.GRADES,
                onItemSelected = {}
            )
        }

        waitForIdle()

        // Verify that the navigation bar renders with all three items
        val timetableNodes = onAllNodesWithText("Timetable").fetchSemanticsNodes()
        val gradesNodes = onAllNodesWithText("Grades").fetchSemanticsNodes()
        val settingsNodes = onAllNodesWithText("Settings").fetchSemanticsNodes()

        assertTrue(timetableNodes.isNotEmpty(), "Timetable item should be present")
        assertTrue(gradesNodes.isNotEmpty(), "Grades item should be present")
        assertTrue(settingsNodes.isNotEmpty(), "Settings item should be present")
    }
}

