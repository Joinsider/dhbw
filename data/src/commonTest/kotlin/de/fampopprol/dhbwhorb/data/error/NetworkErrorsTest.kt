/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.error

import de.fampopprol.dhbwhorb.core.error.AppError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NetworkErrorsTest {

    @Test
    fun everyKnownConnectivityFailure_becomesOffline() {
        assertEquals(AppError.Offline, UnresolvedAddressException().toAppError("fetch"))
        assertEquals(AppError.Offline, ConnectTimeoutException("connect timed out").toAppError("fetch"))
        assertEquals(AppError.Offline, SocketTimeoutException("socket timed out").toAppError("fetch"))
        assertEquals(AppError.Offline, HttpRequestTimeoutException("https://dualis.example", 5000L).toAppError("fetch"))
        assertEquals(AppError.Offline, IOException("broken pipe").toAppError("fetch"))
    }

    @Test
    fun anUnrecognizedThrowable_becomesUnexpectedWithTheSourceAndMessage() {
        val error = IllegalStateException("boom").toAppError("logging in")

        val unexpected = assertIs<AppError.Unexpected>(error)
        assertTrue(unexpected.hint.contains("logging in"))
        assertTrue(unexpected.hint.contains("IllegalStateException"))
        assertTrue(unexpected.hint.contains("boom"))
    }

    @Test
    fun httpStatusToAppError_mapsUnauthorizedAndForbiddenToSessionExpired() {
        assertEquals(AppError.SessionExpired, httpStatusToAppError(401))
        assertEquals(AppError.SessionExpired, httpStatusToAppError(403))
    }

    @Test
    fun httpStatusToAppError_keepsAnyOtherCodeAsIs() {
        assertEquals(AppError.Http(500), httpStatusToAppError(500))
        assertEquals(AppError.Http(200), httpStatusToAppError(200))
    }
}
