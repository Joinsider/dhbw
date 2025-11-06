package de.joinside.dhbw.data.dualis.remote

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Dualis API client - Main HTTP client for all Dualis API requests.
 * This client is responsible ONLY for executing HTTP requests and returning HTML responses.
 * It does NOT handle parsing - that is done by dedicated parsers.
 *
 * Session management and re-authentication should be handled by the calling service.
 *
 * This client is used by all Dualis services (lecture service, grades service, etc.).
 * Rate limiting can be added here in the future.
 *
 * IMPORTANT: Pass the same HttpClient instance used by AuthenticationService to share cookies!
 */
class DualisApiClient(
    private val client: HttpClient
) {
    companion object {
        private const val TAG = "DualisApiClient"

        /**
         * Create a new DualisApiClient with a default HttpClient configuration.
         * For production use, prefer passing the same HttpClient used by AuthenticationService.
         */
        fun createDefault(): DualisApiClient {
            val client = HttpClient {
                expectSuccess = false
                install(HttpCookies)
            }
            return DualisApiClient(client)
        }
    }

    /**
     * Execute a GET request to Dualis and return the HTML response.
     * This method does NO parsing - it only fetches and returns the raw HTML.
     *
     * @param url The URL to request
     * @param urlParameters Query parameters for the request
     * @return ApiResult containing HTML response or error
     */
    suspend fun get(url: String, urlParameters: Map<String, String> = emptyMap()): ApiResult {
        try {
            Napier.d("Executing GET request to: $url", tag = TAG)
            if (urlParameters.isNotEmpty()) {
                Napier.d("Parameters: ${urlParameters.keys.joinToString(", ")}", tag = TAG)
            }

            val response = client.get(url) {
                urlParameters.forEach { (key, value) ->
                    parameter(key, value)
                }
            }

            if (!response.status.isSuccess()) {
                Napier.e("Request failed with status: ${response.status}", tag = TAG)
                return ApiResult.Failure("HTTP ${response.status.value}: ${response.status.description}")
            }

            val htmlContent = response.bodyAsText()
            Napier.d("Request successful, response length: ${htmlContent.length} characters", tag = TAG)

            return ApiResult.Success(htmlContent)

        } catch (e: Exception) {
            Napier.e("Request failed with exception: ${e.message}", e, tag = TAG)
            return ApiResult.Failure("Network error: ${e.message}")
        }
    }

    /**
     * Result wrapper for API responses.
     * Contains either the HTML content or an error message.
     */
    sealed class ApiResult {
        data class Success(val htmlContent: String) : ApiResult()
        data class Failure(val message: String) : ApiResult()
    }
}