/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.core.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AppError.isTransient] decides whether a background worker retries or gives up, so every case
 * of the `when` gets its own assertion rather than one loop that could silently skip a branch if
 * a case were ever removed.
 */
class AppErrorTest {

    @Test
    fun offlineAndSessionExpired_areTransient() {
        assertTrue(AppError.Offline.isTransient)
        assertTrue(AppError.SessionExpired.isTransient)
    }

    @Test
    fun invalidCredentialsAndNoCredentials_areNotTransient() {
        assertFalse(AppError.InvalidCredentials.isTransient)
        assertFalse(AppError.NoCredentials.isTransient)
    }

    @Test
    fun httpErrors_areTransientOnlyAtOrAbove500() {
        assertFalse(AppError.Http(400).isTransient, "a 4xx is our fault, retrying will not help")
        assertFalse(AppError.Http(404).isTransient)
        assertFalse(AppError.Http(499).isTransient)
        assertTrue(AppError.Http(500).isTransient, "a 5xx is the server's bad minute")
        assertTrue(AppError.Http(503).isTransient)
    }

    @Test
    fun storageErrors_areTransient() {
        assertTrue(AppError.Storage("disk full").isTransient)
    }

    @Test
    fun parseUnsupportedAndUnexpected_areNotTransient() {
        assertFalse(AppError.Parse(source = "grades", hint = "bad html").isTransient)
        assertFalse(AppError.Unsupported("downloads in demo mode").isTransient)
        assertFalse(AppError.Unexpected("something odd").isTransient)
    }

    @Test
    fun dataClassVariants_carryTheirFieldsAndSupportEquality() {
        assertEquals(404, AppError.Http(404).code)
        assertEquals(AppError.Http(404), AppError.Http(404))

        val parse = AppError.Parse(source = "timetable", hint = "no weekday headers")
        assertEquals("timetable", parse.source)
        assertEquals("no weekday headers", parse.hint)
        assertEquals(parse, AppError.Parse(source = "timetable", hint = "no weekday headers"))

        assertEquals("disk full", AppError.Storage("disk full").hint)
        assertEquals("demo mode", AppError.Unsupported("demo mode").hint)
        assertEquals("mystery", AppError.Unexpected("mystery").hint)
    }

    @Test
    fun singletonVariants_areEqualToThemselves() {
        assertEquals(AppError.Offline, AppError.Offline)
        assertEquals(AppError.SessionExpired, AppError.SessionExpired)
        assertEquals(AppError.InvalidCredentials, AppError.InvalidCredentials)
        assertEquals(AppError.NoCredentials, AppError.NoCredentials)
    }
}
