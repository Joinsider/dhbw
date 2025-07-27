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
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TimetableViewModel(
    private val dualisService: DualisService,
    private val credentialManager: CredentialManager,
    private val timetableCacheManager: TimetableCacheManager
) {
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

    // Function to update last updated timestamp
    fun updateLastUpdatedTimestamp() {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        lastUpdated = java.time.LocalDateTime.now().format(formatter)
    }

    // Function to load timetable data from cache
    fun loadCachedTimetable(weekStart: LocalDate): Boolean {
        val cachedTimetable = timetableCacheManager.loadTimetable(weekStart)
        if (cachedTimetable != null) {
            timetable = cachedTimetable
            Log.d("TimetableViewModel", "Displaying cached timetable for week: $weekStart")
            return true
        }
        return false
    }

    // Function to fetch timetable from API
    fun fetchTimetableFromApi(weekStart: LocalDate, isForced: Boolean = false) {
        if (isFetchingFromApi && !isForced) return

        isFetchingFromApi = true
        Log.d("TimetableViewModel", "Fetching timetable from API for week starting: $weekStart (forced: $isForced)")

        dualisService.getWeeklySchedule(weekStart) { fetchedTimetable ->
            isFetchingFromApi = false
            isRefreshing = false

            if (fetchedTimetable != null) {
                Log.d("TimetableViewModel", "Fetched Timetable for week starting $weekStart: $fetchedTimetable")

                if (timetable != fetchedTimetable) {
                    timetable = fetchedTimetable
                    timetableCacheManager.saveTimetable(weekStart, fetchedTimetable)
                    Log.d("TimetableViewModel", "Timetable updated and cached for week: $weekStart")
                } else {
                    Log.d("TimetableViewModel", "Fetched timetable is same as current for week: $weekStart")
                }

                updateLastUpdatedTimestamp()
                errorMessage = null
            } else {
                Log.e("TimetableViewModel", "Failed to fetch timetable from API for week starting $weekStart")
                if (timetable == null) {
                    errorMessage = "Failed to load timetable. Please try logging in again."
                }
            }
        }
    }

    fun setRefreshing(refreshing: Boolean) {
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
                Log.d("TimetableViewModel", "Re-authentication successful, fetching timetable")
                fetchTimetableFromApi(weekStart, isForced = needsRefresh)
            } else {
                handleAuthenticationFailure(onLogout)
            }
        }
    }

    private fun handleAuthenticationFailure(onLogout: () -> Unit) {
        Log.e("TimetableViewModel", "Re-authentication failed")
        errorMessage = "Authentication failed. Please log in again."
        if (timetable == null) {
            onLogout()
        }
    }

    private fun handleMissingCredentials(onLogout: () -> Unit) {
        Log.e("TimetableViewModel", "No stored credentials found")
        errorMessage = "No credentials found. Please log in."
        if (timetable == null) {
            onLogout()
        }
    }

    private fun handleNoStoredCredentials(onLogout: () -> Unit) {
        if (timetable == null) {
            onLogout()
        }
    }

    suspend fun authenticateAndFetch(weekStart: LocalDate, onLogout: () -> Unit) {
        val needsRefresh = shouldRefreshData()

        if (credentialManager.hasStoredCredentialsBlocking()) {
            val username = credentialManager.getUsernameBlocking()
            val password = credentialManager.getPassword()

            if (username != null && password != null) {
                performAuthentication(username, password, weekStart, needsRefresh, onLogout)
            } else {
                handleMissingCredentials(onLogout)
            }
        } else {
            handleNoStoredCredentials(onLogout)
        }
    }
}
