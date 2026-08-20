package de.fampopprol.dhbwhorb.net

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Factory for creating platform-specific HttpClient engines.
 * This allows each platform to configure its own engine (OkHttp, Darwin, CIO)
 * with the necessary SSL trust configurations.
 */
expect object HttpClientFactory {
    /**
     * Create a platform-specific HttpClient engine factory.
     */
    fun createEngine(): HttpClientEngineFactory<*>
}
