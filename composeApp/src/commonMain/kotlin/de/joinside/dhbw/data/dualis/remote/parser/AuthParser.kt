package de.joinside.dhbw.data.dualis.remote.parser

import io.github.aakira.napier.Napier

/**
 * Parser for authentication-related HTML responses.
 * This class is responsible for extracting authentication tokens,
 * session IDs, and other relevant data from the HTML content
 * returned by the Dualis system during the login process.
 */
class AuthParser {

    companion object {
        private const val TAG = "AuthParser"
        private const val DUALIS_ENDPOINT = "https://dualis.dhbw.de"
    }

    /**
     * Extract auth token from a URL.
     * The auth token is typically in the format: -N[token_value]
     * Example URL: https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH&ARGUMENTS=-N123456789012345,-N000000000000000,-N000000000000000
     */
    fun extractAuthToken(url: String): String? {
        Napier.d("Extracting auth token from URL: $url", tag = TAG)

        val arguments = url.substringAfter("ARGUMENTS=", "")
        if (arguments.isEmpty()) {
            Napier.w("No ARGUMENTS found in URL", tag = TAG)
            return null
        }

        // The first -N parameter is typically the auth token
        val tokens = arguments.split(",")
        if (tokens.isNotEmpty()) {
            val authToken = tokens[0].removePrefix("-N")
            Napier.d("Extracted auth token: $authToken", tag = TAG)
            return authToken
        }

        Napier.w("Could not extract auth token from ARGUMENTS", tag = TAG)
        return null
    }

    /**
     * Extract redirect URL from refresh header value.
     * Format: "0; URL=relative/path" or just the relative path
     */
    fun extractRedirectUrlFromHeader(refreshHeader: String): String? {
        Napier.d("Extracting redirect URL from header: $refreshHeader", tag = TAG)

        val redirectUrlPart = if (refreshHeader.contains("URL=")) {
            refreshHeader.substringAfter("URL=")
        } else {
            refreshHeader
        }

        val absoluteUrl = makeAbsoluteUrl(DUALIS_ENDPOINT, redirectUrlPart)
        Napier.d("Extracted absolute redirect URL: $absoluteUrl", tag = TAG)
        return absoluteUrl
    }

    /**
     * Extract redirect URL from HTML meta refresh tag.
     * Looks for: <meta http-equiv="refresh" content="0; URL=...">
     */
    fun extractRedirectUrlFromHtml(htmlContent: String, baseUrl: String): String? {
        Napier.d("Extracting redirect URL from HTML", tag = TAG)

        // Look for meta refresh tag
        val metaRefreshPattern = """<meta[^>]*http-equiv\s*=\s*["']?refresh["']?[^>]*content\s*=\s*["']([^"']*)["'][^>]*>""".toRegex(RegexOption.IGNORE_CASE)
        val match = metaRefreshPattern.find(htmlContent)

        if (match != null) {
            val content = match.groupValues[1]
            val urlPart = content.substringAfter("URL=", "").trim()

            if (urlPart.isNotEmpty()) {
                val absoluteUrl = makeAbsoluteUrl(baseUrl, urlPart)
                Napier.d("Extracted redirect URL from HTML: $absoluteUrl", tag = TAG)
                return absoluteUrl
            }
        }

        Napier.w("Could not extract redirect URL from HTML", tag = TAG)
        return null
    }

    /**
     * Check if the HTML content is a redirect page.
     */
    fun isRedirectPage(htmlContent: String): Boolean {
        return htmlContent.contains("<meta", ignoreCase = true) &&
                htmlContent.contains("http-equiv", ignoreCase = true) &&
                htmlContent.contains("refresh", ignoreCase = true)
    }

    /**
     * Check if the HTML content is the main page after successful login.
     * The main page typically contains navigation elements and user info.
     */
    fun isMainPage(htmlContent: String): Boolean {
        // Look for typical main page indicators
        return (htmlContent.contains("STARTPAGE", ignoreCase = true) ||
                htmlContent.contains("Notenspiegel", ignoreCase = true) ||
                htmlContent.contains("Prüfungsergebnisse", ignoreCase = true)) &&
                !isRedirectPage(htmlContent)
    }

    /**
     * Make a relative URL absolute based on the base URL.
     */
    private fun makeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return when {
            relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://") -> {
                relativeUrl
            }
            relativeUrl.startsWith("/") -> {
                "$baseUrl$relativeUrl"
            }
            else -> {
                "$baseUrl/$relativeUrl"
            }
        }
    }
}