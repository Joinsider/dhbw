/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LectureTest {

    private fun lecture(fullName: String? = "Mathematik III", lecturers: List<String> = listOf("Prof. Muster")) =
        Lecture(
            id = 1,
            shortName = "MA3",
            fullName = fullName,
            start = LocalDateTime(2026, 3, 2, 8, 0),
            end = LocalDateTime(2026, 3, 2, 9, 30),
            location = "Room 101",
            isTest = false,
            lecturers = lecturers
        )

    @Test
    fun displayName_prefersTheFullNameWhenPresent() {
        assertEquals("Mathematik III", lecture(fullName = "Mathematik III").displayName)
    }

    @Test
    fun displayName_fallsBackToTheShortNameWhenFullNameIsMissing() {
        assertEquals("MA3", lecture(fullName = null).displayName)
    }

    @Test
    fun lecturersDefaultsToEmpty() {
        val bare = Lecture(
            id = 0,
            shortName = "MA3",
            fullName = null,
            start = LocalDateTime(2026, 3, 2, 8, 0),
            end = LocalDateTime(2026, 3, 2, 9, 30),
            location = "Room 101",
            isTest = true
        )

        assertEquals(emptyList(), bare.lecturers)
        assertEquals(true, bare.isTest)
    }

    @Test
    fun equalsAndHashCode_areStructural() {
        val a = lecture()
        val b = lecture()
        val different = lecture(lecturers = listOf("Someone Else"))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, different)
    }

    @Test
    fun copy_changesOnlyTheGivenField() {
        val original = lecture()
        val moved = original.copy(location = "Room 202")

        assertEquals("Room 202", moved.location)
        assertEquals(original.shortName, moved.shortName)
    }
}
