/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.data.network

import io.github.aakira.napier.Napier
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Custom DNS resolver for Android that first uses the system resolver and,
 * on failure, falls back to DNS-over-HTTPS (DoH) using Cloudflare and Google.
 *
 * This avoids the dnsjava/JNA/JNDI classes that R8 cannot keep on Android
 * and provides a reliable cross-network fallback.
 */
class CustomDnsResolver : Dns {
    companion object {
        private const val TAG = "CustomDnsResolver"

        // DoH endpoints
        private const val CLOUDFLARE_DOH = "https://cloudflare-dns.com/dns-query"
        private const val GOOGLE_DOH = "https://dns.google/dns-query"
    }

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        // You can customize timeouts if needed
        //.callTimeout(5, TimeUnit.SECONDS)
        //.connectTimeout(5, TimeUnit.SECONDS)
        .build()

    // Primary DoH (Cloudflare)
    private val cloudflareDns: DnsOverHttps = DnsOverHttps.Builder()
        .client(baseClient)
        .url(CLOUDFLARE_DOH.toHttpUrl())
        .bootstrapDnsHosts(listOf(
            // Cloudflare IPv4 addresses to avoid dependency on system DNS for initial resolve
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1")
        ))
        .build()

    // Secondary DoH (Google)
    private val googleDns: DnsOverHttps = DnsOverHttps.Builder()
        .client(baseClient)
        .url(GOOGLE_DOH.toHttpUrl())
        .bootstrapDnsHosts(listOf(
            // Google Public DNS IPv4 addresses
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4")
        ))
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        Napier.d("Looking up hostname: $hostname", tag = TAG)

        // First, try the system DNS
        try {
            val addresses = InetAddress.getAllByName(hostname).toList()
            if (addresses.isNotEmpty()) {
                Napier.d("System DNS resolved $hostname to ${addresses.size} addresses", tag = TAG)
                return addresses
            }
        } catch (e: UnknownHostException) {
            Napier.w("System DNS failed for $hostname: ${e.message}", tag = TAG)
        }

        // Fall back to Cloudflare DoH
        try {
            val cf = cloudflareDns.lookup(hostname)
            if (cf.isNotEmpty()) {
                Napier.d("Cloudflare DoH resolved $hostname to ${cf.size} addresses", tag = TAG)
                return cf
            }
        } catch (e: Exception) {
            Napier.w("Cloudflare DoH failed for $hostname: ${e.message}", tag = TAG)
        }

        // Fall back to Google DoH
        try {
            val gg = googleDns.lookup(hostname)
            if (gg.isNotEmpty()) {
                Napier.d("Google DoH resolved $hostname to ${gg.size} addresses", tag = TAG)
                return gg
            }
        } catch (e: Exception) {
            Napier.w("Google DoH failed for $hostname: ${e.message}", tag = TAG)
        }

        Napier.e("All DNS resolution attempts failed for $hostname", tag = TAG)
        throw UnknownHostException("Unable to resolve $hostname using system DNS or DoH")
    }
}
