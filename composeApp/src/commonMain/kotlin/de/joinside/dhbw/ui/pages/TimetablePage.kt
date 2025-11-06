package de.joinside.dhbw.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.ui.navigation.BottomNavItem
import de.joinside.dhbw.ui.navigation.BottomNavigationBar
import de.joinside.dhbw.ui.schedule.views.WeeklyLecturesView
import de.joinside.dhbw.util.isMobilePlatform
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun TimetablePage(
    viewModel: TimetableViewModel? = null,
    onNavigateToGrades: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isLoggedIn: Boolean = true,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel?.uiState ?: TimetableUiState()

    // Reload lectures when page is displayed
    LaunchedEffect(Unit) {
        viewModel?.loadLecturesForCurrentWeek()
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
                    currentItem = BottomNavItem.TIMETABLE,
                    onItemSelected = { item ->
                        when (item) {
                            BottomNavItem.TIMETABLE -> { /* Already here */ }
                            BottomNavItem.GRADES -> onNavigateToGrades()
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
            when {
                uiState.isLoading -> {
                    // Show loading indicator
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading lectures...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
                uiState.error != null -> {
                    // Show error message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = uiState.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                else -> {
                    // Show lectures
                    WeeklyLecturesView(
                        lectures = uiState.lectures,
                        weekLabel = uiState.weekLabel,
                        onPreviousWeek = { viewModel?.goToPreviousWeek() },
                        onNextWeek = { viewModel?.goToNextWeek() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Preview version with mock data for design testing
 */
@Composable
@Preview
fun TimetablePagePreview() {
    TimetablePage(
        viewModel = null,
        isLoggedIn = true
    )
}
