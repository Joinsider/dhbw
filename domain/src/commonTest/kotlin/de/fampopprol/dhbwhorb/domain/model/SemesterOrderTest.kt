/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SemesterOrderTest {

    @Test
    fun aWinterTermComesAfterTheSummerTermOfTheSameYear() {
        val sorted = listOf("WiSe 2025/26", "SoSe 2025", "WiSe 2024/25")
            .sortedWith(SemesterOrder.oldestFirst)

        assertEquals(listOf("WiSe 2024/25", "SoSe 2025", "WiSe 2025/26"), sorted)
    }

    @Test
    fun theOlderSpellingsSortTogetherWithTheCurrentOnes() {
        // Dualis has written both over the years; a mixed list must not fall apart.
        val sorted = listOf("SS 2026", "WiSe 2025/26", "SoSe 2025", "WS 2024/25")
            .sortedWith(SemesterOrder.oldestFirst)

        assertEquals(listOf("WS 2024/25", "SoSe 2025", "WiSe 2025/26", "SS 2026"), sorted)
    }

    @Test
    fun aNameWithoutAYearOrATermSortsLast() {
        val sorted = listOf("Unbekannt", "SoSe 2025").sortedWith(SemesterOrder.oldestFirst)

        assertEquals(listOf("SoSe 2025", "Unbekannt"), sorted)
        assertNull(SemesterOrder.sortKey("Unbekannt"))
    }

    @Test
    fun aNameWithAYearButNoRecognizableTermLetterSortsLast() {
        // Has a year, so the regex matches, but the leading letter is neither W nor S.
        assertNull(SemesterOrder.sortKey("Herbst 2025"))

        val sorted = listOf("Herbst 2025", "SoSe 2025").sortedWith(SemesterOrder.oldestFirst)
        assertEquals(listOf("SoSe 2025", "Herbst 2025"), sorted)
    }

    @Test
    fun leadingWhitespaceBeforeTheTermLetterIsIgnored() {
        assertEquals(SemesterOrder.sortKey("WiSe 2025/26"), SemesterOrder.sortKey("  WiSe 2025/26"))
    }

    @Test
    fun theKeyGrowsWithTime() {
        val wise2425 = SemesterOrder.sortKey("WiSe 2024/25")!!
        val sose2025 = SemesterOrder.sortKey("SoSe 2025")!!
        val wise2526 = SemesterOrder.sortKey("WiSe 2025/26")!!

        assertEquals(listOf(wise2425, sose2025, wise2526), listOf(wise2425, sose2025, wise2526).sorted())
    }
}
