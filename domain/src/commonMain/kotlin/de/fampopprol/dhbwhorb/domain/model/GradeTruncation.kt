/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlin.math.floor

/**
 * How the DHBW states an average: one decimal place, the rest cut off, never rounded.
 *
 * The exam regulations count only the first digit after the comma and drop every further one, and
 * Dualis' Gesamt-GPA follows that. The app used to show the average rounded to two places, so a
 * 2,55 stood next to Dualis' 2,5 and looked like a different number. A 2,59 is still a 2,5.
 */
object GradeTruncation {

    /** [average] cut to one decimal place. */
    fun oneDecimal(average: Double): Double =
        // The epsilon absorbs binary noise: 2,3 is 2.2999999999999998 in a Double, and without it
        // floor() would make a 2,2 of it.
        floor(average * 10 + EPSILON) / 10

    private const val EPSILON = 1e-9
}
