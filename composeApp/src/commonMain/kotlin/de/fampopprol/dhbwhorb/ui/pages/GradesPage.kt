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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.presentation.grades.GradesIntent
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.ui.store.collectState
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.ui.error.toUserMessage
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.grades
import de.fampopprol.dhbwhorb.resources.login_required_for_grades
import de.fampopprol.dhbwhorb.resources.retry
import de.fampopprol.dhbwhorb.ui.grades.components.GpaSummaryCard
import de.fampopprol.dhbwhorb.ui.grades.components.GradeCard
import de.fampopprol.dhbwhorb.ui.grades.components.OverallStatsCard
import de.fampopprol.dhbwhorb.ui.grades.components.SemesterGroupCard
import de.fampopprol.dhbwhorb.ui.grades.components.SemesterSelector
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
    /** Semester to preselect, from a deep link; null shows the combined view. */
    initialSemesterId: String? = null,
    onNavigate: (BottomNavItem) -> Unit = {},
    modifier: Modifier = Modifier,
    store: GradesStore = koinInject()
) {

    val uiState by store.collectState()
    val computeGpa: ComputeGpa = koinInject()
    val hapticFeedback = LocalHapticFeedback.current

    // If we were previously blocked due to missing login and the app is now logged in, try again once
    // Local val: smart casts do not cross module boundaries since the state moved to :presentation.
    val error = uiState.error

    // The store outlives the composition, so this loads once and costs nothing on a tab switch.
    LaunchedEffect(Unit) { store.dispatch(GradesIntent.EnsureLoaded) }

    // A deep link names a semester; select it once the list it belongs to has arrived.
    LaunchedEffect(initialSemesterId, uiState.semesters) {
        if (initialSemesterId == null) return@LaunchedEffect
        uiState.semesters.firstOrNull { it.id == initialSemesterId }?.let { semester ->
            if (uiState.selectedSemester?.id != semester.id) {
                store.dispatch(GradesIntent.SemesterSelected(semester))
            }
        }
    }

    Scaffold(
        modifier = if (isMobilePlatform()) {
            modifier.statusBarsPadding()
        } else {
            modifier
        },
        bottomBar = {
            BottomNavigationBar(
                currentItem = BottomNavItem.GRADES,
                onItemSelected = onNavigate
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 20.dp)
        ) {
            if (uiState.requiresLogin) {
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
            } else if ((uiState.isLoading || uiState.isLoadingSemesters) && uiState.grades.isEmpty()) {
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
            } else if (error != null && uiState.grades.isEmpty()) {
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
                            text = error.toUserMessage(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Button(
                            onClick = { store.dispatch(GradesIntent.Load) }
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
                        store.dispatch(GradesIntent.Refresh)
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
                                selectedSemester = uiState.selectedSemester,
                                onSemesterSelected = { store.dispatch(GradesIntent.SemesterSelected(it)) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Show different content based on selection
                        if (uiState.isShowingAllSemesters) {
                            // Overview mode - show overall statistics
                            if (uiState.overallGpa != null || uiState.totalCreditsEarned > 0) {
                                item {
                                    OverallStatsCard(
                                        overallGpa = uiState.overallGpa,
                                        totalCredits = uiState.totalCreditsEarned,
                                        modulesCompleted = uiState.modulesCompleted
                                    )
                                }
                            }

                            // Group grades by semester and show collapsible cards
                            val gradesBySemester = uiState.grades.groupBy { it.semesterName }
                            gradesBySemester.forEach { (semesterName, semesterGrades) ->
                                item {
                                    val semesterGpa = computeGpa(semesterGrades).average
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

