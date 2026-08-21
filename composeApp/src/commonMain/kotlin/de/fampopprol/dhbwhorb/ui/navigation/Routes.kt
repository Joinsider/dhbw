/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.navigation

import kotlinx.serialization.Serializable

/**
 * The destinations of the logged-in graph.
 *
 * Typed rather than an enum plus a `when`: a route carries its arguments, so `Timetable(week)`
 * either has one or does not compile. The previous `AppScreen` enum could only say *which* screen,
 * which is why "open the grades of this semester" had no way to be expressed at all.
 *
 * They live in `:composeApp` and not in `:presentation` on purpose — iOS navigates natively from
 * P7 onwards, so these are Compose's business.
 */
sealed interface Route {

    /**
     * @param week offset from the current week, as the pager counts them. Null means "today's".
     */
    @Serializable
    data class Timetable(val week: Int? = null) : Route


    @Serializable
    data object Grades : Route

    @Serializable
    data object Documents : Route

    @Serializable
    data object Settings : Route
}

/** The scheme deep links use: `dhbw://timetable?week=-1`, `dhbw://grades`. */
const val DEEP_LINK_SCHEME = "dhbw"

/** Which tab is highlighted for a route. */
fun Route.toNavItem(): BottomNavItem = when (this) {
    is Route.Timetable -> BottomNavItem.TIMETABLE
    is Route.Grades -> BottomNavItem.GRADES
    Route.Documents -> BottomNavItem.DOCUMENTS
    Route.Settings -> BottomNavItem.SETTINGS
}

/** Where a tab tap goes. Tapping a tab always lands on its default arguments. */
fun BottomNavItem.toRoute(): Route = when (this) {
    BottomNavItem.TIMETABLE -> Route.Timetable()
    BottomNavItem.GRADES -> Route.Grades
    BottomNavItem.DOCUMENTS -> Route.Documents
    BottomNavItem.SETTINGS -> Route.Settings
}
