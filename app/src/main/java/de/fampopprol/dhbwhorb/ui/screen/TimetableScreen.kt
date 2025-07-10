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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    // Disable back gesture while in timetable screen
    LaunchedEffect(Unit) {
        val activity = context as ComponentActivity
        val callback = activity.onBackPressedDispatcher.addCallback {
            // Intercept back press - do nothing to disable it
            // You can add custom logic here if needed
        }
        // The callback will be automatically removed when this composable is disposed
    }

    var timetable by remember { mutableStateOf<List<TimetableDay>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFetchingFromApi by remember { mutableStateOf(false) } // New state for API fetching
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Current week state - start with current week
    var currentWeekStart by remember {
        mutableStateOf(
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        )
    }

    // Function to fetch timetable for a specific week
    fun fetchTimetableForWeek(weekStart: LocalDate, isRefresh: Boolean = false) {
        errorMessage = null

        // 1. Try to load from cache first
        val cachedTimetable = timetableCacheManager.loadTimetable(weekStart)
        if (cachedTimetable != null) {
            timetable = cachedTimetable
            isLoading = false // Display cached data immediately
            Log.d("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Displaying cached timetable for week: $weekStart")
        } else {
            isLoading = true // Show loading if no cache
        }

        // 2. Always fetch from API in the background
        isFetchingFromApi = true
        Log.d("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Fetching timetable from API for week starting: $weekStart (refresh: $isRefresh)")

        dualisService.getWeeklySchedule(weekStart) { fetchedTimetable ->
            isFetchingFromApi = false
            isRefreshing = false

            if (fetchedTimetable != null) {
                Log.d("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Fetched Timetable for week starting $weekStart: $fetchedTimetable")

                // Compare with cached data and update if different
                if (fetchedTimetable != cachedTimetable) {
                    timetable = fetchedTimetable
                    timetableCacheManager.saveTimetable(weekStart, fetchedTimetable)
                    Log.d("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Timetable updated and cached for week: $weekStart")
                } else {
                    Log.d("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Fetched timetable is same as cached for week: $weekStart")
                }
                errorMessage = null
            } else {
                Log.e("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Failed to fetch timetable from API for week starting $weekStart")
                if (cachedTimetable == null) {
                    // Only show error if no cached data was available
                    errorMessage = "Failed to load timetable. Please try logging in again."
                }
            }
            isLoading = false // Hide loading after API call completes
        }
    }

    // Function to refresh current week data
    fun refreshCurrentWeek() {
        Log.d("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Pull-to-refresh triggered for week: $currentWeekStart")
        fetchTimetableForWeek(currentWeekStart, isRefresh = true)
    }

    // Function to navigate to previous week
    fun goToPreviousWeek() {
        currentWeekStart = currentWeekStart.minusWeeks(1)
        fetchTimetableForWeek(currentWeekStart)
    }

    // Function to navigate to next week
    fun goToNextWeek() {
        currentWeekStart = currentWeekStart.plusWeeks(1)
        fetchTimetableForWeek(currentWeekStart)
    }

    // Function to go to current week
    fun goToCurrentWeek() {
        currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fetchTimetableForWeek(currentWeekStart)
    }

    // Initial data loading and re-authentication logic
    LaunchedEffect(Unit) {
        if (credentialManager.hasStoredCredentials()) {
            val username = credentialManager.getUsername()
            val password = credentialManager.getPassword()

            if (username != null && password != null) {
                Log.d("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Re-authenticating with stored credentials")
                dualisService.login(username, password) { result ->
                    if (result != null) {
                        Log.d("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Re-authentication successful, fetching timetable")
                        fetchTimetableForWeek(currentWeekStart)
                    } else {
                        Log.e("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "Re-authentication failed")
                        isLoading = false
                        errorMessage = "Authentication failed. Please log in again."
                        credentialManager.logout()
                        onLogout()
                    }
                }
            } else {
                Log.e("de.fampopprol.dhbwhorb.ui.screen.TimetableScreen", "No stored credentials found")
                isLoading = false
                errorMessage = "No stored credentials found."
                onLogout()
            }
        } else {
            // If no stored credentials, try to fetch directly (user might have just logged in)
            fetchTimetableForWeek(currentWeekStart)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar with Logout button
        TopAppBar(
            title = { Text("Timetable") },
            actions = {
                OutlinedButton(
                    onClick = {
                        credentialManager.logout()
                        onLogout()
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Logout")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Loading indicator
        if (isFetchingFromApi) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Week Navigation Controls
        WeekNavigationBar(
            currentWeekStart = currentWeekStart,
            onPreviousWeek = ::goToPreviousWeek,
            onNextWeek = ::goToNextWeek,
            onCurrentWeek = ::goToCurrentWeek,
            isLoading = isFetchingFromApi // Use isFetchingFromApi for navigation button enabling
        )

        // Pull-to-refresh wrapper for the content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = ::refreshCurrentWeek,
            modifier = Modifier.fillMaxSize()
        ) {
            // Always display the calendar, even if timetable is null or empty
            WeeklyCalendar(
                timetable = timetable ?: emptyList(), // Provide empty list if null
                startOfWeek = currentWeekStart,
                onPreviousWeek = ::goToPreviousWeek,
                onNextWeek = ::goToNextWeek
            )

            // Display error message over the calendar if present and no data is loaded
            if (errorMessage != null && (timetable == null || timetable!!.isEmpty())) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { fetchTimetableForWeek(currentWeekStart) }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}