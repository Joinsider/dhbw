/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Note: item labels come from localised string resources, so assertions use [navItemTestTag]
 * instead of the visible text — otherwise these tests pass or fail depending on the locale of
 * the machine running them (English on CI, German on a German dev machine).
 */
@OptIn(ExperimentalTestApi::class)
class BottomNavigationBarTest {

    @Test
    fun bottomNavItem_hasCorrectEnumValues() {
        val values = BottomNavItem.entries
        assertEquals(4, values.size, "Should have exactly 4 navigation items")
        assertTrue(values.contains(BottomNavItem.TIMETABLE))
        assertTrue(values.contains(BottomNavItem.GRADES))
        assertTrue(values.contains(BottomNavItem.DOCUMENTS))
        assertTrue(values.contains(BottomNavItem.SETTINGS))
    }

    @Test
    fun bottomNavItem_hasCorrectIcons() {
        assertEquals(Icons.Default.DateRange, BottomNavItem.TIMETABLE.icon)
        assertEquals(Icons.Default.Star, BottomNavItem.GRADES.icon)
        assertEquals(Icons.Default.Description, BottomNavItem.DOCUMENTS.icon)
        assertEquals(Icons.Default.Settings, BottomNavItem.SETTINGS.icon)
    }

    @Test
    fun bottomNavigationBar_displaysAllItems() = runComposeUiTest {
        setContent {
            BottomNavigationBar(currentItem = BottomNavItem.TIMETABLE, onItemSelected = {})
        }

        waitForIdle()

        BottomNavItem.entries.forEach { item ->
            onNodeWithTag(navItemTestTag(item)).assertIsDisplayed()
        }
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
        onNodeWithTag(navItemTestTag(BottomNavItem.GRADES)).performClick()
        waitForIdle()

        assertNotNull(selectedItem, "Selected item should not be null after click")
        assertEquals(BottomNavItem.GRADES, selectedItem)
    }

    @Test
    fun bottomNavigationBar_switchesBetweenItems() = runComposeUiTest {
        var currentItem = BottomNavItem.TIMETABLE

        setContent {
            BottomNavigationBar(currentItem = currentItem, onItemSelected = { currentItem = it })
        }

        waitForIdle()
        onNodeWithTag(navItemTestTag(BottomNavItem.SETTINGS)).performClick()
        waitForIdle()

        assertEquals(BottomNavItem.SETTINGS, currentItem)
    }

    @Test
    fun bottomNavigationBar_rendersCorrectly() = runComposeUiTest {
        setContent {
            BottomNavigationBar(currentItem = BottomNavItem.GRADES, onItemSelected = {})
        }

        waitForIdle()

        BottomNavItem.entries.forEach { item ->
            onNodeWithTag(navItemTestTag(item)).assertIsDisplayed()
        }
    }
}
