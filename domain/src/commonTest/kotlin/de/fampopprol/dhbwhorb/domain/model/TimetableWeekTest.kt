/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class TimetableWeekTest {

    private fun week(isPartial: Boolean = false, fromCache: Boolean = false) = TimetableWeek(
        weekOffset = 0,
        start = LocalDateTime(2026, 3, 2, 0, 0),
        end = LocalDateTime(2026, 3, 8, 23, 59),
        lectures = emptyList(),
        isPartial = isPartial,
        fromCache = fromCache
    )

    @Test
    fun isPartialAndFromCacheDefaultToFalse() {
        val plain = TimetableWeek(
            weekOffset = 1,
            start = LocalDateTime(2026, 3, 2, 0, 0),
            end = LocalDateTime(2026, 3, 8, 23, 59),
            lectures = emptyList()
        )

        assertFalse(plain.isPartial)
        assertFalse(plain.fromCache)
    }

    @Test
    fun equalsAndHashCode_areStructural() {
        val a = week(isPartial = true)
        val b = week(isPartial = true)
        val different = week(fromCache = true)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, different)
    }

    @Test
    fun copy_changesOnlyTheGivenField() {
        val original = week()
        val cached = original.copy(fromCache = true)

        assertEquals(true, cached.fromCache)
        assertEquals(original.weekOffset, cached.weekOffset)
        assertEquals(original.lectures, cached.lectures)
    }
}
