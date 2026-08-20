package de.fampopprol.dhbwhorb.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/**
 * macOS implementation of the HttpClientFactory.
 * Uses the Darwin engine.
 */
actual object HttpClientFactory {
    /**
     * Create the macOS-specific HttpClient engine factory (Darwin).
     */
    actual fun createEngine(): HttpClientEngineFactory<*> = Darwin
}
