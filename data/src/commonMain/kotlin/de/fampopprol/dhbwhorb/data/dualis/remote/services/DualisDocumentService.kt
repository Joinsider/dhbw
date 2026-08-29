/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.core.error.map
import de.fampopprol.dhbwhorb.data.dualis.demo.DemoDataProvider
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.dualis.remote.DownloadedBytes
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.DocumentParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import io.github.aakira.napier.Napier

/**
 * Lists and downloads the documents Dualis publishes for a student.
 */
class DualisDocumentService(
    private val apiClient: DualisApiClient,
    private val sessionManager: SessionManager,
    private val reAuthenticator: ReAuthenticator,
    private val gateway: DualisPageGateway,
    private val documentParser: DocumentParser = DocumentParser(),
    private val htmlParser: HtmlParser = HtmlParser()
) {
    companion object {
        private const val TAG = "DualisDocumentService"
        private const val BASE_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val DUALIS_ORIGIN = "https://dualis.dhbw.de"
    }

    suspend fun fetchDocuments(): Outcome<List<DualisDocument>> {
        if (sessionManager.isDemoMode()) {
            Napier.d("Returning demo documents", tag = TAG)
            return Outcome.Ok(DemoDataProvider.demoDocuments())
        }

        val html = gateway.fetchPage(
            source = "documents",
            isValid = ::isValidDocumentPage,
            buildUrl = { auth -> "$BASE_URL?APPNAME=CampusNet&PRGNAME=CREATEDOCUMENT&ARGUMENTS=-N${auth.sessionId},-N000339" }
        )

        return html.map { documentParser.parseDocuments(it) }
    }

    /**
     * Download the bytes behind [url].
     *
     * The download endpoint does answer with 401 when the session is gone, unlike the HTML pages,
     * so one re-authentication and retry is worth it here.
     */
    suspend fun downloadDocument(url: String): Outcome<ByteArray> {
        if (sessionManager.isDemoMode()) {
            // The demo documents carry their own generated PDF, so the whole download path —
            // including the platform's viewer and save dialog — works without a Dualis account.
            val content = DemoDataProvider.demoDocumentContent(url)
                ?: return Outcome.Err(AppError.Unsupported("Unknown demo document: $url"))
            Napier.d("Serving demo document (${content.size} bytes)", tag = TAG)
            return Outcome.Ok(content)
        }

        // Dualis hands out relative paths like /scripts/filetransfer.exe?…
        val absoluteUrl = if (url.startsWith("/")) "$DUALIS_ORIGIN$url" else url

        return downloadWithRetry(absoluteUrl, retried = false)
    }

    private suspend fun downloadWithRetry(url: String, retried: Boolean): Outcome<ByteArray> {
        if (!sessionManager.isAuthenticated()) {
            when (val reAuth = reAuthenticator.reAuthenticate()) {
                is Outcome.Ok -> Unit
                is Outcome.Err -> return reAuth
            }
        }

        val authData = sessionManager.getAuthData()
        if (authData == null || authData.sessionId.isEmpty()) {
            return Outcome.Err(AppError.SessionExpired)
        }

        val cookie = authData.cookie?.substringBefore(";")
        return when (val result = apiClient.getRawBytes(url, cookie)) {
            is Outcome.Ok -> {
                // A 200 that is a page rather than a file means the session timed out: Dualis
                // says so in HTML and in the status code says nothing at all. Handing those bytes
                // on would save its timeout notice as the student's certificate.
                if (DownloadedBytes.looksLikeHtmlPage(result.value)) {
                    if (retried) {
                        Napier.w("Download still answered with a page after re-authenticating", tag = TAG)
                        Outcome.Err(AppError.SessionExpired)
                    } else {
                        Napier.w("Download answered with a page, re-authenticating once", tag = TAG)
                        when (val reAuth = reAuthenticator.reAuthenticate()) {
                            is Outcome.Ok -> downloadWithRetry(url, retried = true)
                            is Outcome.Err -> reAuth
                        }
                    }
                } else {
                    result
                }
            }
            is Outcome.Err -> {
                if (result.error is AppError.SessionExpired && !retried) {
                    Napier.w("Download rejected, re-authenticating once", tag = TAG)
                    when (val reAuth = reAuthenticator.reAuthenticate()) {
                        is Outcome.Ok -> downloadWithRetry(url, retried = true)
                        is Outcome.Err -> reAuth
                    }
                } else {
                    result
                }
            }
        }
    }

    /** The document list is a `class="tb"` table; a redirect page is not one. */
    private fun isValidDocumentPage(htmlContent: String): Boolean =
        htmlContent.contains("class=\"tb\"", ignoreCase = true) && !htmlParser.isRedirectPage(htmlContent)
}
