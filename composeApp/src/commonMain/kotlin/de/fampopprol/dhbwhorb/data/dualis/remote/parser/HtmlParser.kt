package de.fampopprol.dhbwhorb.data.dualis.remote.parser

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
        // Look specifically for <meta http-equiv="refresh" ... content="...URL=..."> patterns
        val refreshMetaPattern =
            """<meta[^>]*http-equiv\s*=\s*['"]?refresh['"]?[^>]*content\s*=\s*['"]?\s*\d+\s*;\s*url\s*=\s*[^'">]+['"]?""".toRegex(
                RegexOption.IGNORE_CASE
            )
        val isRedirect = refreshMetaPattern.containsMatchIn(htmlContent)

        Napier.d("Is redirect page: $isRedirect", tag = TAG)
        return isRedirect
    }

    /**
     * Check if the HTML content is the main page after successful login.
     * The main page typically contains the welcome message with the user's name.
     */
    fun isMainPage(htmlContent: String): Boolean {
        // Check for the welcome message which appears on the actual main page (MLSSTART)
        val hasWelcomeMessage =
            htmlContent.contains("Herzlich willkommen,", ignoreCase = true) || htmlContent.contains(
                "Welcome,",
                ignoreCase = true
            )

        // Also check for other main page indicators as fallback
        val hasMainPageIndicators = htmlContent.contains("STARTPAGE", ignoreCase = true) ||
                htmlContent.contains("Home", ignoreCase = true) ||
                htmlContent.contains("Prüfungsergebnisse", ignoreCase = true) ||
                htmlContent.contains("Notenspiegel", ignoreCase = true)

        val isMain = (hasWelcomeMessage || hasMainPageIndicators) && !isRedirectPage(htmlContent)

        Napier.d(
            "Is main page: $isMain (hasWelcome: $hasWelcomeMessage, hasIndicators: $hasMainPageIndicators)",
            tag = TAG
        )
        return isMain
    }

    /**
     * Extract title from HTML page for debugging.
     */
    fun extractTitle(htmlContent: String): String? {
        // Use [\s\S] instead of . to match any character including newlines
        val titlePattern = """<title>([\s\S]*?)</title>""".toRegex(RegexOption.IGNORE_CASE)
        val match = titlePattern.find(htmlContent)
        val titleText = match?.groupValues?.get(1)?.trim()
        Napier.d("Extracted title: $titleText", tag = TAG)
        return titleText
    }

    /**
     * Extract user's full name from the main page welcome message.
     * Looks for pattern: <h1>Herzlich willkommen, \[Name]!</h1>
     */
    fun extractUserFullName(htmlContent: String): String? {
        Napier.d("Searching for welcome message in HTML content", tag = TAG)

        // Check if the content contains the welcome phrase
        val hasWelcome = htmlContent.contains(
            "Herzlich willkommen",
            ignoreCase = true
        ) || htmlContent.contains("Welcome", ignoreCase = true)
        Napier.d("HTML contains 'Herzlich willkommen': $hasWelcome", tag = TAG)

        if (hasWelcome) {
            // Find the surrounding context
            val welcomeIndex = htmlContent.indexOf("Herzlich willkommen", ignoreCase = true)
                .takeIf { it >= 0 } ?: htmlContent.indexOf("Welcome", ignoreCase = true)
            val contextStart = maxOf(0, welcomeIndex - 50)
            val contextEnd = minOf(htmlContent.length, welcomeIndex + 150)
            val context = htmlContent.substring(contextStart, contextEnd)
            Napier.d("Welcome message context: $context", tag = TAG)
        }

        val namePattern =
            """<h1>\s*(?:Herzlich willkommen|Welcome),\s*([^!<]+)!\s*</h1>""".toRegex(RegexOption.IGNORE_CASE)
        val match = namePattern.find(htmlContent)

        if (match != null) {
            val fullName = match.groupValues[1].trim()
            Napier.d("✓ Regex matched! Extracted user full name: '$fullName'", tag = TAG)
            return fullName
        } else {
            Napier.w("✗ Regex pattern did not match in HTML content", tag = TAG)

            // Try alternative patterns for debugging and as fallback
            val altH1Pattern =
                """<h1>\s*(?:Herzlich willkommen|Welcome),\s*([^!<]+)!""".toRegex(RegexOption.IGNORE_CASE)
            val altH1Match = altH1Pattern.find(htmlContent)
            if (altH1Match != null) {
                val candidate = altH1Match.groupValues[1].trim()
                Napier.d("Alternative h1 pattern matched: '$candidate'", tag = TAG)
                return candidate
            }

            val simplePattern =
                """(?:Herzlich willkommen|Welcome),\s*([^!<]+)!""".toRegex(RegexOption.IGNORE_CASE)
            val simpleMatch = simplePattern.find(htmlContent)
            if (simpleMatch != null) {
                val candidate = simpleMatch.groupValues[1].trim()
                Napier.d("Alternative simple pattern matched: '$candidate'", tag = TAG)
                return candidate
            } else {
                Napier.d("Even simple pattern didn't match", tag = TAG)
            }

            return null
        }
    }

    /**
     * Check if page is an error page
     * This could include something like invalid, timeout, expired, missing credentials etc.
     * @returns true if page is an error page
     */
    fun isErrorPage(htmlContent: String): Boolean {
        // Check for the specific error page patterns from Dualis

        // 1. Check for "Zugang verweigert" (Access denied) - most common error
        if (htmlContent.contains("Zugang verweigert", ignoreCase = true)) {
            Napier.d("Error page detected: Zugang verweigert (Access denied)", tag = TAG)
            return true
        }

        // 2. Check for access_denied body class
        if (htmlContent.contains("class=\"access_denied\"", ignoreCase = true) ||
            htmlContent.contains("class='access_denied'", ignoreCase = true)) {
            Napier.d("Error page detected: access_denied body class", tag = TAG)
            return true
        }

        // 3. Check for other common German/English error patterns
        val errorPatterns = listOf(
            "fehler",                           // German for 'error'
            "ungültig",                         // Invalid in German
            "anmeldung fehlgeschlagen",         // Login failed in German
            "login failed",                     // Login failed
            "session.*ungültig",                // Invalid session in German
            "session.*expired",                 // Session expired
            "abgelaufen",                       // Expired in German
            "keine berechtigung",               // No permission in German
            "not authorized",                   // Not authorized
            "zugriff.*verweigert",              // Access denied pattern
            "access.*denied"                    // Access denied pattern
        )

        for (pattern in errorPatterns) {
            if (htmlContent.contains(pattern.toRegex(RegexOption.IGNORE_CASE))) {
                Napier.d("Error page detected: pattern '$pattern'", tag = TAG)
                return true
            }
        }

        Napier.d("No error patterns detected in page", tag = TAG)
        return false
    }

    /**
     * Check if a timetable/schedule page is valid by looking for expected content.
     * This helps detect session expired pages that don't have explicit error messages.
     * @returns true if the page appears to be a valid timetable page
     */
    fun isValidTimetablePage(htmlContent: String): Boolean {
        // A valid timetable page should have the weekday headers
        val hasWeekdayHeaders = htmlContent.contains("class=\"weekday\"", ignoreCase = false)

        // Should also have appointment cells
        val hasAppointments = htmlContent.contains("class=\"appointment\"", ignoreCase = false)

        // Should NOT be a redirect page
        val isNotRedirect = !isRedirectPage(htmlContent)

        val isValid = hasWeekdayHeaders && isNotRedirect

        if (!isValid) {
            Napier.w("Invalid timetable page: hasWeekdayHeaders=$hasWeekdayHeaders, hasAppointments=$hasAppointments, isNotRedirect=$isNotRedirect", tag = TAG)

            // Log what we found instead
            if (htmlContent.contains("login", ignoreCase = true)) {
                Napier.w("Page appears to be a login page (session likely expired)", tag = TAG)
            }
        } else {
            Napier.d("Valid timetable page detected", tag = TAG)
        }

        return isValid
    }

    /**
     * Check if a grade page is valid by looking for expected content.
     * @returns true if the page appears to be a valid grade page
     */
    fun isValidGradePage(htmlContent: String): Boolean {
        // A valid grade page should have the semester dropdown
        val hasSemesterDropdown = htmlContent.contains("id=\"semester\"", ignoreCase = true)

        // Should also have the grades table
        val hasGradesTable = htmlContent.contains("class=\"nb list\"", ignoreCase = true)
        
        // Or it might say no data available
        val hasNoDataMessage = htmlContent.contains("Keine Prüfungsdaten vorhanden", ignoreCase = true)

        // Should NOT be a redirect page
        val isNotRedirect = !isRedirectPage(htmlContent)

        val isValid = (hasSemesterDropdown || hasGradesTable || hasNoDataMessage) && isNotRedirect

        if (!isValid) {
            Napier.w("Invalid grade page: hasSemesterDropdown=$hasSemesterDropdown, hasGradesTable=$hasGradesTable, hasNoDataMessage=$hasNoDataMessage, isNotRedirect=$isNotRedirect", tag = TAG)
        } else {
            Napier.d("Valid grade page detected", tag = TAG)
        }

        return isValid
    }
}