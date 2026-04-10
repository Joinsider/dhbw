package de.fampopprol.dhbwhorb.data.network

import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Utility for initializing the shared HttpClient asynchronously.
 * This ensures networking setup happens on a background thread.
 */
object HttpClientInitializer {
    private const val TAG = "HttpClientInitializer"

    /**
     * Initializes the shared HttpClient asynchronously.
     * Uses the shared configuration from AuthenticationService.
     * Handles cancellation gracefully to prevent resource leaks.
     * @return The initialized HttpClient instance.
     */
    suspend fun initializeHttpClientAsync(): HttpClient = withContext(Dispatchers.IO) {
        Napier.d("Initializing HttpClient asynchronously...", tag = TAG)
        val client = AuthenticationService.createSharedHttpClient()
        try {
            // Check if we are still active after creation
            ensureActive()
            Napier.d("HttpClient initialized successfully", tag = TAG)
            client
        } catch (e: Exception) {
            // If cancelled or failed after creation, close the client to release resources
            Napier.w("HttpClient initialization cancelled or failed, closing client", tag = TAG)
            client.close()
            throw e
        }
    }
}
