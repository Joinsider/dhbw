/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.net

import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClearableCookiesStorageTest {

    private val dualis = Url("https://dualis.dhbw.de/")

    @Test
    fun cookiesAreKept_untilTheyAreCleared() = runTest {
        val storage = ClearableCookiesStorage()
        storage.addCookie(dualis, Cookie(name = "JSESSIONID", value = "abc", path = "/"))

        assertEquals("abc", storage.get(dualis).find { it.name == "JSESSIONID" }?.value)

        storage.clear()

        assertTrue(storage.get(dualis).isEmpty(), "the session cookie outlived the session")
    }

    @Test
    fun theStorageStaysUsable_soTheNextLoginCanFillIt() = runTest {
        val storage = ClearableCookiesStorage()
        storage.addCookie(dualis, Cookie(name = "JSESSIONID", value = "first", path = "/"))
        storage.clear()

        storage.addCookie(dualis, Cookie(name = "JSESSIONID", value = "second", path = "/"))

        assertEquals("second", storage.get(dualis).find { it.name == "JSESSIONID" }?.value)
    }
}
