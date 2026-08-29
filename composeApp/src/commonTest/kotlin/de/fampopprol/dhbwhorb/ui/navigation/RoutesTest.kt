/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The route types are what deep links decode into and what the navigation bar maps back from, so
 * both directions have to line up for every tab. An enum plus a `when` could not express a
 * destination's arguments at all, which is why "open the grades of this semester" had no route.
 */
class RoutesTest {

    @Test
    fun everyTab_mapsToARouteAndBackToItself() {
        for (item in BottomNavItem.entries) {
            assertEquals(
                item,
                item.toRoute().toNavItem(),
                "$item has to survive the round trip through its route"
            )
        }
    }

    @Test
    fun tappingATab_landsOnDefaultArguments() {
        // A tab tap is "show me this screen", not "show me the week I deep-linked to last time".
        assertEquals(Route.Timetable(week = null), BottomNavItem.TIMETABLE.toRoute())
        assertEquals(Route.Grades, BottomNavItem.GRADES.toRoute())
    }

    @Test
    fun aRoutesArgumentsDoNotChangeWhichTabItBelongsTo() {
        assertEquals(BottomNavItem.TIMETABLE, Route.Timetable(week = -12).toNavItem())
        assertEquals(BottomNavItem.GRADES, Route.Grades.toNavItem())
    }
}
