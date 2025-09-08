/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages network connectivity status and provides real-time updates
 * Uses ConnectivityManager to listen for network changes and exposes state via StateFlow and Compose state
 * Also provides utility methods for checking online status and airplane mode
 */
class NetworkConnectivityManager(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _hasInternetAccess = MutableStateFlow(false)
    val hasInternetAccess: StateFlow<Boolean> = _hasInternetAccess.asStateFlow()

    var isOnline by mutableStateOf(false)
        private set

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("NetworkConnectivityManager", "Network available: $network")
            _isConnected.value = true
            checkInternetAccess()
        }

        override fun onLost(network: Network) {
            Log.d("NetworkConnectivityManager", "Network lost: $network")
            _isConnected.value = false
            _hasInternetAccess.value = false
            isOnline = false
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d("NetworkConnectivityManager", "Network capabilities changed - Internet: $hasInternet, Validated: $hasValidated")

            _hasInternetAccess.value = hasInternet && hasValidated
            isOnline = hasInternet && hasValidated
        }
    }

    init {
        // Initial connectivity check
        checkInitialConnectivity()

        // Register for network callbacks
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    private fun checkInitialConnectivity() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val isConnected = activeNetwork != null
        val hasInternet = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val hasValidated = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        _isConnected.value = isConnected
        _hasInternetAccess.value = hasInternet && hasValidated
        isOnline = hasInternet && hasValidated

        Log.d("NetworkConnectivityManager", "Initial connectivity - Connected: $isConnected, Internet: $hasInternet, Validated: $hasValidated")
    }

    private fun checkInternetAccess() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val hasInternet = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val hasValidated = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        _hasInternetAccess.value = hasInternet && hasValidated
        isOnline = hasInternet && hasValidated

        Log.d("NetworkConnectivityManager", "Internet access check - Internet: $hasInternet, Validated: $hasValidated")
    }

    /**
     * Check if device is currently online (has internet access)
     */
    fun isCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Check if device is in airplane mode
     */
    fun isAirplaneModeOn(): Boolean {
        return android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
    }

    /**
     * Get detailed connectivity status for debugging
     */
    fun getConnectivityStatus(): String {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return when {
            isAirplaneModeOn() -> "Airplane Mode"
            activeNetwork == null -> "No Network"
            networkCapabilities == null -> "No Capabilities"
            !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "No Internet Capability"
            !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "Internet Not Validated"
            else -> "Online"
        }
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("NetworkConnectivityManager", "Error unregistering network callback", e)
        }
    }
}
