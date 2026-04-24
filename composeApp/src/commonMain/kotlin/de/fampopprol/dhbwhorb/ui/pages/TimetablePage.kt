package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.april_short
import de.fampopprol.dhbwhorb.resources.august_short
import de.fampopprol.dhbwhorb.resources.december_short
import de.fampopprol.dhbwhorb.resources.error_loading_lectures
import de.fampopprol.dhbwhorb.resources.february_short
import de.fampopprol.dhbwhorb.resources.january_short
import de.fampopprol.dhbwhorb.resources.july_short
import de.fampopprol.dhbwhorb.resources.june_short
import de.fampopprol.dhbwhorb.resources.march_short
import de.fampopprol.dhbwhorb.resources.may_short
import de.fampopprol.dhbwhorb.resources.november_short
import de.fampopprol.dhbwhorb.resources.october_short
import de.fampopprol.dhbwhorb.resources.september_short
import de.fampopprol.dhbwhorb.resources.this_week
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavigationBar
import de.fampopprol.dhbwhorb.ui.schedule.modules.dialogs.LectureDetailsDialog
import de.fampopprol.dhbwhorb.ui.schedule.models.LectureModel
import de.fampopprol.dhbwhorb.ui.schedule.modules.week.WeekNavigationBar
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.WeekLabelData
import de.fampopprol.dhbwhorb.ui.schedule.views.WeeklyLecturesView
import de.fampopprol.dhbwhorb.util.isMobilePlatform
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.stringResource
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, InternalResourceApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun TimetablePage(
    viewModel: TimetableViewModel,
    database: AppDatabase? = null,
    authenticationService: AuthenticationService? = null,
    sharedHttpClient: HttpClient? = null,
    sessionManager: SessionManager? = null,
    onNavigateToGrades: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isLoggedIn: Boolean = true,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState
    var selectedLecture by remember { mutableStateOf<LectureModel?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    // Set up pager with a very large number of pages
    val initialPage = 1000
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2001 })

    // Sync pager state with ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val offset = page - initialPage
                viewModel.loadLecturesForWeek(offset)
            }
    }

    Scaffold(
        modifier = if (isMobilePlatform()) modifier.statusBarsPadding() else modifier,
        bottomBar = {
            if (isLoggedIn) {
                BottomNavigationBar(
                    currentItem = BottomNavItem.TIMETABLE,
                    onItemSelected = { item ->
                        when (item) {
                            BottomNavItem.TIMETABLE -> { /* Already here */ }
                            BottomNavItem.GRADES -> onNavigateToGrades()
                            BottomNavItem.DOCUMENTS -> onNavigateToDocuments()
                            BottomNavItem.SETTINGS -> onNavigateToSettings()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Shared Navigation Bar for all pages
            WeekLabelDisplay(uiState.weekLabelData, viewModel, initialPage, pagerState)

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page ->
                    val offset = page - initialPage
                    val isRefreshing = viewModel.isWeekRefreshing(offset)
                    val isLoading = viewModel.isWeekLoading(offset)
                    val lectures = viewModel.getLecturesForWeekSync(offset)

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            viewModel.refreshLectures(offset)
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        WeeklyLecturesView(
                            lectures = lectures,
                            onLectureClick = { selectedLecture = it },
                            isRefreshing = isLoading || isRefreshing,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Error banner (non-blocking)
                if (uiState.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Text(
                            text = stringResource(Res.string.error_loading_lectures),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        // Selected lecture dialog
        selectedLecture?.let { lecture ->
            LectureDetailsDialog(lecture = lecture, onDismiss = { selectedLecture = null })
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
private fun WeekLabelDisplay(
    weekLabelData: WeekLabelData?,
    viewModel: TimetableViewModel,
    initialPage: Int,
    pagerState: androidx.compose.foundation.pager.PagerState
) {
    val weekLabel = weekLabelData?.let { formatWeekLabel(it) } ?: stringResource(Res.string.this_week)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    WeekNavigationBar(
        weekLabel = weekLabel,
        onPreviousWeek = {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        },
        onNextWeek = {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        },
        onWeekLabelClick = {
            coroutineScope.launch {
                pagerState.animateScrollToPage(initialPage)
            }
        },
        onRefresh = {
            val offset = pagerState.currentPage - initialPage
            viewModel.refreshLectures(offset)
        },
        isRefreshing = viewModel.isWeekRefreshing(pagerState.currentPage - initialPage),
        modifier = Modifier.padding(8.dp)
    )
}

@Composable
private fun formatWeekLabel(data: WeekLabelData): String {
    val mondayMonthStr = stringResource(getMonthResource(data.mondayMonth))
    val fridayMonthStr = stringResource(getMonthResource(data.fridayMonth))
    return if (data.mondayMonth == data.fridayMonth) {
        "${data.mondayDay.toString().padStart(2, '0')} - ${data.fridayDay.toString().padStart(2, '0')} $mondayMonthStr"
    } else {
        "${data.mondayDay.toString().padStart(2, '0')} $mondayMonthStr - ${data.fridayDay.toString().padStart(2, '0')} $fridayMonthStr"
    }
}

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
