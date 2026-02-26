package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthParserTest {

    private val authParser = AuthParser()

    @Test
    fun `extractAuthToken extracts token from URL with ARGUMENTS parameter`() {
        // Given
        val url = "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH&ARGUMENTS=-N123456789012345,-N000000000000000,-N000000000000000"

        // When
        val token = authParser.extractAuthToken(url)

        // Then
        assertNotNull(token)
        assertEquals("123456789012345", token)
    }

    @Test
    fun `extractAuthToken returns null when no ARGUMENTS in URL`() {
        // Given
        val url = "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet"

        // When
        val token = authParser.extractAuthToken(url)

        // Then
        assertNull(token)
    }

    @Test
    fun `extractAuthToken handles multiple tokens and extracts first one`() {
        // Given
        val url = "https://dualis.dhbw.de/scripts/mgrqispi.dll?ARGUMENTS=-N111111111111111,-N222222222222222,-N333333333333333"

        // When
        val token = authParser.extractAuthToken(url)

        // Then
        assertNotNull(token)
        assertEquals("111111111111111", token)
    }

    @Test
    fun `extractRedirectUrlFromHeader extracts URL from refresh header with URL prefix`() {
        // Given
        val header = "0; URL=/scripts/mgrqispi.dll?APPNAME=CampusNet"

        // When
        val url = authParser.extractRedirectUrlFromHeader(header)

        // Then
        assertNotNull(url)
        assertTrue(url.contains("/scripts/mgrqispi.dll"))
    }

    @Test
    fun `extractRedirectUrlFromHeader handles header without URL prefix`() {
        // Given
        val header = "/scripts/mgrqispi.dll?APPNAME=CampusNet"

        // When
        val url = authParser.extractRedirectUrlFromHeader(header)

        // Then
        assertNotNull(url)
        assertTrue(url.contains("/scripts/mgrqispi.dll"))
    }

    @Test
    fun `extractRedirectUrlFromHeader makes relative URL absolute`() {
        // Given
        val header = "0; URL=/scripts/test.dll"

        // When
        val url = authParser.extractRedirectUrlFromHeader(header)

        // Then
        assertNotNull(url)
        assertTrue(url.startsWith("https://dualis.dhbw.de"))
    }

    @Test
    fun `extractRedirectUrlFromHtml extracts URL from meta refresh tag`() {
        // Given
        val html = """
            <html>
            <head>
                <meta http-equiv="refresh" content="0; URL=/scripts/next.dll">
            </head>
            <body></body>
            </html>
        """.trimIndent()

        // When
        val url = authParser.extractRedirectUrlFromHtml(html, "https://dualis.dhbw.de")

        // Then
        assertNotNull(url)
        assertTrue(url.contains("/scripts/next.dll"))
    }

    @Test
    fun `extractRedirectUrlFromHtml returns null when no meta refresh tag`() {
        // Given
        val html = """
            <html>
            <head>
                <title>Test Page</title>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        // When
        val url = authParser.extractRedirectUrlFromHtml(html, "https://dualis.dhbw.de")

        // Then
        assertNull(url)
    }

    @Test
    fun `extractRedirectUrlFromHtml handles various meta tag formats`() {
        // Given - single quotes
        val html1 = """<meta http-equiv='refresh' content='0; URL=/test.dll'>"""

        // When
        val url1 = authParser.extractRedirectUrlFromHtml(html1, "https://dualis.dhbw.de")

        // Then
        assertNotNull(url1)
        assertTrue(url1.contains("/test.dll"))
    }

    @Test
    fun `isRedirectPage returns true for page with meta refresh`() {
        // Given
        val html = """
            <html>
            <head>
                <meta http-equiv="refresh" content="0; URL=/next.dll">
            </head>
            </html>
        """.trimIndent()

        // When
        val isRedirect = authParser.isRedirectPage(html)

        // Then
        assertTrue(isRedirect)
    }

    @Test
    fun `isRedirectPage returns false for page without meta refresh`() {
        // Given
        val html = """
            <html>
            <head>
                <title>Normal Page</title>
            </head>
            <body>Content</body>
            </html>
        """.trimIndent()

        // When
        val isRedirect = authParser.isRedirectPage(html)

        // Then
        assertFalse(isRedirect)
    }

    @Test
    fun `isMainPage returns true for page with STARTPAGE`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>STARTPAGE</h1>
                <div>Welcome</div>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = authParser.isMainPage(html)

        // Then
        assertTrue(isMain)
    }

    @Test
    fun `isMainPage returns true for page with Notenspiegel`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Notenspiegel</h1>
                <table>Grades</table>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = authParser.isMainPage(html)

        // Then
        assertTrue(isMain)
    }

    @Test
    fun `isMainPage returns true for page with Pruefungsergebnisse`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Prüfungsergebnisse</h1>
                <div>Exam results</div>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = authParser.isMainPage(html)

        // Then
        assertTrue(isMain)
    }

    @Test
    fun `isMainPage returns false for redirect page`() {
        // Given
        val html = """
            <html>
            <head>
                <meta http-equiv="refresh" content="0; URL=/next.dll">
            </head>
            <body>Redirecting...</body>
            </html>
        """.trimIndent()

        // When
        val isMain = authParser.isMainPage(html)

        // Then
        assertFalse(isMain)
    }

    @Test
    fun `isMainPage returns false for normal page without indicators`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Some Page</h1>
                <div>Content</div>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = authParser.isMainPage(html)

        // Then
        assertFalse(isMain)
    }

    @Test
    fun `makeAbsoluteUrl handles absolute URLs`() {
        // Given
        val absoluteUrl = "https://example.com/path"

        // When - using reflection to access private method (or we can test through public methods)
        val url = authParser.extractRedirectUrlFromHeader(absoluteUrl)

        // Then
        assertNotNull(url)
        assertEquals(absoluteUrl, url)
    }

    @Test
    fun `makeAbsoluteUrl handles relative URLs with leading slash`() {
        // Given
        val header = "/scripts/test.dll"

        // When
        val url = authParser.extractRedirectUrlFromHeader(header)

        // Then
        assertNotNull(url)
        assertTrue(url.startsWith("https://dualis.dhbw.de/"))
    }
}

