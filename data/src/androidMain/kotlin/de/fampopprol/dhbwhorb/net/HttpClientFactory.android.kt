package de.fampopprol.dhbwhorb.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android implementation of the HttpClientFactory.
 * Uses the OkHttp engine with a custom DNS resolver as fallback (system DNS -> DoH).
 *
 * Android's trust store is maintained by the system, so unlike desktop it needs no extra roots.
 */
actual object HttpClientFactory {
    actual fun create(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
        HttpClient(OkHttp) { configure() }
}
