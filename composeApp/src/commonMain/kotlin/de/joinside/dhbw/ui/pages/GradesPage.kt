package de.joinside.dhbw.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.data.storage.database.entities.grades.GradeEntity
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.grades
import de.joinside.dhbw.resources.login_required_for_grades
import de.joinside.dhbw.ui.grades.components.GpaSummaryCard
import de.joinside.dhbw.ui.grades.components.GradeCard
import de.joinside.dhbw.ui.grades.components.OverallStatsCard
import de.joinside.dhbw.ui.grades.components.SemesterGroupCard
import de.joinside.dhbw.ui.grades.components.SemesterSelector
import de.joinside.dhbw.ui.grades.viewModels.ALL_SEMESTERS_ID
import de.joinside.dhbw.ui.grades.viewModels.GradesUiState
import de.joinside.dhbw.ui.grades.viewModels.GradesViewModel
import de.joinside.dhbw.ui.navigation.BottomNavItem
import de.joinside.dhbw.ui.navigation.BottomNavigationBar
import de.joinside.dhbw.util.isMobilePlatform
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GradesPage(
    viewModel: GradesViewModel? = null,
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isLoggedIn: Boolean = true,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel?.uiState ?: GradesUiState()
    val hapticFeedback = LocalHapticFeedback.current

    // If we were previously blocked due to missing login and the app is now logged in, try again once
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && uiState.requiresLogin) {
            viewModel?.loadSemesters()
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
            } else if (uiState.isLoading && uiState.grades.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else if (uiState.error != null && uiState.grades.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel?.refreshGrades() },
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        if (uiState.isRefreshing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 58.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                LoadingIndicator()
                            }
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                    }
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
                                onSemesterSelected = { viewModel?.selectSemester(it) },
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
