package de.fampopprol.dhbwhorb.ui.screen

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.components.WeekNavigationBar
import de.fampopprol.dhbwhorb.ui.components.WeeklyCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    dualisService: DualisService,
    credentialManager: CredentialManager,
    timetableCacheManager: TimetableCacheManager,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()


    var timetable by remember { mutableStateOf<List<TimetableDay>?>(null) }
    var isFetchingFromApi by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }

    // Current week state - start with current week
    var currentWeekStart by remember {
        mutableStateOf(
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        )
    }

    // Function to update last updated timestamp
    fun updateLastUpdatedTimestamp() {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        lastUpdated = java.time.LocalDateTime.now().format(formatter)
    }

    // Function to load timetable data from cache
    fun loadCachedTimetable(weekStart: LocalDate): Boolean {
        val cachedTimetable = timetableCacheManager.loadTimetable(weekStart)
        if (cachedTimetable != null) {
            timetable = cachedTimetable
            // Don't update lastUpdated when loading from cache - keep the last API fetch time
            Log.d("TimetableScreen", "Displaying cached timetable for week: $weekStart")
            return true
        }
        return false
    }

    // Function to fetch timetable from API
    fun fetchTimetableFromApi(weekStart: LocalDate, isForced: Boolean = false) {
        // Only fetch if not already fetching
        if (isFetchingFromApi && !isForced) return

        isFetchingFromApi = true
        Log.d("TimetableScreen", "Fetching timetable from API for week starting: $weekStart (forced: $isForced)")

        dualisService.getWeeklySchedule(weekStart) { fetchedTimetable ->
            isFetchingFromApi = false
            isRefreshing = false

            if (fetchedTimetable != null) {
                Log.d("TimetableScreen", "Fetched Timetable for week starting $weekStart: $fetchedTimetable")

                // Compare with current data and update if different
                if (timetable != fetchedTimetable) {
                    timetable = fetchedTimetable
                    timetableCacheManager.saveTimetable(weekStart, fetchedTimetable)
                    Log.d("TimetableScreen", "Timetable updated and cached for week: $weekStart")
                } else {
                    Log.d("TimetableScreen", "Fetched timetable is same as current for week: $weekStart")
                }

                // Always update timestamp when data is successfully fetched from API
                updateLastUpdatedTimestamp()
                errorMessage = null
            } else {
                Log.e("TimetableScreen", "Failed to fetch timetable from API for week starting $weekStart")
                if (timetable == null) {
                    // Only show error if no data was available
                    errorMessage = "Failed to load timetable. Please try logging in again."
                }
            }
        }
    }

    // Function to handle week change (navigate to a different week)
    fun changeWeek(weekStart: LocalDate) {
        currentWeekStart = weekStart

        // First try to load from cache immediately
        val foundInCache = loadCachedTimetable(weekStart)

        // Then fetch from API to ensure data is fresh
        fetchTimetableFromApi(weekStart, isForced = !foundInCache)
    }

    // Function to refresh current week data
    fun refreshCurrentWeek() {
        Log.d("TimetableScreen", "Pull-to-refresh triggered for week: $currentWeekStart")
        isRefreshing = true
        fetchTimetableFromApi(currentWeekStart, isForced = true)
    }

    // Function to navigate to previous week
    fun goToPreviousWeek() {
        changeWeek(currentWeekStart.minusWeeks(1))
    }

    // Function to navigate to next week
    fun goToNextWeek() {
        changeWeek(currentWeekStart.plusWeeks(1))
    }

    // Function to go to current week
    fun goToCurrentWeek() {
        changeWeek(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }

    // Load cached data immediately on screen initialization
    LaunchedEffect(Unit) {
        loadCachedTimetable(currentWeekStart)
    }

    // Initial data loading and re-authentication logic
    LaunchedEffect(credentialManager, dualisService) {
        // Check if the cached data needs refreshing
        val needsRefresh = if (timetable == null) true else {
            // Get the last day from the timetable
            val today = LocalDate.now()
            val lastCacheDay = if (timetable?.isNotEmpty() == true) {
                try {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                    LocalDate.parse(timetable!!.last().date, formatter)
                } catch (e: Exception) {
                    Log.e("TimetableScreen", "Error parsing last cache day", e)
                    null
                }
            } else null

            // Refresh if no cached data or if it's stale (more than half a day old)
            // We use DAYS instead of HOURS since LocalDate doesn't have time information
            lastCacheDay == null || ChronoUnit.DAYS.between(lastCacheDay, today) > 0
        }

        if (credentialManager.hasStoredCredentialsBlocking()) {
            val username = credentialManager.getUsernameBlocking()
            val password = credentialManager.getPassword()

            if (username != null && password != null) {
                Log.d("TimetableScreen", "Re-authenticating with stored credentials")
                dualisService.login(username, password) { result ->
                    if (result != null) {
                        Log.d("TimetableScreen", "Re-authentication successful, fetching timetable")
                        fetchTimetableFromApi(currentWeekStart, isForced = needsRefresh)
                    } else {
                        Log.e("TimetableScreen", "Re-authentication failed")
                        errorMessage = "Authentication failed. Please log in again."
                        if (timetable == null) {
                            // If no cached data and auth failed, show error to user
                            onLogout()
                        }
                    }
                }
            } else {
                Log.e("TimetableScreen", "No stored credentials found")
                errorMessage = "No credentials found. Please log in."
                if (timetable == null) {
                    onLogout()
                }
            }
        } else {
            // No stored credentials, redirect to login if no cached data
            if (timetable == null) {
                onLogout()
            }
        }
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            state = pullRefreshState,
            onRefresh = {
                isRefreshing = true
                refreshCurrentWeek()
            },
            isRefreshing = isRefreshing,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Week navigation bar
                WeekNavigationBar(
                    currentWeekStart = currentWeekStart,
                    onPreviousWeek = { goToPreviousWeek() },
                    onNextWeek = { goToNextWeek() },
                    onCurrentWeek = { goToCurrentWeek() },
                    isLoading = isFetchingFromApi,
                    lastUpdated = lastUpdated,
                    modifier = Modifier.fillMaxWidth()
                )

                // Show loading indicator for API requests
                if (isFetchingFromApi) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Error message or weekly calendar
                if (errorMessage != null && timetable == null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    refreshCurrentWeek()
                                }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                } else {
                    // Calendar with timetable data (even if it's null, the component handles empty state)
                    WeeklyCalendar(
                        timetable = timetable ?: emptyList(),
                        startOfWeek = currentWeekStart,
                        onPreviousWeek = { goToPreviousWeek() },
                        onNextWeek = { goToNextWeek() },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}