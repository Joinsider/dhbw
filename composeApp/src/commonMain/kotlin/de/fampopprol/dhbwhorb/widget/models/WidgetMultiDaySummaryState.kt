// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.models

/**
 * Data class representing the state for the Multi-Day Summary widget variants
 * (Wide and Large layouts).
 *
 * Shows classes for today and tomorrow. If either day lacks classes, the use
 * case automatically selects the next two available days with lectures.
 *
 * @param days The list of day summaries to display (typically 2 days)
 */
data class WidgetMultiDaySummaryState(
    val days: List<WidgetDaySummaryState>
)
