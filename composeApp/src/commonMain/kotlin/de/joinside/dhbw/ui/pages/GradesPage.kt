package de.joinside.dhbw.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Grading
import androidx.compose.material.icons.filled.Grading
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.grades
import de.joinside.dhbw.ui.grades.components.GpaSummaryCard
import de.joinside.dhbw.ui.grades.components.GradeCard
import de.joinside.dhbw.ui.grades.components.SemesterSelector
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
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            if (uiState.isLoading && uiState.grades.isEmpty()) {
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(Res.string.grades),
                                style = MaterialTheme.typography.headlineLargeEmphasized,
                                modifier = Modifier.padding(16.dp),
                                fontWeight = Bold
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

                        if (uiState.semesterGpa != null) {
                            item {
                                GpaSummaryCard(gpa = uiState.semesterGpa)
                            }
                        }

                        items(uiState.grades) { grade ->
                            GradeCard(grade = grade)
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