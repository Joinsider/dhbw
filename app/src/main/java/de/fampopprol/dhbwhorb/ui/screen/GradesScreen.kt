package de.fampopprol.dhbwhorb.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.Module
import de.fampopprol.dhbwhorb.data.dualis.models.ExamState
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    dualisService: DualisService,
    modifier: Modifier = Modifier,
    credentialManager: CredentialManager? = null
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var studyGrades by remember { mutableStateOf<StudyGrades?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // Function to fetch grades data
    fun fetchGrades(updateLoadingState: Boolean = true) {
        if (updateLoadingState && !isRefreshing) {
            isLoading = true
        }
        errorMessage = null

        dualisService.getStudyGrades { result ->
            isLoading = false
            isRefreshing = false

            if (result != null) {
                studyGrades = result
                errorMessage = null
                Log.d("GradesScreen", "Fetched grades: $result")
            } else {
                errorMessage = "Failed to load grades. Please try again."
                Log.e("GradesScreen", "Failed to fetch grades")
            }
        }
    }

    // Function to ensure authentication before fetching grades
    fun ensureAuthAndFetchGrades() {
        if (!isRefreshing) {
            isLoading = true
        }
        errorMessage = null

        // Check if we need to authenticate first
        if (!dualisService.isAuthenticated() && credentialManager != null) {
            scope.launch {
                val hasStoredCredentials = credentialManager.hasStoredCredentialsBlocking()
                if (hasStoredCredentials) {
                    val username = credentialManager.getUsernameBlocking()
                    val password = credentialManager.getPassword()

                    if (username != null && password != null) {
                        Log.d("GradesScreen", "Re-authenticating before fetching grades")
                        dualisService.login(username, password) { result ->
                            if (result != null) {
                                Log.d("GradesScreen", "Authentication successful, now fetching grades")
                                // Now we can fetch grades after successful authentication
                                fetchGrades(false)
                            } else {
                                isLoading = false
                                isRefreshing = false
                                errorMessage = "Authentication failed. Please login again."
                                Log.e("GradesScreen", "Authentication failed")
                            }
                        }
                    } else {
                        isLoading = false
                        isRefreshing = false
                        errorMessage = "No credentials found. Please login again."
                        Log.e("GradesScreen", "No stored credentials found")
                    }
                } else {
                    isLoading = false
                    isRefreshing = false
                    errorMessage = "Please login to view your grades."
                    Log.e("GradesScreen", "No stored credentials found")
                }
            }
        } else {
            // Already authenticated, fetch grades directly
            fetchGrades(false)
        }
    }

    // Initial data loading
    LaunchedEffect(Unit) {
        ensureAuthAndFetchGrades()
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
                ensureAuthAndFetchGrades()
            },
            isRefreshing = isRefreshing,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isLoading && !isRefreshing) {
                    // Show loading indicator when initially loading
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(16.dp)
                            )
                            Text(
                                text = "Loading grades...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (errorMessage != null && studyGrades == null) {
                    // Show error message
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { ensureAuthAndFetchGrades() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry"
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("Retry")
                        }
                    }
                } else {
                    // Show grades content
                    GradesContent(
                        studyGrades = studyGrades,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun GradesContent(
    studyGrades: StudyGrades?,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Show a thin progress indicator at top during refresh
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            // Empty spacer to maintain layout stability
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Grades Header
        Text(
            text = "Your Study Performance",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )

        // GPA Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Grade Point Average (GPA)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Overall GPA",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = studyGrades?.gpaTotal?.toString() ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Main Modules GPA",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = studyGrades?.gpaMainModules?.toString() ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Credits Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Credit Points",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Credits Gained",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = studyGrades?.creditsGained?.toString() ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Credits Total",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = studyGrades?.creditsTotal?.toString() ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress Bar
                if (studyGrades != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = if (studyGrades.creditsTotal > 0) {
                        (studyGrades.creditsGained / studyGrades.creditsTotal).toFloat()
                    } else 0f

                    Column {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        )

                        Text(
                            text = "${String.format("%.1f", progress * 100)}% completed",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }

        // Individual Modules Section
        if (studyGrades != null && studyGrades.modules.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Individual Modules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    studyGrades.modules.forEach { module ->
                        ModuleCard(module = module)
                    }
                }
            }
        }

        // Additional info message
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Text(
                    text = "For detailed module information including individual exams, please visit the Dualis website.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ModuleCard(
    module: Module,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (module.state) {
                ExamState.PASSED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ExamState.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ExamState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when (module.state) {
                ExamState.PASSED -> MaterialTheme.colorScheme.onPrimaryContainer
                ExamState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                ExamState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = module.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (module.grade == "noch nicht gesetzt") "Not graded" else module.grade,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (module.state) {
                            ExamState.PASSED -> MaterialTheme.colorScheme.primary
                            ExamState.FAILED -> MaterialTheme.colorScheme.error
                            ExamState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = "${module.credits} CP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status indicator
            val statusText = when (module.state) {
                ExamState.PASSED -> "Passed"
                ExamState.FAILED -> "Failed"
                ExamState.PENDING -> "Pending"
            }

            val statusIcon = when (module.state) {
                ExamState.PASSED -> "✓"
                ExamState.FAILED -> "✗"
                ExamState.PENDING -> "⏱"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusIcon,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (module.state) {
                        ExamState.PASSED -> MaterialTheme.colorScheme.primary
                        ExamState.FAILED -> MaterialTheme.colorScheme.error
                        ExamState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
