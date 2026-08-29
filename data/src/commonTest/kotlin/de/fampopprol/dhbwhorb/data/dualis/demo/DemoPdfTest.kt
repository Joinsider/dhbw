/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The parts of a PDF a reader actually refuses to open a file over: the header, the trailer, and
 * the cross-reference table whose numbers have to be the true byte offsets of the objects.
 */
class DemoPdfTest {

    private val pdf = DemoPdf.render(
        title = "Studienbescheinigung",
        lines = listOf("Max Mustermann", "", "Semesterbeitrag (bezahlt)", "Grüße aus Horb")
    )

    private val text = pdf.decodeToString()

    @Test
    fun itIsAPdfFromStartToEnd() {
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("/Type /Catalog"))
        assertTrue(text.contains("/BaseFont /Helvetica"))
        assertTrue(text.contains("/BaseFont /Courier"))
    }

    @Test
    fun everyCrossReferenceEntryPointsAtItsObject() {
        val offsets = Regex("""^(\d{10}) 00000 n $""", RegexOption.MULTILINE)
            .findAll(text)
            .map { it.groupValues[1].toInt() }
            .toList()

        val objects = Regex("""^(\d+) 0 obj$""", RegexOption.MULTILINE).findAll(text).count()
        assertEquals(objects, offsets.size, "one entry per object, and object 0 is the free one")
        assertEquals("0 ${objects + 1}", text.substringAfter("xref\n").substringBefore("\n"))
        offsets.forEachIndexed { index, offset ->
            val declaration = "${index + 1} 0 obj"
            assertEquals(
                declaration,
                text.substring(offset, offset + declaration.length),
                "the xref entry for object ${index + 1} points somewhere else"
            )
        }
    }

    @Test
    fun startxrefPointsAtTheCrossReferenceTable() {
        val startxref = text.substringAfterLast("startxref").trim().substringBefore("\n").toInt()

        assertEquals("xref", text.substring(startxref, startxref + 4))
    }

    @Test
    fun theStreamLengthIsTheNumberOfBytesInIt() {
        val declared = Regex("""/Length (\d+)""").find(text)!!.groupValues[1].toInt()
        val stream = text.substringAfter("stream\n").substringBefore("\nendstream")

        assertEquals(declared, stream.length)
    }

    @Test
    fun textIsWrittenInTheEncodingTheFontDeclares() {
        // WinAnsi, so an umlaut is one byte — as UTF-8 it would be two, and readers would show
        // "GrÃ¼ÃŸe". The bytes are checked rather than the string, because decodeToString()
        // reads them back as UTF-8 and would hide exactly this.
        assertTrue(text.contains("/Encoding /WinAnsiEncoding"))
        assertTrue(pdf.contains(0xFC.toByte()), "ü must be a single WinAnsi byte")
    }

    @Test
    fun bracketsInTheTextAreEscaped() {
        // An unescaped ( ends the string literal early and corrupts the page.
        assertTrue(text.contains("Semesterbeitrag \\(bezahlt\\)"))
    }
}
