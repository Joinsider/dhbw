package de.fampopprol.dhbwhorb

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.dualis.network.DualisService
import de.fampopprol.dhbwhorb.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.components.WeeklyCalendar
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DHBWHorbTheme {
                val context = LocalContext.current
                val credentialManager = remember { CredentialManager(context) }
                var isLoggedIn by remember { mutableStateOf(credentialManager.isLoggedIn()) }
                val dualisService = remember { DualisService() }

                // Auto-login if credentials are stored
                LaunchedEffect(Unit) {
                    if (credentialManager.hasStoredCredentials() && !isLoggedIn) {
                        val username = credentialManager.getUsername()
                        val password = credentialManager.getPassword()
                        if (username != null && password != null) {
                            dualisService.login(username, password) { result ->
                                if (result != null) {
                                    isLoggedIn = true
                                    Log.d("MainActivity", "Auto-login successful")
                                } else {
                                    Log.e("MainActivity", "Auto-login failed")
                                    credentialManager.logout() // Clear invalid credentials
                                }
                            }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0.dp) // Remove default content padding
                ) { innerPadding ->
                    if (!isLoggedIn) {
                        LoginScreen(
                            dualisService = dualisService,
                            credentialManager = credentialManager,
                            onLoginSuccess = { isLoggedIn = true },
                            modifier = Modifier
                                .padding(innerPadding)
                                .windowInsetsPadding(WindowInsets.systemBars)
                        )
                    } else {
                        TimetableScreen(
                            dualisService = dualisService,
                            credentialManager = credentialManager,
                            onLogout = { isLoggedIn = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    dualisService: DualisService,
    credentialManager: CredentialManager,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf(credentialManager.getUsername() ?: "") }
    var password by remember { mutableStateOf("") }
    var rememberCredentials by remember { mutableStateOf(credentialManager.hasStoredCredentials()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DHBW Horb Login",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberCredentials,
                onCheckedChange = { rememberCredentials = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Remember credentials",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                dualisService.login(username, password) { result ->
                    isLoading = false
                    if (result != null) {
                        if (rememberCredentials) {
                            credentialManager.saveCredentials(username, password)
                        } else {
                            credentialManager.logout() // Clear any existing credentials
                        }
                        onLoginSuccess()
                        Log.d("LoginScreen", "Login successful")
                    } else {
                        errorMessage = "Login failed. Please check your credentials."
                        Log.e("LoginScreen", "Login failed")
                    }
                }
            },
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Logging in..." else "Login")
        }

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    dualisService: DualisService,
    credentialManager: CredentialManager,
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
        if (isRefresh) {
            isRefreshing = true
        } else {
            isLoading = true
        }
        errorMessage = null

        Log.d("TimetableScreen", "Fetching timetable for week starting: $weekStart (refresh: $isRefresh)")

        dualisService.getWeeklySchedule(weekStart) { fetchedTimetable ->
            isLoading = false
            isRefreshing = false
            if (fetchedTimetable != null) {
                Log.d("TimetableScreen", "Fetched Timetable for week starting $weekStart: $fetchedTimetable")
                timetable = fetchedTimetable
                errorMessage = null
            } else {
                Log.e("TimetableScreen", "Failed to fetch timetable for week starting $weekStart")
                errorMessage = "Failed to load timetable. Please try logging in again."
            }
        }
    }

    // Function to refresh current week data
    fun refreshCurrentWeek() {
        Log.d("TimetableScreen", "Pull-to-refresh triggered for week: $currentWeekStart")
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
                Log.d("TimetableScreen", "Re-authenticating with stored credentials")
                dualisService.login(username, password) { result ->
                    if (result != null) {
                        Log.d("TimetableScreen", "Re-authentication successful, fetching timetable")
                        fetchTimetableForWeek(currentWeekStart)
                    } else {
                        Log.e("TimetableScreen", "Re-authentication failed")
                        isLoading = false
                        errorMessage = "Authentication failed. Please log in again."
                        credentialManager.logout()
                        onLogout()
                    }
                }
            } else {
                Log.e("TimetableScreen", "No stored credentials found")
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

        // Week Navigation Controls
        WeekNavigationBar(
            currentWeekStart = currentWeekStart,
            onPreviousWeek = ::goToPreviousWeek,
            onNextWeek = ::goToNextWeek,
            onCurrentWeek = ::goToCurrentWeek,
            isLoading = isLoading || isRefreshing
        )

        // Pull-to-refresh wrapper for the content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = ::refreshCurrentWeek,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Loading timetable...",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                errorMessage != null -> {
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

                timetable != null && timetable!!.isNotEmpty() -> {
                    WeeklyCalendar(
                        timetable = timetable!!,
                        startOfWeek = currentWeekStart,
                        onPreviousWeek = ::goToPreviousWeek,
                        onNextWeek = ::goToNextWeek
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "No timetable data available for this week",
                                color = MaterialTheme.colorScheme.onBackground,
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
}

@Composable
fun WeekNavigationBar(
    currentWeekStart: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val weekEnd = currentWeekStart.plusDays(6)
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val isCurrentWeek = currentWeekStart == LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Date range display
            Text(
                text = "${dateFormatter.format(currentWeekStart)} - ${dateFormatter.format(weekEnd)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous week button
                Button(
                    onClick = onPreviousWeek,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("← Previous")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Current week button (only show if not already on current week)
                if (!isCurrentWeek) {
                    OutlinedButton(
                        onClick = onCurrentWeek,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Today")
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Next week button
                Button(
                    onClick = onNextWeek,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Next →")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    DHBWHorbTheme {
        LoginScreen(dualisService = DualisService(), credentialManager = CredentialManager(LocalContext.current), onLoginSuccess = {})
    }
}

@Preview(showBackground = true)
@Composable
fun TimetableScreenPreview() {
    DHBWHorbTheme {
        TimetableScreen(dualisService = DualisService(), credentialManager = CredentialManager(LocalContext.current), onLogout = {})
    }
}