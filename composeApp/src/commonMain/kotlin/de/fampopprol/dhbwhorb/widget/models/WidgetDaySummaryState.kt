// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.models

import kotlinx.datetime.LocalDate

/**
 * Data class representing the state for the Day Summary widget variants
 * (Compact and Tall layouts).
 *
 * If there are no classes on the requested day, the use case automatically
 * fetches the next available day with lectures.
 *
 * @param date The date for which classes are shown
 * @param classes The list of classes for this day
 * @param isToday Whether the shown date is today (vs a future fallback day)
 */
data class WidgetDaySummaryState(
    val date: LocalDate,
    val classes: List<WidgetClassState>,
    val isToday: Boolean
)
