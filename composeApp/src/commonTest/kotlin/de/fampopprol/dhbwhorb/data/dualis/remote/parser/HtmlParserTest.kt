package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlParserTest {

    private val htmlParser = HtmlParser()

    @Test
    fun `isRedirectPage returns true for meta refresh redirect`() {
        // Given
        val html = """
            <html>
            <head>
                <meta http-equiv="refresh" content="0; URL=/next.html">
            </head>
            <body></body>
            </html>
        """.trimIndent()

        // When
        val isRedirect = htmlParser.isRedirectPage(html)

        // Then
        assertTrue(isRedirect)
    }

    @Test
    fun `isRedirectPage returns true for case-insensitive meta tag`() {
        // Given
        val html = """
            <html>
            <head>
                <META HTTP-EQUIV="REFRESH" CONTENT="0; URL=/next.html">
            </head>
            <body></body>
            </html>
        """.trimIndent()

        // When
        val isRedirect = htmlParser.isRedirectPage(html)

        // Then
        assertTrue(isRedirect)
    }

    @Test
    fun `isRedirectPage returns false for normal page`() {
        // Given
        val html = """
            <html>
            <head>
                <title>Normal Page</title>
            </head>
            <body>
                <h1>Content</h1>
            </body>
            </html>
        """.trimIndent()

        // When
        val isRedirect = htmlParser.isRedirectPage(html)

        // Then
        assertFalse(isRedirect)
    }

    @Test
    fun `isMainPage returns true for page with STARTPAGE`() {
        // Given
        val html = """
            <html>
            <body>
                <div id="pageTitle">STARTPAGE</div>
                <nav>Navigation</nav>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = htmlParser.isMainPage(html)

        // Then
        assertTrue(isMain)
    }

    @Test
    fun `isMainPage returns true for page with Notenspiegel`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Notenspiegel - Grade Overview</h1>
                <table>
                    <tr><td>Grades</td></tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = htmlParser.isMainPage(html)

        // Then
        assertTrue(isMain)
    }

    @Test
    fun `isMainPage returns true for page with Pruefungsergebnisse`() {
        // Given
        val html = """
            <html>
            <body>
                <h2>Prüfungsergebnisse</h2>
                <div>Exam results content</div>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = htmlParser.isMainPage(html)

        // Then
        assertTrue(isMain)
    }

    @Test
    fun `isMainPage returns false for redirect page even with keywords`() {
        // Given - has keyword but is redirect
        val html = """
            <html>
            <head>
                <meta http-equiv="refresh" content="0; URL=/startpage.html">
            </head>
            <body>
                <div>STARTPAGE</div>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = htmlParser.isMainPage(html)

        // Then
        assertFalse(isMain)
    }

    @Test
    fun `isMainPage returns false for normal page without indicators`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Some Other Page</h1>
                <p>This is not a main page</p>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = htmlParser.isMainPage(html)

        // Then
        assertFalse(isMain)
    }

    @Test
    fun `extractTitle extracts title from HTML`() {
        // Given
        val html = """
            <html>
            <head>
                <title>Test Page Title</title>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        // When
        val title = htmlParser.extractTitle(html)

        // Then
        assertNotNull(title)
        assertEquals("Test Page Title", title)
    }

    @Test
    fun `extractTitle returns null when no title tag`() {
        // Given
        val html = """
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body></body>
            </html>
        """.trimIndent()

        // When
        val title = htmlParser.extractTitle(html)

        // Then
        assertNull(title)
    }

    @Test
    fun `extractTitle handles case-insensitive title tag`() {
        // Given
        val html = """
            <html>
            <head>
                <TITLE>Uppercase Title</TITLE>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        // When
        val title = htmlParser.extractTitle(html)

        // Then
        assertNotNull(title)
        assertEquals("Uppercase Title", title)
    }

    @Test
    fun `extractTitle trims whitespace from title`() {
        // Given
        val html = """
            <html>
            <head>
                <title>  
                    Whitespace Title  
                </title>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        // When
        val title = htmlParser.extractTitle(html)

        // Then
        assertNotNull(title)
        assertEquals("Whitespace Title", title)
    }

    @Test
    fun `isMainPage is case-insensitive for keywords`() {
        // Given
        val html1 = "<html><body>startpage</body></html>"
        val html2 = "<html><body>NOTENSPIEGEL</body></html>"
        val html3 = "<html><body>prüfungsergebnisse</body></html>"

        // When & Then
        assertTrue(htmlParser.isMainPage(html1))
        assertTrue(htmlParser.isMainPage(html2))
        assertTrue(htmlParser.isMainPage(html3))
    }

    @Test
    fun `isRedirectPage handles malformed HTML gracefully`() {
        // Given
        val html = "<html><meta http-equiv=refresh content=0>"

        // When
        val isRedirect = htmlParser.isRedirectPage(html)

        // Then
        assertTrue(isRedirect)
    }

    @Test
    fun `extractUserFullName returns full name from welcome message`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Herzlich willkommen, Johannes Popp!</h1>
            </body>
            </html>
        """.trimIndent()

        // When
        val fullName = htmlParser.extractUserFullName(html)

        // Then
        assertNotNull(fullName)
        assertEquals("Johannes Popp", fullName)
    }

    @Test
    fun `extractUserFullName returns null when welcome message not found`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Some other content</h1>
            </body>
            </html>
        """.trimIndent()

        // When
        val fullName = htmlParser.extractUserFullName(html)

        // Then
        assertNull(fullName)
    }

    @Test
    fun `extractUserFullName handles name with special characters`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Herzlich willkommen, Müller-Schmödt, Anna-Maria!</h1>
            </body>
            </html>
        """.trimIndent()

        // When
        val fullName = htmlParser.extractUserFullName(html)

        // Then
        assertNotNull(fullName)
        assertEquals("Müller-Schmödt, Anna-Maria", fullName)
    }

    @Test
    fun `extractUserFullName trims whitespace`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Herzlich willkommen,   Max Mustermann  !</h1>
            </body>
            </html>
        """.trimIndent()

        // When
        val fullName = htmlParser.extractUserFullName(html)

        // Then
        assertNotNull(fullName)
        assertEquals("Max Mustermann", fullName)
    }

    @Test
    fun `extractUserFullName is case-insensitive`() {
        // Given
        val html = """
            <html>
            <body>
                <H1>HERZLICH WILLKOMMEN, Test User!</H1>
            </body>
            </html>
        """.trimIndent()

        // When
        val fullName = htmlParser.extractUserFullName(html)

        // Then
        assertNotNull(fullName)
        assertEquals("Test User", fullName)
    }

    @Test
    fun `extractUserFullName works with realistic Dualis HTML structure`() {
        // Given - realistic HTML structure from Dualis main page
        val html = """
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
            <html>
            <head>
                <title>Dualis - Startseite</title>
            </head>
            <body>
                <div class="header">
                    <h1>Herzlich willkommen, Johannes Popp!</h1>
                </div>
                <div class="content">
                    <!-- other page content -->
                </div>
            </body>
            </html>
        """.trimIndent()

        // When
        val fullName = htmlParser.extractUserFullName(html)

        // Then
        assertNotNull(fullName)
        assertEquals("Johannes Popp", fullName)
    }

    @Test
    fun `isMainPage detects page with welcome message`() {
        // Given
        val html = """
            <html>
            <body>
                <h1>Herzlich willkommen, Johannes Popp!</h1>
                <div>Main page content</div>
            </body>
            </html>
        """.trimIndent()

        // When
        val isMain = htmlParser.isMainPage(html)

        // Then
        assertTrue(isMain)
    }
}

