/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures

/**
 * What Dualis sends from the download endpoint when the session has timed out.
 *
 * Trimmed from the page a real download actually returned — with HTTP 200, which is the whole
 * problem: nothing but the bytes says this is not the requested PDF.
 */
object DownloadFixtures {

    val SESSION_TIMEOUT_PAGE: ByteArray = """
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="de" lang="de"><head>
        <title>Duale Hochschule Baden-Württemberg</title>
        </head>
        <body>
        <div id="pageContainer">
        <h1>Timeout!</h1>
        <p>Es wurde seit den letzten 30&nbsp;Minuten keine Abfrage mehr abgesetzt.
        Bitte melden Sie sich erneut an.</p>
        </div>
        </body></html>
    """.trimIndent().encodeToByteArray()

    /** The first bytes of a real PDF, which is what a working download starts with. */
    val PDF_HEADER: ByteArray = "%PDF-1.7\n%âãÏÓ\n1 0 obj\n".encodeToByteArray()
}
