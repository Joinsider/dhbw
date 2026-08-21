package de.fampopprol.dhbwhorb.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

/**
 * macOS implementation of the HttpClientFactory.
 * Uses the Darwin engine, which validates against the system trust store.
 */
actual object HttpClientFactory {
    actual fun create(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
        HttpClient(Darwin) { configure() }
}
