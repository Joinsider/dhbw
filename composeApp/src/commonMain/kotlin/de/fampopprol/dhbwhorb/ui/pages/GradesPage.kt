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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource

import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GradesPage(
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isLoggedIn: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: GradesViewModel = koinInject()
) {

    val uiState = viewModel.uiState
    val hapticFeedback = LocalHapticFeedback.current

    // If we were previously blocked due to missing login and the app is now logged in, try again once
    // Local val: smart casts do not cross module boundaries since the state moved to :presentation.
    val errorMessage = uiState.error

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && uiState.requiresLogin) {
            viewModel.loadSemesters()
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
            } else if ((uiState.isLoading || uiState.isLoadingSemesters || viewModel == null) && uiState.grades.isEmpty()) {
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
                            modifier = Modifier.testTag("gradesPageTitle").padding(bottom = 24.dp)
                        )
                    }
                    items(6) {
                        GradeCardSkeleton()
                    }
                }
            } else if (errorMessage != null && uiState.grades.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            // Local val: smart casts do not cross module boundaries.
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Button(
                            onClick = { viewModel.loadSemesters() }
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
                        viewModel.refreshGrades()
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
                                modifier = Modifier.testTag("gradesPageTitle").padding(bottom = 24.dp)
                            )
                        }

                        item {
                            SemesterSelector(
                                semesters = uiState.semesters,
                                selectedSemesterId = uiState.selectedSemesterId,
                                onSemesterSelected = { viewModel.selectSemester(it) },
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
                            val semesterGpa = uiState.semesterGpa
                            if (semesterGpa != null) {
                                item {
                                    GpaSummaryCard(gpa = semesterGpa)
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
