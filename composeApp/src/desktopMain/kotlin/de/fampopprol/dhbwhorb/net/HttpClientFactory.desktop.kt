package de.fampopprol.dhbwhorb.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

/**
 * Desktop implementation of the HttpClientFactory.
 * Uses the CIO engine.
 */
actual object HttpClientFactory {
    /**
     * Create the Desktop-specific HttpClient engine factory (CIO).
     */
    actual fun createEngine(): HttpClientEngineFactory<*> = CIO
}
