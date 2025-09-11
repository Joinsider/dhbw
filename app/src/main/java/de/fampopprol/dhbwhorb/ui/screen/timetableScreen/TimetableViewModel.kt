/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.timetableScreen

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.network.NetworkConnectivityManager
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TimetableViewModel(
    private val dualisService: DualisService,
    private val credentialManager: CredentialManager,
    private val timetableCacheManager: TimetableCacheManager,
    private val networkConnectivityManager: NetworkConnectivityManager? = null
) : ViewModel() {
    var timetable by mutableStateOf<List<TimetableDay>?>(null)
        private set

    var isFetchingFromApi by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var lastUpdated by mutableStateOf<String?>(null)
        private set

    var isOffline by mutableStateOf(false)
        private set

    var rateLimitMessage by mutableStateOf<String?>(null)
        private set

    private var preFetchJob: Job? = null
    private var reconnectJob: Job? = null
    private var pendingWeekStart: LocalDate? = null

    init {
        // Clean up expired cache on initialization
        viewModelScope.launch {
            timetableCacheManager.clearExpiredCache()
        }

        // Monitor network connectivity changes
        networkConnectivityManager?.let { networkManager ->
            viewModelScope.launch {
                networkManager.hasInternetAccess.collect { hasInternet ->
                    val wasOffline = isOffline
                    isOffline = !hasInternet

                    Log.d("TimetableViewModel", "Network status changed - Online: $hasInternet, Was offline: $wasOffline")

                    if (wasOffline && hasInternet) {
                        // Just came back online - try to refresh data
                        Log.d("TimetableViewModel", "Device came back online, attempting to refresh data")
                        handleReconnection()
                    } else if (!hasInternet) {
                        // Went offline - show cached data with offline message
                        Log.d("TimetableViewModel", "Device went offline, showing cached data")
                        handleOfflineMode()
                    }
                }
            }
        }

        // Collect rate limit state
        viewModelScope.launch {
            de.fampopprol.dhbwhorb.data.dualis.network.RateLimitTracker.state.collect { state ->
                if (state.isRateLimited) {
                    rateLimitMessage = if (state.finalFailure) {
                        "Zugriff verweigert - rate limit reached. Please log out and log in again."
                    } else {
                        "Zugriff verweigert - retrying (${state.attempt}/${state.maxAttempts})..."
                    }
                } else {
                    rateLimitMessage = null
                }
            }
        }
    }

    private fun handleReconnection() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(1000) // Wait a moment for connection to stabilize

            val weekToRefresh = pendingWeekStart ?: getCurrentWeekStart()
            Log.d("TimetableViewModel", "Attempting to refresh data after reconnection for week: $weekToRefresh")

            // Clear the offline error message
            if (errorMessage?.contains("offline", ignoreCase = true) == true) {
                errorMessage = null
            }

            // Try to fetch fresh data
            fetchTimetableFromApi(weekToRefresh, isForced = false)
        }
    }

    private fun handleOfflineMode() {
        // Show offline message but keep cached data
        val currentWeek = getCurrentWeekStart()
        val hasCachedData = loadCachedTimetable(currentWeek)

        if (hasCachedData) {
            errorMessage = "Offline - when the phone is connected to the internet again, Dualis will be fetched again"
        } else {
            errorMessage = "No internet connection and no cached data available"
        }
    }

    private fun getCurrentWeekStart(): LocalDate {
        return LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    }

    // Function to update last updated timestamp
    fun updateLastUpdatedTimestamp() {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        lastUpdated = java.time.LocalDateTime.now().format(formatter)
    }

    // Enhanced function to load timetable data from cache with validation
    fun loadCachedTimetable(weekStart: LocalDate): Boolean {
        // First try to load valid (non-expired) cached data
        val validCachedTimetable = timetableCacheManager.getValidCachedTimetable(weekStart)
        if (validCachedTimetable != null) {
            timetable = validCachedTimetable
            Log.d("TimetableViewModel", "Displaying valid cached timetable for week: $weekStart")
            return true
        }

        // If no valid cache, try to load any cached data as fallback (for offline usage)
        val anyCachedTimetable = timetableCacheManager.loadTimetable(weekStart)
        if (anyCachedTimetable != null) {
            timetable = anyCachedTimetable
            Log.d("TimetableViewModel", "Displaying expired/fallback cached timetable for week: $weekStart")
            return true
        }

        Log.d("TimetableViewModel", "No cached timetable found for week: $weekStart")
        return false
    }

    // Enhanced function to load the best available cached data on app startup
    fun loadBestAvailableCache(preferredWeekStart: LocalDate): Boolean {
        Log.d("TimetableViewModel", "Loading best available cache on app startup")

        // First, try to load the preferred week
        if (loadCachedTimetable(preferredWeekStart)) {
            Log.d("TimetableViewModel", "Loaded preferred week cache: $preferredWeekStart")
            return true
        }

        // If preferred week is not cached, find the best alternative
        val allCachedWeeks = timetableCacheManager.getAllCachedWeeks()
        if (allCachedWeeks.isEmpty()) {
            Log.d("TimetableViewModel", "No cached weeks available")
            return false
        }

        // Try to find a valid cache entry closest to the preferred week
        val bestWeek = allCachedWeeks.minByOrNull { week ->
            kotlin.math.abs(ChronoUnit.DAYS.between(preferredWeekStart, week))
        }

        if (bestWeek != null && loadCachedTimetable(bestWeek)) {
            Log.d("TimetableViewModel", "Loaded closest available cache: $bestWeek (preferred was $preferredWeekStart)")
            return true
        }

        Log.d("TimetableViewModel", "No usable cached data found")
        return false
    }

    // Function to fetch timetable from API with enhanced cache logic and network awareness
    fun fetchTimetableFromApi(weekStart: LocalDate, isForced: Boolean = false) {
        if (isFetchingFromApi && !isForced) {
            // Even if a main fetch is in progress, still try to prefetch surrounding weeks
            preFetchTimetables(weekStart)
            return
        }

        // Store the week we're trying to fetch for reconnection scenarios
        pendingWeekStart = weekStart

        // Check network connectivity first
        val isOnline = networkConnectivityManager?.isCurrentlyOnline() ?: true

        if (!isOnline) {
            Log.d("TimetableViewModel", "Device is offline, showing cached data")
            val hasCachedData = loadCachedTimetable(weekStart)
            if (hasCachedData) {
                errorMessage = "Offline - when the phone is connected to the internet again, Dualis will be fetched again"
            } else {
                errorMessage = "No internet connection and no cached data available"
            }
            isOffline = true
            return
        }

        // Enhanced cache checking - consider cache validity
        val hasValidCache = timetableCacheManager.isCacheValid(weekStart)
        if (hasValidCache && !isForced) {
            Log.d("TimetableViewModel", "Skipping API fetch for week $weekStart, valid cache available.")
            // Load the cached data
            loadCachedTimetable(weekStart)
            // Still prefetch surrounding weeks if they're not cached
            preFetchTimetables(weekStart)
            return
        }

        isFetchingFromApi = true
        Log.d("TimetableViewModel", "Fetching timetable from API for week starting: $weekStart (forced: $isForced, cache valid: $hasValidCache)")

        dualisService.getWeeklySchedule(weekStart) { fetchedTimetable ->
            isFetchingFromApi = false
            isRefreshing = false

            if (fetchedTimetable != null) {
                Log.d("TimetableViewModel", "Fetched Timetable for week starting $weekStart: $fetchedTimetable")

                // Always update the cache with fresh data
                timetable = fetchedTimetable
                timetableCacheManager.saveTimetable(weekStart, fetchedTimetable)
                Log.d("TimetableViewModel", "Timetable updated and cached for week: $weekStart")

                // After the main timetable is fetched and cached, pre-fetch surrounding weeks
                preFetchTimetables(weekStart)

                updateLastUpdatedTimestamp()
                // Clear offline error message if it was showing
                if (errorMessage?.contains("offline", ignoreCase = true) == true) {
                    errorMessage = null
                }
                isOffline = false
            } else {
                // Rate limit specific handling
                val rlState = de.fampopprol.dhbwhorb.data.dualis.network.RateLimitTracker.state.value
                if (rlState.finalFailure) {
                    errorMessage = "Dualis rate limited. Please log out and log in again."
                } else {
                    // Check if we're offline and handle accordingly
                    val currentlyOnline = networkConnectivityManager?.isCurrentlyOnline() ?: true

                    if (!currentlyOnline) {
                        // We're offline, show cached data with offline message
                        val fallbackCache = timetableCacheManager.loadTimetable(weekStart)
                        if (fallbackCache != null) {
                            timetable = fallbackCache
                            errorMessage = "Offline - when the phone is connected to the internet again, Dualis will be fetched again"
                            isOffline = true
                        } else {
                            errorMessage = "No internet connection and no cached data available"
                            isOffline = true
                        }
                    } else {
                        // We're online but API failed, try cached data as fallback
                        if (timetable == null) {
                            val fallbackCache = timetableCacheManager.loadTimetable(weekStart)
                            if (fallbackCache != null) {
                                timetable = fallbackCache
                                Log.d("TimetableViewModel", "Using fallback cached data due to API failure")
                                errorMessage = "Using cached data - network unavailable"
                            } else {
                                errorMessage = "Failed to load timetable. Please try logging in again."
                            }
                        } else {
                            errorMessage = "Failed to refresh timetable - showing cached data"
                        }
                    }
                }
            }
        }
    }

    private fun preFetchTimetables(currentWeekStart: LocalDate) {
        // Don't prefetch if we're offline
        if (isOffline || networkConnectivityManager?.isCurrentlyOnline() == false) {
            Log.d("TimetableViewModel", "Skipping prefetch - device is offline")
            return
        }

        preFetchJob?.cancel() // Cancel any existing pre-fetch job
        preFetchJob = viewModelScope.launch {
            delay(700) // Wait before starting to pre-fetch
            Log.d("TimetableViewModel", "Starting to pre-fetch surrounding timetables.")

            // Prioritize weeks closer to current week
            val weeksToFetch = mutableListOf<LocalDate>()

            // Add next 2 weeks first (higher priority)
            for (i in 1..2) {
                weeksToFetch.add(currentWeekStart.plusWeeks(i.toLong()))
            }

            // Add previous 2 weeks
            for (i in 1..2) {
                weeksToFetch.add(currentWeekStart.minusWeeks(i.toLong()))
            }

            // Add additional weeks if needed
            for (i in 3..4) {
                weeksToFetch.add(currentWeekStart.plusWeeks(i.toLong()))
                weeksToFetch.add(currentWeekStart.minusWeeks(i.toLong()))
            }

            weeksToFetch.forEach { weekToFetch ->
                // Only fetch if cache is invalid or missing and we're still online
                if (!timetableCacheManager.isCacheValid(weekToFetch) &&
                    (networkConnectivityManager?.isCurrentlyOnline() != false)) {
                    fetchTimetableFromApiInBackground(weekToFetch)
                    delay(100) // Slightly longer delay to be nice to the server
                }
            }
        }
    }

    private fun fetchTimetableFromApiInBackground(weekStart: LocalDate) {
        // Skip if we have valid cache or are offline
        if (timetableCacheManager.isCacheValid(weekStart) ||
            networkConnectivityManager?.isCurrentlyOnline() == false) {
            Log.d("TimetableViewModel", "Skipping pre-fetch for week $weekStart - valid cache available or offline.")
            return
        }

        Log.d("TimetableViewModel", "Pre-fetching timetable from API for week starting: $weekStart")

        dualisService.getWeeklySchedule(weekStart) { fetchedTimetable ->
            if (fetchedTimetable != null) {
                timetableCacheManager.saveTimetable(weekStart, fetchedTimetable)
                Log.d("TimetableViewModel", "Pre-fetched and cached timetable for week: $weekStart")
            } else {
                Log.e("TimetableViewModel", "Failed to pre-fetch timetable from API for week starting $weekStart")
            }
        }
    }

    // Public API to trigger surrounding weeks prefetch from UI/navigation
    fun prefetchSurroundingWeeks(weekStart: LocalDate) {
        preFetchTimetables(weekStart)
    }

    // Function to set refreshing state from external callers
    fun setRefreshingState(refreshing: Boolean) {
        isRefreshing = refreshing
    }

    private fun shouldRefreshData(): Boolean {
        if (timetable == null) return true

        val today = LocalDate.now()
        val lastCacheDay = getLastCacheDay()

        return lastCacheDay == null || ChronoUnit.DAYS.between(lastCacheDay, today) > 0
    }

    private fun getLastCacheDay(): LocalDate? {
        return if (timetable?.isNotEmpty() == true) {
            try {
                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                LocalDate.parse(timetable!!.last().date, formatter)
            } catch (e: Exception) {
                Log.e("TimetableViewModel", "Error parsing last cache day", e)
                null
            }
        } else null
    }

    private fun performAuthentication(
        username: String,
        password: String,
        weekStart: LocalDate,
        needsRefresh: Boolean,
        onLogout: () -> Unit
    ) {
        Log.d("TimetableViewModel", "Re-authenticating with stored credentials")
        dualisService.login(username, password) { result ->
            if (result != null) {
                viewModelScope.launch {
                    delay(1000) // Add a delay before fetching the timetable
                    Log.d("TimetableViewModel", "Re-authentication successful, fetching timetable")
                    fetchTimetableFromApi(weekStart, isForced = needsRefresh)
                }
            } else {
                handleAuthenticationFailure(onLogout)
            }
        }
    }

    private fun handleAuthenticationFailure(onLogout: () -> Unit) {
        Log.e("TimetableViewModel", "Re-authentication failed")

        // Try to show cached data even if authentication failed
        if (timetable == null) {
            val cachedWeeks = timetableCacheManager.getAllCachedWeeks()
            if (cachedWeeks.isNotEmpty()) {
                val bestWeek = cachedWeeks.maxByOrNull { it }
                if (bestWeek != null) {
                    loadCachedTimetable(bestWeek)
                    errorMessage = "Authentication failed - showing cached data"
                    return
                }
            }
            // Only logout if we have no cached data to show
            errorMessage = "Authentication failed. Please log in again."
            onLogout()
        } else {
            errorMessage = "Authentication failed - showing cached data"
        }
    }

    private fun handleMissingCredentials(onLogout: () -> Unit) {
        Log.e("TimetableViewModel", "No stored credentials found")

        // Try to show any available cached data
        if (timetable == null) {
            val cachedWeeks = timetableCacheManager.getAllCachedWeeks()
            if (cachedWeeks.isNotEmpty()) {
                val bestWeek = cachedWeeks.maxByOrNull { it }
                if (bestWeek != null) {
                    loadCachedTimetable(bestWeek)
                    errorMessage = "No credentials found - showing cached data"
                    return
                }
            }
            // Only logout if we have no cached data to show
            errorMessage = "No credentials found. Please log in."
            onLogout()
        } else {
            errorMessage = "No credentials found - showing cached data"
        }
    }

    private fun handleNoStoredCredentials(onLogout: () -> Unit) {
        // Try to load any available cached data before logging out
        val cachedWeeks = timetableCacheManager.getAllCachedWeeks()
        if (cachedWeeks.isNotEmpty() && timetable == null) {
            val bestWeek = cachedWeeks.maxByOrNull { it }
            if (bestWeek != null) {
                loadCachedTimetable(bestWeek)
                errorMessage = "No stored credentials - showing cached data"
                return
            }
        }

        if (timetable == null) {
            onLogout()
        }
    }

    fun initialize(weekStart: LocalDate, onLogout: () -> Unit) {
        viewModelScope.launch {
            // First, try to load the best available cached data immediately
            val hasLoadedCache = loadBestAvailableCache(weekStart)

            if (hasLoadedCache) {
                Log.d("TimetableViewModel", "App started with cached data available")
                // Update the timestamp to show when cache was loaded
                val lastCacheUpdate = timetableCacheManager.getLastCacheUpdateTime()
                if (lastCacheUpdate != null) {
                    val formatter = DateTimeFormatter.ofPattern("HH:mm")
                    lastUpdated = lastCacheUpdate.format(formatter)
                }
            }

            // Check if we're offline first
            val isCurrentlyOnline = networkConnectivityManager?.isCurrentlyOnline() ?: true

            if (!isCurrentlyOnline) {
                Log.d("TimetableViewModel", "Device is offline during initialization")
                handleOfflineMode()
                return@launch
            }

            val needsRefresh = shouldRefreshData() || !timetableCacheManager.isCacheValid(weekStart)

            if (credentialManager.hasStoredCredentialsBlocking()) {
                val username = credentialManager.getUsernameBlocking()
                val password = credentialManager.getPassword()

                if (username != null && password != null) {
                    if (needsRefresh || !hasLoadedCache) {
                        performAuthentication(username, password, weekStart, needsRefresh, onLogout)
                    } else {
                        // We have valid cached data, but still prefetch in background
                        Log.d("TimetableViewModel", "Valid cache available, prefetching in background")
                        preFetchTimetables(weekStart)
                    }
                } else {
                    handleMissingCredentials(onLogout)
                }
            } else {
                handleNoStoredCredentials(onLogout)
            }
        }
    }
}
