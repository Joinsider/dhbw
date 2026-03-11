// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.models

/**
 * Sealed class representing the state for the "Up Next" widget variant.
 *
 * This widget shows the immediately next class or the currently running class.
 */
sealed class WidgetUpNextState {
    /**
     * There is an upcoming or ongoing class to display.
     *
     * @param nextClass The next or currently ongoing class
     */
    data class HasClass(val nextClass: WidgetClassState) : WidgetUpNextState()

    /**
     * There are no upcoming or ongoing classes in the available data window.
     */
    data object NoUpcomingClass : WidgetUpNextState()
}
