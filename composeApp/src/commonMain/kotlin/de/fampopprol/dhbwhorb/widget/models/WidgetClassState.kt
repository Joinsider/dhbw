// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.models

/**
 * Platform-agnostic data class representing a single class/lecture for widget display.
 *
 * @param name Full display name of the class
 * @param shortName Short name for compact widget layouts
 * @param startTime Formatted start time string (e.g. "08:00")
 * @param endTime Formatted end time string (e.g. "09:30")
 * @param room Room or location of the class
 * @param isOngoing Whether this class is currently in progress
 */
data class WidgetClassState(
    val name: String,
    val shortName: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val isOngoing: Boolean = false
)
