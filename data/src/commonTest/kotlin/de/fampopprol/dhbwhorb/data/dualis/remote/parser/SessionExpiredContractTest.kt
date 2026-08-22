/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DualisFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Dualis answers an expired session with the login page and HTTP 200, not with 401.
 * Every parser therefore has to recognise that page as "not my content" — otherwise the
 * failure reaches the user as silently empty data instead of a prompt to log in again.
 */
class SessionExpiredContractTest {

    private val htmlParser = HtmlParser()
    private val authParser = AuthParser()
    private val documentParser = DocumentParser()

    @Test
    fun htmlParser_doesNotTreatExpiredSessionAsMainPage() {
        assertFalse(htmlParser.isMainPage(DualisFixtures.SESSION_EXPIRED))
    }

    @Test
    fun htmlParser_doesNotTreatExpiredSessionAsTimetable() {
        assertFalse(htmlParser.isValidTimetablePage(DualisFixtures.SESSION_EXPIRED))
    }

    @Test
    fun htmlParser_doesNotTreatExpiredSessionAsGradePage() {
        assertFalse(htmlParser.isValidGradePage(DualisFixtures.SESSION_EXPIRED))
    }

    @Test
    fun authParser_doesNotTreatExpiredSessionAsMainPage() {
        assertFalse(authParser.isMainPage(DualisFixtures.SESSION_EXPIRED))
    }

    @Test
    fun documentParser_returnsNoDocumentsForExpiredSession() {
        assertEquals(emptyList(), documentParser.parseDocuments(DualisFixtures.SESSION_EXPIRED))
    }

    @Test
    fun documentParser_returnsNoDocumentsForEmptyInput() {
        assertEquals(emptyList(), documentParser.parseDocuments(DualisFixtures.EMPTY))
    }

    @Test
    fun documentParser_returnsNoDocumentsForNonHtmlInput() {
        assertEquals(emptyList(), documentParser.parseDocuments(DualisFixtures.NOT_HTML))
    }

    @Test
    fun htmlParser_survivesNonHtmlInput() {
        assertFalse(htmlParser.isMainPage(DualisFixtures.NOT_HTML))
        assertFalse(htmlParser.isValidTimetablePage(DualisFixtures.NOT_HTML))
        assertFalse(htmlParser.isValidGradePage(DualisFixtures.NOT_HTML))
    }
}
