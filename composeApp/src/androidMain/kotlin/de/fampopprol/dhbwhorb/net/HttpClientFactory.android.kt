package de.fampopprol.dhbwhorb.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android implementation of the HttpClientFactory.
 * Uses the OkHttp engine which follows the system's network security configuration.
 */
actual object HttpClientFactory {
    /**
     * Create the Android-specific HttpClient engine factory (OkHttp).
     */
    actual fun createEngine(): HttpClientEngineFactory<*> = OkHttp
}
