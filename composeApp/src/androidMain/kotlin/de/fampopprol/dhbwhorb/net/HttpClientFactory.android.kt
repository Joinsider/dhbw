package de.fampopprol.dhbwhorb.net

import de.fampopprol.dhbwhorb.data.network.CustomDnsResolver
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android implementation of the HttpClientFactory.
 * Uses the OkHttp engine with a custom DNS resolver as fallback (system DNS -> DoH).
 */
actual object HttpClientFactory {
    /**
     * Create the Android-specific HttpClient engine factory (OkHttp).
     */
    actual fun createEngine(): HttpClientEngineFactory<*> = OkHttp
}
