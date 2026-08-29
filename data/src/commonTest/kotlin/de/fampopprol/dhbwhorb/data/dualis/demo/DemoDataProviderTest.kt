/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.demo

import de.fampopprol.dhbwhorb.domain.model.SemesterOrder
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoDataProviderTest {

    private val inWinterTerm = LocalDate(2026, 1, 15)
    private val inSummerTerm = LocalDate(2026, 5, 4)

    @Test
    fun semestersAreNamedAfterTheTermTheyBelongTo() {
        // January belongs to the winter term that started the previous autumn.
        assertEquals("WiSe 2025/26", DemoDataProvider.demoSemesters(inWinterTerm).first().name)
        assertEquals("SoSe 2026", DemoDataProvider.demoSemesters(inSummerTerm).first().name)
    }

    @Test
    fun semestersAreThreeAndInStudiedOrder() {
        val semesters = DemoDataProvider.demoSemesters(inSummerTerm)

        assertEquals(listOf("SoSe 2026", "WiSe 2025/26", "SoSe 2025"), semesters.map { it.name })
        // Newest first, so sorting them oldest-first has to reverse them exactly.
        assertEquals(
            semesters.map { it.name }.reversed(),
            semesters.map { it.name }.sortedWith(SemesterOrder.oldestFirst)
        )
    }

    @Test
    fun everyListedSemesterHasGrades_andTheCurrentOneIsStillRunning() {
        val semesters = DemoDataProvider.demoSemesters(inSummerTerm)
        val current = semesters.first()
        val finished = semesters.last()

        val currentGrades = DemoDataProvider.demoGrades(current, studentId = "demo")
        val finishedGrades = DemoDataProvider.demoGrades(finished, studentId = "demo")

        assertTrue(currentGrades.any { it.grade == null }, "the current semester is not over")
        assertTrue(currentGrades.all { it.semesterName == current.name })
        assertTrue(finishedGrades.isNotEmpty() && finishedGrades.all { it.grade != null })
        assertTrue(finishedGrades.all { it.status == "bestanden" })
    }

    @Test
    fun aSemesterTheDemoDoesNotHave_hasNoGrades() {
        val other = DemoDataProvider.demoSemesters(inSummerTerm).first().copy(id = "42")

        assertEquals(emptyList(), DemoDataProvider.demoGrades(other, studentId = "demo"))
    }

    @Test
    fun everyListedDocumentCanBeDownloaded() {
        for (document in DemoDataProvider.demoDocuments(inSummerTerm)) {
            val content = DemoDataProvider.demoDocumentContent(document.downloadUrl, inSummerTerm)

            assertNotNull(content, "${document.title} has no file behind it")
            assertTrue(content.size > 400, "${document.title} is suspiciously small")
            assertTrue(content.decodeToString().startsWith("%PDF-1.4"), "not a PDF")
        }
    }

    @Test
    fun anUnknownDocumentUrl_hasNoContent() {
        assertNull(DemoDataProvider.demoDocumentContent("/scripts/filetransfer.exe?nope"))
    }

    @Test
    fun documentDatesFollowTheDayTheyAreLookedAt() {
        val documents = DemoDataProvider.demoDocuments(LocalDate(2026, 5, 4))

        // dd.MM.yy, twelve days before the given date.
        assertEquals("22.04.26", documents.first().date)
    }
}
