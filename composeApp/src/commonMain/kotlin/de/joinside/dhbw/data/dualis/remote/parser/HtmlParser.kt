package de.joinside.dhbw.data.dualis.remote.parser

import io.github.aakira.napier.Napier

/**
 * General HTML parser for Dualis pages.
 * Provides common utilities for parsing various Dualis HTML responses.
 */
class HtmlParser {

    companion object {
        private const val TAG = "HtmlParser"
    }

    /**
     * Check if the HTML content is a redirect page.
     */
    fun isRedirectPage(htmlContent: String): Boolean {
        val isRedirect = htmlContent.contains("<meta", ignoreCase = true) &&
                htmlContent.contains("http-equiv", ignoreCase = true) &&
                htmlContent.contains("refresh", ignoreCase = true)

        Napier.d("Is redirect page: $isRedirect", tag = TAG)
        return isRedirect
    }

    /**
     * Check if the HTML content is the main page after successful login.
     * The main page typically contains navigation elements and user info.
     */
    fun isMainPage(htmlContent: String): Boolean {
        val isMain = (htmlContent.contains("STARTPAGE", ignoreCase = true) ||
                htmlContent.contains("Notenspiegel", ignoreCase = true) ||
                htmlContent.contains("Prüfungsergebnisse", ignoreCase = true)) &&
                !isRedirectPage(htmlContent)

        Napier.d("Is main page: $isMain", tag = TAG)
        return isMain
    }

    /**
     * Extract title from HTML page for debugging.
     */
    fun extractTitle(htmlContent: String): String? {
        val titlePattern = """<title>(.*?)</title>""".toRegex(RegexOption.IGNORE_CASE)
        val match = titlePattern.find(htmlContent)
        return match?.groupValues?.get(1)?.trim()
    }
}