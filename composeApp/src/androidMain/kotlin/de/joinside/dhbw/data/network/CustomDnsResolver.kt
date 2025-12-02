/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.data.network

import android.os.Build
import androidx.annotation.RequiresApi
import io.github.aakira.napier.Napier
import okhttp3.Dns
import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration

/**
 * Custom DNS resolver that uses Google's public DNS servers (8.8.8.8 and 8.8.4.4)
 * as a fallback when the system DNS fails to resolve.
 *
 * This fixes issues where Android's system DNS can't resolve certain hostnames,
 * particularly on some networks or with some DNS configurations.
 *
 * Uses dnsjava library for proper DNS queries.
 */
class CustomDnsResolver : Dns {
    companion object {
        private const val TAG = "CustomDnsResolver"

        // Google's public DNS servers
        private const val GOOGLE_DNS_PRIMARY = "8.8.8.8"
        private const val GOOGLE_DNS_SECONDARY = "8.8.4.4"

        // Cloudflare DNS as additional fallback
        private const val CLOUDFLARE_DNS = "1.1.1.1"
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

        // If system DNS fails, try Google DNS
        Napier.d("Attempting resolution via Google DNS...", tag = TAG)
        val googleDnsResult = try {
            resolveUsingDnsServer(hostname, GOOGLE_DNS_PRIMARY)
        } catch (e: Exception) {
            Napier.w("Google DNS primary failed: ${e.message}", tag = TAG)
            null
        }

        if (googleDnsResult != null && googleDnsResult.isNotEmpty()) {
            Napier.d("Google DNS resolved $hostname to ${googleDnsResult.size} addresses", tag = TAG)
            return googleDnsResult
        }

        // Try secondary Google DNS
        val googleDnsSecondaryResult = try {
            resolveUsingDnsServer(hostname, GOOGLE_DNS_SECONDARY)
        } catch (e: Exception) {
            Napier.w("Google DNS secondary failed: ${e.message}", tag = TAG)
            null
        }

        if (googleDnsSecondaryResult != null && googleDnsSecondaryResult.isNotEmpty()) {
            Napier.d("Google DNS secondary resolved $hostname to ${googleDnsSecondaryResult.size} addresses", tag = TAG)
            return googleDnsSecondaryResult
        }

        // Try Cloudflare DNS as last resort
        val cloudflareResult = try {
            resolveUsingDnsServer(hostname, CLOUDFLARE_DNS)
        } catch (e: Exception) {
            Napier.w("Cloudflare DNS failed: ${e.message}", tag = TAG)
            null
        }

        if (cloudflareResult != null && cloudflareResult.isNotEmpty()) {
            Napier.d("Cloudflare DNS resolved $hostname to ${cloudflareResult.size} addresses", tag = TAG)
            return cloudflareResult
        }

        // If everything fails, throw exception
        Napier.e("All DNS resolution attempts failed for $hostname", tag = TAG)
        throw UnknownHostException("Unable to resolve $hostname using system DNS, Google DNS, or Cloudflare DNS")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun resolveUsingDnsServer(hostname: String, dnsServer: String): List<InetAddress> {
        val resolver = SimpleResolver(dnsServer)
        resolver.timeout = Duration.ofSeconds(5) // 5 second timeout

        val lookup = Lookup(hostname, Type.A)
        lookup.setResolver(resolver)

        val records = lookup.run() ?: return emptyList()

        return records.mapNotNull { record ->
            if (record is ARecord) {
                try {
                    record.address
                } catch (e: Exception) {
                    Napier.w("Failed to convert DNS record to InetAddress: ${e.message}", tag = TAG)
                    null
                }
            } else {
                null
            }
        }
    }
}

