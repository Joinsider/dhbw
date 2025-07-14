/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.models

data class TimetableEvent(
    val title: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val lecturer: String
)