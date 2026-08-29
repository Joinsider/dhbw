/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import io.github.aakira.napier.Napier

/**
 * Fetches an authenticated Dualis page, re-authenticating once if the session turns out to be
 * unusable.
 *
 * The timetable, grade and document services each carried their own copy of this loop, and the
 * three copies had drifted: only one of them checked for an empty session id, and each turned a
 * failure into a differently worded `Exception`. One implementation, one classification.
 */
class DualisPageGateway(
    private val apiClient: DualisApiClient,
    private val sessionManager: SessionManager,
    private val reAuthenticator: ReAuthenticator,
    private val htmlParser: HtmlParser = HtmlParser()
) {
    private companion object {
        const val TAG = "DualisPageGateway"

        /** One retry: if a fresh session still yields the wrong page, retrying will not help. */
        const val MAX_ATTEMPTS = 2
    }

    /**
     * GET the page [buildUrl] produces and hand back its HTML.
     *
     * @param source what is being loaded, used in [AppError.Parse]
     * @param isValid recognises the expected page; anything else triggers a re-login and, if that
     *   does not help, a classified failure rather than an empty list
     */
    suspend fun fetchPage(
        source: String,
        isValid: (String) -> Boolean,
        buildUrl: (AuthData) -> String
    ): Outcome<String> {
        var attempt = 0

        while (true) {
            val lastAttempt = attempt >= MAX_ATTEMPTS - 1

            if (!sessionManager.isAuthenticated()) {
                when (val reAuth = reAuthenticator.reAuthenticate()) {
                    is Outcome.Ok -> Unit
                    is Outcome.Err -> return reAuth
                }
            }

            val authData = sessionManager.getAuthData()
            if (authData == null || authData.sessionId.isEmpty()) {
                Napier.w("No usable session id for $source", tag = TAG)
                if (lastAttempt) return Outcome.Err(AppError.SessionExpired)
                when (val reAuth = reAuthenticator.reAuthenticate()) {
                    is Outcome.Ok -> Unit
                    is Outcome.Err -> return reAuth
                }
                attempt++
                continue
            }

            // The stored value is the raw Set-Cookie header; the Cookie header takes only the
            // name=value part in front of the first attribute.
            val cookie = authData.cookie?.substringBefore(";")

            val html = when (val response = apiClient.get(buildUrl(authData), emptyMap(), cookie)) {
                is Outcome.Ok -> response.value
                is Outcome.Err -> {
                    val retryable = response.error is AppError.SessionExpired
                    if (!retryable || lastAttempt) return response
                    when (val reAuth = reAuthenticator.reAuthenticate()) {
                        is Outcome.Ok -> Unit
                        is Outcome.Err -> return reAuth
                    }
                    attempt++
                    continue
                }
            }

            if (isValid(html) && !htmlParser.isErrorPage(html)) return Outcome.Ok(html)

            val title = htmlParser.extractTitle(html)
            Napier.w("Unexpected page for $source (title: '$title')", tag = TAG)

            if (!lastAttempt) {
                when (val reAuth = reAuthenticator.reAuthenticate()) {
                    is Outcome.Ok -> Unit
                    is Outcome.Err -> return reAuth
                }
                attempt++
                continue
            }

            // A fresh session still did not produce the expected page. An error page means the
            // account cannot reach this content; anything else means Dualis answers with
            // something we no longer recognise, which is a scraping problem, not a session one.
            return if (htmlParser.isErrorPage(html)) {
                Outcome.Err(AppError.SessionExpired)
            } else {
                Outcome.Err(AppError.Parse(source, "unexpected page, title: '$title'"))
            }
        }
    }
}
