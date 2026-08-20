package de.fampopprol.dhbwhorb.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Desktop implementation of the HttpClientFactory.
 * Uses the OkHttp engine for better SSL/TLS compatibility.
 */
actual object HttpClientFactory {
    /**
     * Create the Desktop-specific HttpClient engine factory (OkHttp).
     */
    actual fun createEngine(): HttpClientEngineFactory<*> = OkHttp
}
