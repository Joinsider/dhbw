/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote

/**
 * Telling a downloaded document from a Dualis page that arrived in its place.
 *
 * The download endpoint answers an expired session with HTTP 200 and its "Timeout! Es wurde seit
 * den letzten 30 Minuten keine Abfrage mehr abgesetzt" page, not with 401. Nothing in the status
 * code says anything is wrong, so the bytes have to be looked at: a page saved as
 * "Zahlungsinformation Semesterbeiträge.pdf" is what the preview then refuses to open.
 *
 * Dualis only ever offers PDFs here, so HTML in this position is never the document.
 */
object DownloadedBytes {

    /** Enough to see the doctype; a PDF's header is in its first bytes too. */
    private const val SNIFF_LENGTH = 512

    fun looksLikeHtmlPage(bytes: ByteArray): Boolean {
        val head = bytes
            .copyOf(minOf(SNIFF_LENGTH, bytes.size))
            .decodeToString()
            .removePrefix("﻿")
            .trimStart()
            .lowercase()

        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }
}
