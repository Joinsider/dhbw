/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.network

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The fallback chain — system DNS, then Cloudflare DoH, then Google DoH — is exercised here via
 * injectable lookup functions rather than real network calls; see the constructor of
 * [CustomDnsResolver] for why they're swappable.
 */
class CustomDnsResolverTest {

    private val loopback = listOf(InetAddress.getByName("127.0.0.1"))
    private val docsAddress = listOf(InetAddress.getByName("203.0.113.10"))

    @Test
    fun lookup_systemDnsSucceeds_neverTriesDoh() {
        var cloudflareCalled = false
        var googleCalled = false
        val resolver = CustomDnsResolver(
            systemLookup = { loopback },
            cloudflareLookup = { cloudflareCalled = true; docsAddress },
            googleLookup = { googleCalled = true; docsAddress },
        )

        val result = resolver.lookup("dualis.dhbw.de")

        assertEquals(loopback, result)
        assertEquals(false, cloudflareCalled)
        assertEquals(false, googleCalled)
    }

    @Test
    fun lookup_systemDnsThrows_fallsBackToCloudflare() {
        val resolver = CustomDnsResolver(
            systemLookup = { throw UnknownHostException("no system DNS") },
            cloudflareLookup = { docsAddress },
            googleLookup = { error("must not be called") },
        )

        assertEquals(docsAddress, resolver.lookup("dualis.dhbw.de"))
    }

    @Test
    fun lookup_systemDnsReturnsEmpty_alsoFallsBackToCloudflare() {
        val resolver = CustomDnsResolver(
            systemLookup = { emptyList() },
            cloudflareLookup = { docsAddress },
            googleLookup = { error("must not be called") },
        )

        assertEquals(docsAddress, resolver.lookup("dualis.dhbw.de"))
    }

    @Test
    fun lookup_systemAndCloudflareFail_fallsBackToGoogle() {
        val resolver = CustomDnsResolver(
            systemLookup = { throw UnknownHostException() },
            cloudflareLookup = { throw RuntimeException("cloudflare unreachable") },
            googleLookup = { docsAddress },
        )

        assertEquals(docsAddress, resolver.lookup("dualis.dhbw.de"))
    }

    @Test
    fun lookup_cloudflareReturnsEmpty_fallsBackToGoogle() {
        val resolver = CustomDnsResolver(
            systemLookup = { throw UnknownHostException() },
            cloudflareLookup = { emptyList() },
            googleLookup = { docsAddress },
        )

        assertEquals(docsAddress, resolver.lookup("dualis.dhbw.de"))
    }

    @Test
    fun lookup_allThreeFail_throwsUnknownHostException() {
        val resolver = CustomDnsResolver(
            systemLookup = { throw UnknownHostException() },
            cloudflareLookup = { throw RuntimeException("cloudflare unreachable") },
            googleLookup = { throw RuntimeException("google unreachable") },
        )

        assertFailsWith<UnknownHostException> { resolver.lookup("dualis.dhbw.de") }
    }

    @Test
    fun lookup_allThreeReturnEmpty_throwsUnknownHostException() {
        val resolver = CustomDnsResolver(
            systemLookup = { emptyList() },
            cloudflareLookup = { emptyList() },
            googleLookup = { emptyList() },
        )

        assertFailsWith<UnknownHostException> { resolver.lookup("dualis.dhbw.de") }
    }
}
