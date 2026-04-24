package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.grades
import de.fampopprol.dhbwhorb.resources.login_required_for_grades
import de.fampopprol.dhbwhorb.resources.retry
import de.fampopprol.dhbwhorb.ui.grades.components.GpaSummaryCard
import de.fampopprol.dhbwhorb.ui.grades.components.GradeCard
import de.fampopprol.dhbwhorb.ui.grades.components.OverallStatsCard
import de.fampopprol.dhbwhorb.ui.grades.components.SemesterGroupCard
import de.fampopprol.dhbwhorb.ui.grades.components.SemesterSelector
import de.fampopprol.dhbwhorb.ui.grades.viewModels.ALL_SEMESTERS_ID
import de.fampopprol.dhbwhorb.ui.grades.viewModels.GradesUiState
import de.fampopprol.dhbwhorb.ui.grades.viewModels.GradesViewModel
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavigationBar
import de.fampopprol.dhbwhorb.ui.components.GradeCardSkeleton
import de.fampopprol.dhbwhorb.util.isMobilePlatform
import org.jetbrains.compose.resources.stringResource

import androidx.compose.runtime.remember
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisGradeService
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import io.ktor.client.HttpClient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GradesPage(
    viewModel: GradesViewModel? = null,
    database: AppDatabase? = null,
    authenticationService: AuthenticationService? = null,
    sharedHttpClient: HttpClient? = null,
    sessionManager: SessionManager? = null,
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isLoggedIn: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Truly lazy initialization of ViewModel if not provided
    val actualViewModel = viewModel ?: remember(database, authenticationService, sharedHttpClient, sessionManager) {
        if (database != null && authenticationService != null && sharedHttpClient != null && sessionManager != null) {
            val gradeService = DualisGradeService(
                apiClient = DualisApiClient(sharedHttpClient),
                sessionManager = sessionManager,
                authenticationService = authenticationService,
                gradeDao = database.gradeDao(),
                gradeCacheMetadataDao = database.gradeCacheMetadataDao()
            )
            GradesViewModel(gradeService, database.gradeDao())
        } else {
            null
        }
    }

    // Call cleanup on disposal
    DisposableEffect(actualViewModel) {
        onDispose {
            actualViewModel?.cleanup()
        }
    }

    val uiState = actualViewModel?.uiState ?: GradesUiState()
    val hapticFeedback = LocalHapticFeedback.current

    // If we were previously blocked due to missing login and the app is now logged in, try again once
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && uiState.requiresLogin) {
            actualViewModel?.loadSemesters()
        }
    }

    Scaffold(
        modifier = if (isMobilePlatform()) {
            modifier.statusBarsPadding()
        } else {
            modifier
        },
        bottomBar = {
            if (isLoggedIn) {
                BottomNavigationBar(
                    currentItem = BottomNavItem.GRADES,
                    onItemSelected = { item ->
                        when (item) {
                            BottomNavItem.TIMETABLE -> onNavigateToTimetable()
                            BottomNavItem.GRADES -> { /* Already here */
                            }
                            BottomNavItem.DOCUMENTS -> onNavigateToDocuments()
                            BottomNavItem.SETTINGS -> onNavigateToSettings()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 20.dp)
        ) {
            if (uiState.requiresLogin && !isLoggedIn) {
                // Friendly message instead of an error when not logged in
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.login_required_for_grades),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if ((uiState.isLoading || uiState.isLoadingSemesters || actualViewModel == null) && uiState.grades.isEmpty()) {
                // Skeleton UI for Grades
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(
                            text = stringResource(Res.string.grades),
                            style = MaterialTheme.typography.headlineLargeEmphasized,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                    items(6) {
                        GradeCardSkeleton()
                    }
                }
            } else if (uiState.error != null && uiState.grades.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = uiState.error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Button(
                            onClick = { actualViewModel?.loadSemesters() }
                        ) {
                            Text(text = stringResource(Res.string.retry))
                        }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        actualViewModel?.refreshGrades()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(
                                text = stringResource(Res.string.grades),
                                style = MaterialTheme.typography.headlineLargeEmphasized,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }

                        item {
                            SemesterSelector(
                                semesters = uiState.semesters,
                                selectedSemesterId = uiState.selectedSemesterId,
                                onSemesterSelected = { actualViewModel?.selectSemester(it) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Show different content based on selection
                        if (uiState.selectedSemesterId == ALL_SEMESTERS_ID) {
                            // Overview mode - show overall statistics
                            if (uiState.overallGpa != null || uiState.totalCreditsEarned > 0) {
                                item {
                                    OverallStatsCard(
                                        overallGpa = uiState.overallGpa,
                                        totalCredits = uiState.totalCreditsEarned,
                                        modulesCompleted = uiState.grades.count { it.grade != null }
                                    )
                                }
                            }

                            // Group grades by semester and show collapsible cards
                            val gradesBySemester = uiState.grades.groupBy { it.semesterName }
                            gradesBySemester.forEach { (semesterName, semesterGrades) ->
                                item {
                                    val semesterGpa = calculateSemesterGpa(semesterGrades)
                                    SemesterGroupCard(
                                        semesterName = semesterName,
                                        grades = semesterGrades.sortedBy { it.moduleName },
                                        semesterGpa = semesterGpa
                                    )
                                }
                            }
                        } else {
                            // Single semester mode - show as before
                            if (uiState.semesterGpa != null) {
                                item {
                                    GpaSummaryCard(gpa = uiState.semesterGpa)
                                }
                            }

                            items(uiState.grades) { grade ->
                                GradeCard(grade = grade)
                            }
                        }

                        // Spacer for bottom padding to avoid overlapping with FAB or similar if added
                        item {
                            Box(modifier = Modifier.padding(bottom = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

// Helper function to calculate GPA for a list of grades
private fun calculateSemesterGpa(grades: List<GradeEntity>): Double? {
    var totalWeightedPoints = 0.0
    var totalCredits = 0.0

    for (grade in grades) {
        val gradeValueStr = grade.grade?.replace(",", ".")
        val gradeValue = gradeValueStr?.toDoubleOrNull()

        if (gradeValue != null && grade.credits > 0) {
            totalWeightedPoints += gradeValue * grade.credits
            totalCredits += grade.credits
        }
    }

    return if (totalCredits > 0) {
        totalWeightedPoints / totalCredits
    } else {
        null
    }
}
