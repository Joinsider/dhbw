package de.fampopprol.dhbwhorb.data.network

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A lifecycle-aware manager for HttpClient.
 * Ensures the client is properly closed when the activity is destroyed.
 * Implements [AutoCloseable] for explicit closure and [DefaultLifecycleObserver]
 * for automatic lifecycle-based cleanup.
 */
class HttpClientManager : DefaultLifecycleObserver, AutoCloseable {
    private var activeClient: HttpClient? = null
    private val mutex = Mutex()
    private var isClosed = false

    /**
     * Set the current HttpClient instance.
     * If an existing client was set, it will be closed.
     */
    suspend fun setClient(newClient: HttpClient) {
        mutex.withLock {
            if (isClosed) {
                Napier.w("Attempted to set client on a closed HttpClientManager", tag = TAG)
                newClient.close()
                return@withLock
            }
            // Close old client if we're replacing it
            if (activeClient != null && activeClient !== newClient) {
                Napier.d("Closing existing HttpClient to replace with new instance", tag = TAG)
                activeClient?.close()
            }
            activeClient = newClient
            Napier.d("HttpClient registered with HttpClientManager", tag = TAG)
        }
    }

    /**
     * The current HttpClient instance.
     */
    val client: HttpClient?
        get() = activeClient

    /**
     * Definitively close the connection pool and the client.
     */
    override fun close() {
        activeClient?.let {
            it.close()
            activeClient = null
            isClosed = true
            Napier.d("HttpClient definitively closed via HttpClientManager", tag = TAG)
        }
    }

    /**
     * Automatic cleanup triggered by Lifecycle.ON_DESTROY.
     */
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Napier.d("Lifecycle ON_DESTROY detected, initiating HttpClient cleanup", tag = TAG)
        close()
    }

    companion object {
        private const val TAG = "HttpClientManager"
    }
}
