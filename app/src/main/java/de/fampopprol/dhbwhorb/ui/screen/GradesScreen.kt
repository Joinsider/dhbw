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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.Module
import de.fampopprol.dhbwhorb.data.dualis.models.ExamState
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    dualisService: DualisService,
    modifier: Modifier = Modifier,
    credentialManager: CredentialManager? = null,
    gradesCacheManager: GradesCacheManager? = null
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var studyGrades by remember { mutableStateOf<StudyGrades?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // Semester selection state
    var availableSemesters by remember { mutableStateOf<List<Semester>>(emptyList()) }
    var selectedSemester by remember { mutableStateOf<Semester?>(null) }
    var isLoadingSemesters by remember { mutableStateOf(true) }

    // Store string resources in variables that can be accessed from non-composable functions
    val failedToLoadGradesMessage = stringResource(R.string.failed_to_load_grades)
    val authenticationFailedMessage = stringResource(R.string.authentication_failed)
    val noCredentialsFoundMessage = stringResource(R.string.no_credentials_found)
    val pleaseLoginMessage = stringResource(R.string.please_login)

    // Function to fetch grades data for a specific semester
    fun fetchGradesForSemester(semester: Semester, updateLoadingState: Boolean = true, forceRefresh: Boolean = false) {
        if (updateLoadingState && !isRefreshing) {
            isLoading = true
        }
        errorMessage = null

        scope.launch {
            // Check cache first if not forcing refresh
            if (!forceRefresh && gradesCacheManager != null) {
                if (gradesCacheManager.hasValidCachedGrades(semester)) {
                    val cachedData = gradesCacheManager.getCachedGrades()
                    if (cachedData != null) {
                        studyGrades = cachedData.first
                        selectedSemester = cachedData.second
                        isLoading = false
                        isRefreshing = false
                        Log.d("GradesScreen", "Using cached grades for semester: ${semester.displayName}")
                        return@launch
                    }
                }
            }

            // Fetch from network
            dualisService.getStudyGradesForSemester(semester) { result ->
                isLoading = false
                isRefreshing = false

                if (result != null) {
                    studyGrades = result
                    selectedSemester = semester
                    errorMessage = null

                    // Cache the result
                    scope.launch {
                        gradesCacheManager?.cacheGrades(result, semester)
                    }

                    Log.d("GradesScreen", "Fetched grades for semester ${semester.displayName}: $result")
                } else {
                    errorMessage = failedToLoadGradesMessage
                    Log.e("GradesScreen", "Failed to fetch grades for semester ${semester.displayName}")
                }
            }
        }
    }

    // Function to fetch available semesters
    fun fetchSemesters(forceRefresh: Boolean = false) {
        isLoadingSemesters = true

        scope.launch {
            // Check cache first if not forcing refresh
            if (!forceRefresh && gradesCacheManager != null) {
                val cachedSemesters = gradesCacheManager.getCachedSemesters()
                if (cachedSemesters != null) {
                    availableSemesters = cachedSemesters
                    isLoadingSemesters = false

                    // Select the appropriate semester
                    val defaultSemester = cachedSemesters.find { it.isSelected } ?: cachedSemesters.first()
                    if (selectedSemester == null) {
                        selectedSemester = defaultSemester
                        fetchGradesForSemester(defaultSemester, false)
                    }

                    Log.d("GradesScreen", "Using cached semesters: ${cachedSemesters.size} semesters")
                    return@launch
                }
            }

            // Fetch from network
            dualisService.getAvailableSemesters { semesters ->
                isLoadingSemesters = false
                if (semesters != null && semesters.isNotEmpty()) {
                    availableSemesters = semesters

                    // Cache the semesters
                    scope.launch {
                        gradesCacheManager?.cacheSemesters(semesters)
                    }

                    // Select the first semester that is marked as selected, or the first one if none are selected
                    val defaultSemester = semesters.find { it.isSelected } ?: semesters.first()
                    selectedSemester = defaultSemester
                    // Fetch grades for the default semester
                    fetchGradesForSemester(defaultSemester, false, forceRefresh)
                    Log.d("GradesScreen", "Fetched ${semesters.size} semesters, selected: ${defaultSemester.displayName}")
                } else {
                    // Fallback to default semester selection if fetching fails
                    val defaultSemesters = Semester.getDefaultSemesters()
                    availableSemesters = defaultSemesters
                    val defaultSemester = defaultSemesters.find { it.isSelected } ?: defaultSemesters.first()
                    selectedSemester = defaultSemester
                    fetchGradesForSemester(defaultSemester, false, forceRefresh)
                    Log.w("GradesScreen", "Failed to fetch semesters, using defaults")
                }
            }
        }
    }

    // Function to ensure authentication before fetching data
    fun ensureAuthAndFetchData(forceRefresh: Boolean = false) {
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
                        Log.d("GradesScreen", "Re-authenticating before fetching data")
                        dualisService.login(username, password) { result ->
                            if (result != null) {
                                Log.d("GradesScreen", "Authentication successful, now fetching semesters")
                                // Now we can fetch semesters and grades after successful authentication
                                fetchSemesters(forceRefresh)
                            } else {
                                isLoading = false
                                isRefreshing = false
                                errorMessage = authenticationFailedMessage
                                Log.e("GradesScreen", "Authentication failed")
                            }
                        }
                    } else {
                        isLoading = false
                        isRefreshing = false
                        errorMessage = noCredentialsFoundMessage
                        Log.e("GradesScreen", "No stored credentials found")
                    }
                } else {
                    isLoading = false
                    isRefreshing = false
                    errorMessage = pleaseLoginMessage
                    Log.e("GradesScreen", "No stored credentials found")
                }
            }
        } else {
            // Already authenticated, fetch semesters and grades directly
            fetchSemesters(forceRefresh)
        }
    }

    // Initial data loading
    LaunchedEffect(Unit) {
        ensureAuthAndFetchData()
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
                selectedSemester?.let { semester ->
                    fetchGradesForSemester(semester, false, forceRefresh = true)
                } ?: ensureAuthAndFetchData(forceRefresh = true)
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
                                text = if (isLoadingSemesters) {
                                    stringResource(R.string.loading_semesters)
                                } else {
                                    stringResource(R.string.loading_grades)
                                },
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
                            onClick = { ensureAuthAndFetchData(forceRefresh = true) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.retry)
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                } else {
                    // Show grades content with semester selection
                    GradesContent(
                        studyGrades = studyGrades,
                        isRefreshing = isRefreshing,
                        availableSemesters = availableSemesters,
                        selectedSemester = selectedSemester,
                        onSemesterSelected = { semester ->
                            selectedSemester = semester
                            fetchGradesForSemester(semester)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesContent(
    studyGrades: StudyGrades?,
    isRefreshing: Boolean,
    availableSemesters: List<Semester>,
    selectedSemester: Semester?,
    onSemesterSelected: (Semester) -> Unit,
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
            text = stringResource(R.string.your_study_performance),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )

        // Semester Selection
        Column {
            Text(
                text = stringResource(R.string.select_semester),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Semester dropdown
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedSemester?.displayName ?: "",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.select_semester)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // Menu items for each available semester
                    availableSemesters.forEach { semester ->
                        DropdownMenuItem(
                            onClick = {
                                onSemesterSelected(semester)
                                expanded = false
                            },
                            text = {
                                Text(
                                    text = semester.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        )
                    }
                }
            }
        }

        // GPA Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.grade_point_average),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.overall_gpa),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = studyGrades?.gpaTotal?.toString() ?: stringResource(R.string.not_available),
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
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.credit_points),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.credits_gained),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = studyGrades?.creditsGained?.toString() ?: stringResource(R.string.not_available),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.credits_total),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = studyGrades?.creditsTotal?.toString() ?: stringResource(R.string.not_available),
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
                            text = stringResource(R.string.progress_completed, String.format(Locale.getDefault(), "%.1f", progress * 100)),
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
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.individual_modules),
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
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    text = stringResource(R.string.detailed_module_info),
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                        text = if (module.grade == "noch nicht gesetzt") stringResource(R.string.not_graded) else module.grade,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (module.state) {
                            ExamState.PASSED -> MaterialTheme.colorScheme.primary
                            ExamState.FAILED -> MaterialTheme.colorScheme.error
                            ExamState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = "${module.credits} ${stringResource(R.string.credit_points_short)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status indicator
            val statusText = when (module.state) {
                ExamState.PASSED -> stringResource(R.string.passed)
                ExamState.FAILED -> stringResource(R.string.failed)
                ExamState.PENDING -> stringResource(R.string.pending)
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
