/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DualisDocumentTest {

    private val document = DualisDocument(
        title = "Bescheinigung",
        date = "01.01.2026",
        time = "12:00",
        downloadUrl = "https://dualis.example/download/1"
    )

    @Test
    fun equalsAndHashCode_areStructural() {
        val same = document.copy()
        val different = document.copy(title = "Other")

        assertEquals(document, same)
        assertEquals(document.hashCode(), same.hashCode())
        assertNotEquals(document, different)
    }

    @Test
    fun copy_changesOnlyTheGivenField() {
        val renamed = document.copy(title = "Renamed")

        assertEquals("Renamed", renamed.title)
        assertEquals(document.date, renamed.date)
        assertEquals(document.time, renamed.time)
        assertEquals(document.downloadUrl, renamed.downloadUrl)
    }

    @Test
    fun isSerializable() {
        val json = Json.encodeToString(document)
        val decoded = Json.decodeFromString<DualisDocument>(json)

        assertEquals(document, decoded)
    }

    @Test
    fun toString_containsTheTitle() {
        assertEquals(true, document.toString().contains("Bescheinigung"))
    }
}
