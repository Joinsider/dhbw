/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote

import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DownloadFixtures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadedBytesTest {

    @Test
    fun theSessionTimeoutPageIsRecognised() {
        assertTrue(DownloadedBytes.looksLikeHtmlPage(DownloadFixtures.SESSION_TIMEOUT_PAGE))
    }

    @Test
    fun aPdfIsNotAPage() {
        assertFalse(DownloadedBytes.looksLikeHtmlPage(DownloadFixtures.PDF_HEADER))
    }

    @Test
    fun leadingWhitespaceAndCaseDoNotHideAPage() {
        assertTrue(DownloadedBytes.looksLikeHtmlPage("\n\n   <HTML><body>Fehler</body></HTML>".encodeToByteArray()))
    }

    @Test
    fun bytesThatAreNotTextAreNotAPage() {
        // A PDF is full of sequences that are not valid UTF-8; sniffing must survive them.
        val binary = ByteArray(600) { (it * 37).toByte() }

        assertFalse(DownloadedBytes.looksLikeHtmlPage(binary))
    }

    @Test
    fun anEmptyDownloadIsNotAPage() {
        assertFalse(DownloadedBytes.looksLikeHtmlPage(ByteArray(0)))
    }

    @Test
    fun htmlFurtherDownDoesNotCount() {
        // A PDF may well contain the word "<html>" somewhere in an embedded stream; only the
        // start of the file decides.
        val pdf = DownloadFixtures.PDF_HEADER + "<html>not the document's doing</html>".encodeToByteArray()

        assertFalse(DownloadedBytes.looksLikeHtmlPage(pdf))
    }
}
