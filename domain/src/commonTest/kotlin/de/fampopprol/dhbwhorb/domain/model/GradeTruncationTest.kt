/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class GradeTruncationTest {

    @Test
    fun theSecondDecimalIsCutOffNotRounded() {
        // What Dualis showed as 2,5 while the app said 2,55.
        assertEquals(2.5, GradeTruncation.oneDecimal(2.55))
        assertEquals(2.5, GradeTruncation.oneDecimal(2.5999))
        assertEquals(1.0, GradeTruncation.oneDecimal(1.0))
    }

    @Test
    fun binaryNoiseDoesNotCostADecimal() {
        assertEquals(2.3, GradeTruncation.oneDecimal(0.1 + 2.2))
        assertEquals(2.3, GradeTruncation.oneDecimal(23.0 / 10))
    }
}
