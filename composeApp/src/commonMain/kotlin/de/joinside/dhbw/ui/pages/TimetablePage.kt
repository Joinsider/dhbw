package de.joinside.dhbw.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.april_short
import de.joinside.dhbw.resources.august_short
import de.joinside.dhbw.resources.december_short
import de.joinside.dhbw.resources.error_loading_lectures
import de.joinside.dhbw.resources.february_short
import de.joinside.dhbw.resources.january_short
import de.joinside.dhbw.resources.july_short
import de.joinside.dhbw.resources.june_short
import de.joinside.dhbw.resources.loading_lectures
import de.joinside.dhbw.resources.march_short
import de.joinside.dhbw.resources.may_short
import de.joinside.dhbw.resources.november_short
import de.joinside.dhbw.resources.october_short
import de.joinside.dhbw.resources.september_short
import de.joinside.dhbw.resources.this_week
import de.joinside.dhbw.ui.navigation.BottomNavItem
import de.joinside.dhbw.ui.navigation.BottomNavigationBar
import de.joinside.dhbw.ui.schedule.modules.dialogs.LectureDetailsDialog
import de.joinside.dhbw.ui.schedule.models.LectureModel
import de.joinside.dhbw.ui.schedule.viewModels.TimetableUiState
import de.joinside.dhbw.ui.schedule.viewModels.TimetableViewModel
import de.joinside.dhbw.ui.schedule.viewModels.WeekLabelData
import de.joinside.dhbw.ui.schedule.views.WeeklyLecturesView
import de.joinside.dhbw.util.isMobilePlatform
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class, InternalResourceApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun TimetablePage(
    viewModel: TimetableViewModel? = null,
    onNavigateToGrades: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isLoggedIn: Boolean = true,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel?.uiState ?: TimetableUiState()

    // State for selected lecture dialog
    var selectedLecture by remember { mutableStateOf<LectureModel?>(null) }

    val hapticFeedback = LocalHapticFeedback.current

    //  lectures when page is displayed
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            LoadingIndicator()
                            Text(
                                text = stringResource(Res.string.loading_lectures),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp),
                                textAlign = TextAlign.Center
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
                                text = stringResource(Res.string.error_loading_lectures),
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
                    // Show lectures with pull-to-refresh
                    // Format week label from WeekLabelData
                    val weekLabel = uiState.weekLabelData?.let { data ->
                        formatWeekLabel(data)
                    } ?: stringResource(Res.string.this_week)

                    if (isMobilePlatform()) {
                        // Mobile: Use pull-to-refresh with Material 3 Expressive LoadingIndicator
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = {
                                viewModel?.refreshLectures()
                            },
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
                            WeeklyLecturesView(
                                lectures = uiState.lectures,
                                weekLabel = weekLabel,
                                onPreviousWeek = { viewModel?.goToPreviousWeek() },
                                onNextWeek = { viewModel?.goToNextWeek() },
                                onWeekLabelClick = {
                                    // Return to current week if not already there
                                    if (uiState.currentWeekOffset != 0) {
                                        viewModel?.loadLecturesForCurrentWeek()
                                    }
                                },
                                onLectureClick = { lecture ->
                                    selectedLecture = lecture
                                },
                                onRefresh = {
                                    viewModel?.refreshLectures()
                                },
                                isRefreshing = uiState.isRefreshing,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Desktop: Use regular layout with refresh button in navigation bar
                        WeeklyLecturesView(
                            lectures = uiState.lectures,
                            weekLabel = weekLabel,
                            onPreviousWeek = { viewModel?.goToPreviousWeek() },
                            onNextWeek = { viewModel?.goToNextWeek() },
                            onWeekLabelClick = {
                                // Return to current week if not already there
                                if (uiState.currentWeekOffset != 0) {
                                    viewModel?.loadLecturesForCurrentWeek()
                                }
                            },
                            onLectureClick = { lecture ->
                                selectedLecture = lecture
                            },
                            onRefresh = {
                                viewModel?.refreshLectures()
                            },
                            isRefreshing = uiState.isRefreshing,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Show lecture details dialog when a lecture is selected
            selectedLecture?.let { lecture ->
                LectureDetailsDialog(
                    lecture = lecture,
                    onDismiss = { selectedLecture = null }
                )
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
}

/**
 * Format WeekLabelData into a localized string.
 * Examples: "04 - 08 Nov" or "28 Nov - 02 Dec"
 */
@Composable
private fun formatWeekLabel(data: WeekLabelData): String {
    val mondayMonthStr = stringResource(getMonthResource(data.mondayMonth))
    val fridayMonthStr = stringResource(getMonthResource(data.fridayMonth))

    return if (data.mondayMonth == data.fridayMonth) {
        // Same month: "04 - 08 Nov"
        "${data.mondayDay.toString().padStart(2, '0')} - ${data.fridayDay.toString().padStart(2, '0')} $mondayMonthStr"
    } else {
        // Different months: "28 Nov - 02 Dec"
        "${data.mondayDay.toString().padStart(2, '0')} $mondayMonthStr - ${data.fridayDay.toString().padStart(2, '0')} $fridayMonthStr"
    }
}

/**
 * Map Month enum to string resource.
 */
private fun getMonthResource(month: Month) = when (month) {
    Month.JANUARY -> Res.string.january_short
    Month.FEBRUARY -> Res.string.february_short
    Month.MARCH -> Res.string.march_short
    Month.APRIL -> Res.string.april_short
    Month.MAY -> Res.string.may_short
    Month.JUNE -> Res.string.june_short
    Month.JULY -> Res.string.july_short
    Month.AUGUST -> Res.string.august_short
    Month.SEPTEMBER -> Res.string.september_short
    Month.OCTOBER -> Res.string.october_short
    Month.NOVEMBER -> Res.string.november_short
    Month.DECEMBER -> Res.string.december_short
}

