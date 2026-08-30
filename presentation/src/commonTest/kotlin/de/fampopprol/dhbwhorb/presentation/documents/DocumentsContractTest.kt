/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.documents

import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DocumentsContractTest {

    private val bescheinigung = DualisDocument(
        title = "Studienbescheinigung",
        date = "25.03.26",
        time = "09:40",
        downloadUrl = "/download/1"
    )
    private val zahlung = DualisDocument(
        title = "Zahlungsaufforderung",
        date = "01.01.26",
        time = "00:00",
        downloadUrl = "/download/2"
    )

    @Test
    fun documents_withABlankQuery_returnsEverything() {
        val state = DocumentsState(allDocuments = listOf(bescheinigung, zahlung), searchQuery = "")

        assertEquals(listOf(bescheinigung, zahlung), state.documents)
    }

    @Test
    fun documents_filtersByTitleCaseInsensitively() {
        val state = DocumentsState(allDocuments = listOf(bescheinigung, zahlung), searchQuery = "zahlung")

        assertEquals(listOf(zahlung), state.documents)
    }

    @Test
    fun isDownloading_matchesByTheDocumentsKey() {
        val state = DocumentsState(downloading = setOf(bescheinigung.key()))

        assertTrue(state.isDownloading(bescheinigung))
        assertFalse(state.isDownloading(zahlung))
    }

    @Test
    fun key_combinesTitleDateAndTime_soTwoNoticesWithTheSameTitleDiffer() {
        val secondPayment = zahlung.copy(date = "01.02.26")

        assertNotEquals(zahlung.key(), secondPayment.key())
    }

    @Test
    fun openFile_equalsComparesBytesByContentNotByReference() {
        val a = DocumentsEffect.OpenFile("a.pdf", byteArrayOf(1, 2, 3))
        val b = DocumentsEffect.OpenFile("a.pdf", byteArrayOf(1, 2, 3))
        val differentBytes = DocumentsEffect.OpenFile("a.pdf", byteArrayOf(9))
        val differentName = DocumentsEffect.OpenFile("b.pdf", byteArrayOf(1, 2, 3))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, differentBytes)
        assertNotEquals(a, differentName)
        assertFalse(a.equals("not an OpenFile"))
    }

    @Test
    fun saveFile_equalsComparesBytesByContentNotByReference() {
        val a = DocumentsEffect.SaveFile("a.pdf", byteArrayOf(1, 2, 3))
        val b = DocumentsEffect.SaveFile("a.pdf", byteArrayOf(1, 2, 3))
        val differentBytes = DocumentsEffect.SaveFile("a.pdf", byteArrayOf(9))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, differentBytes)
        assertFalse(a.equals("not a SaveFile"))
    }
}
