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

    /** The outcome of a single attempt: either the loop is done, or it should retry. */
    private sealed interface AttemptOutcome {
        data class Done(val outcome: Outcome<String>) : AttemptOutcome
        data object Retry : AttemptOutcome
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

            when (val result = attemptFetch(source, isValid, buildUrl, lastAttempt)) {
                is AttemptOutcome.Done -> return result.outcome
                AttemptOutcome.Retry -> attempt++
            }
        }
    }

    /** One pass: re-authenticate if needed, fetch the page and validate it. */
    private suspend fun attemptFetch(
        source: String,
        isValid: (String) -> Boolean,
        buildUrl: (AuthData) -> String,
        lastAttempt: Boolean
    ): AttemptOutcome {
        reAuthenticateIfUnauthenticated()?.let { return AttemptOutcome.Done(it) }

        val authData = sessionManager.getAuthData()
        if (authData == null || authData.sessionId.isEmpty()) {
            return retryOrExpire(source, lastAttempt)
        }

        val html = when (val response = fetchHtml(authData, buildUrl)) {
            is Outcome.Ok -> response.value
            is Outcome.Err -> {
                val retryable = response.error is AppError.SessionExpired
                if (!retryable || lastAttempt) return AttemptOutcome.Done(response)
                reAuthenticateOrErr()?.let { return AttemptOutcome.Done(it) }
                return AttemptOutcome.Retry
            }
        }

        if (isValid(html) && !htmlParser.isErrorPage(html)) {
            return AttemptOutcome.Done(Outcome.Ok(html))
        }

        return handleUnexpectedPage(source, html, lastAttempt)
    }

    /** GET the page; the stored cookie is the raw Set-Cookie header, trimmed to name=value. */
    private suspend fun fetchHtml(
        authData: AuthData,
        buildUrl: (AuthData) -> String
    ): Outcome<String> {
        val cookie = authData.cookie?.substringBefore(";")
        return apiClient.get(buildUrl(authData), emptyMap(), cookie)
    }

    /** No usable session id: give up on the last attempt, otherwise re-authenticate and retry. */
    private suspend fun retryOrExpire(source: String, lastAttempt: Boolean): AttemptOutcome {
        Napier.w("No usable session id for $source", tag = TAG)
        if (lastAttempt) return AttemptOutcome.Done(Outcome.Err(AppError.SessionExpired))
        reAuthenticateOrErr()?.let { return AttemptOutcome.Done(it) }
        return AttemptOutcome.Retry
    }

    /**
     * A fresh session still did not produce the expected page. An error page means the account
     * cannot reach this content; anything else means Dualis answers with something we no longer
     * recognise, which is a scraping problem, not a session one.
     */
    private suspend fun handleUnexpectedPage(
        source: String,
        html: String,
        lastAttempt: Boolean
    ): AttemptOutcome {
        val title = htmlParser.extractTitle(html)
        Napier.w("Unexpected page for $source (title: '$title')", tag = TAG)

        if (!lastAttempt) {
            reAuthenticateOrErr()?.let { return AttemptOutcome.Done(it) }
            return AttemptOutcome.Retry
        }

        val outcome = if (htmlParser.isErrorPage(html)) {
            Outcome.Err(AppError.SessionExpired)
        } else {
            Outcome.Err(AppError.Parse(source, "unexpected page, title: '$title'"))
        }
        return AttemptOutcome.Done(outcome)
    }

    private suspend fun reAuthenticateIfUnauthenticated(): Outcome.Err? {
        if (sessionManager.isAuthenticated()) return null
        return reAuthenticateOrErr()
    }

    private suspend fun reAuthenticateOrErr(): Outcome.Err? =
        when (val reAuth = reAuthenticator.reAuthenticate()) {
            is Outcome.Ok -> null
            is Outcome.Err -> reAuth
        }
}
