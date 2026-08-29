/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.error.httpStatusToAppError
import de.fampopprol.dhbwhorb.data.error.toAppError
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Executes HTTP requests against Dualis and returns the raw response. No parsing happens here —
 * that belongs to the parsers — and no session handling either, which belongs to the services.
 *
 * The [client] must be the same instance [de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService]
 * uses, because the session cookie lives in its cookie storage.
 */
class DualisApiClient(
    private val client: HttpClient
) {
    companion object {
        private const val TAG = "DualisApiClient"
    }

    /**
     * GET [url] and return the response body.
     *
     * A transport failure becomes [de.fampopprol.dhbwhorb.core.error.AppError.Offline] and a
     * non-2xx status becomes an [de.fampopprol.dhbwhorb.core.error.AppError.Http] — the caller
     * used to receive both as one opaque message string and could not tell them apart.
     */
    suspend fun get(
        url: String,
        urlParameters: Map<String, String> = emptyMap(),
        cookie: String? = null
    ): Outcome<String> {
        return try {
            Napier.d("Executing GET request to: $url", tag = TAG)

            val response = client.get(url) {
                urlParameters.forEach { (key, value) -> parameter(key, value) }
                // Dualis' Set-Cookie value carries attributes the Cookie header must not repeat;
                // callers hand in the already-trimmed "name=value" part.
                if (cookie != null) headers.append("Cookie", cookie)
            }

            if (!response.status.isSuccess()) {
                Napier.e("Request failed with status: ${response.status}", tag = TAG)
                return Outcome.Err(httpStatusToAppError(response.status.value))
            }

            val htmlContent = response.bodyAsText()
            Napier.d("Request successful, response length: ${htmlContent.length} characters", tag = TAG)
            Outcome.Ok(htmlContent)
        } catch (e: Exception) {
            Napier.e("Request failed with exception: ${e.message}", e, tag = TAG)
            Outcome.Err(e.toAppError(url))
        }
    }

    /** GET [url] and return the response body unparsed, for file downloads. */
    suspend fun getRawBytes(url: String, cookie: String?): Outcome<ByteArray> {
        return try {
            Napier.d("Executing GET request for raw bytes to: $url", tag = TAG)

            val response = client.get(url) {
                if (cookie != null) headers.append("Cookie", cookie)
            }

            if (!response.status.isSuccess()) {
                Napier.e("Request for raw bytes failed with status: ${response.status}", tag = TAG)
                return Outcome.Err(httpStatusToAppError(response.status.value))
            }

            val bytes = response.body<ByteArray>()
            Napier.d("Raw bytes request successful, response length: ${bytes.size} bytes", tag = TAG)
            Outcome.Ok(bytes)
        } catch (e: Exception) {
            Napier.e("Request for raw bytes failed with exception: ${e.message}", e, tag = TAG)
            Outcome.Err(e.toAppError(url))
        }
    }
}
