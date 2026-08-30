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
import androidx.compose.ui.platform.testTag
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
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableIntent
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableStore
import de.fampopprol.dhbwhorb.ui.error.toUserMessage
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.store.collectState
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavigationBar
import de.fampopprol.dhbwhorb.ui.schedule.modules.dialogs.LectureDetailsDialog
import de.fampopprol.dhbwhorb.ui.schedule.modules.week.WeekNavigationBar
import de.fampopprol.dhbwhorb.ui.schedule.views.WeeklyLecturesView
import de.fampopprol.dhbwhorb.util.isMobilePlatform
import org.koin.compose.koinInject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, InternalResourceApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun TimetablePage(
    /** Week offset from a deep link; null opens the current week. */
    initialWeek: Int? = null,
    onNavigate: (BottomNavItem) -> Unit = {},
    modifier: Modifier = Modifier,
    store: TimetableStore = koinInject()
) {
    val uiState by store.collectState()
    val hapticFeedback = LocalHapticFeedback.current

    // The pager fakes an infinite range around the current week.
    val initialPage = 1000
    val pagerState = rememberPagerState(
        initialPage = initialPage + (initialWeek ?: 0),
        pageCount = { 2001 }
    )

    // The pager owns the scroll position; the store owns everything that follows from it.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                store.dispatch(TimetableIntent.WeekFocused(page - initialPage))
            }
    }

    Scaffold(
        modifier = if (isMobilePlatform()) modifier.statusBarsPadding() else modifier,
        bottomBar = {
            BottomNavigationBar(
                currentItem = BottomNavItem.TIMETABLE,
                onItemSelected = onNavigate
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Shared Navigation Bar for all pages
            WeekLabelDisplay(
                start = uiState.currentWeek.start,
                end = uiState.currentWeek.end,
                isRefreshing = uiState.currentWeek.isRefreshing,
                onRefresh = {
                    store.dispatch(TimetableIntent.Refresh(pagerState.currentPage - initialPage))
                },
                initialPage = initialPage,
                pagerState = pagerState
            )

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page ->
                    val offset = page - initialPage
                    val week = uiState.week(offset)

                    PullToRefreshBox(
                        isRefreshing = week.isRefreshing,
                        onRefresh = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            store.dispatch(TimetableIntent.Refresh(offset))
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        WeeklyLecturesView(
                            lectures = week.lectures,
                            onLectureClick = { store.dispatch(TimetableIntent.LectureOpened(it)) },
                            isRefreshing = week.isLoading || week.isRefreshing,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Error banner (non-blocking): the week may still show cached lectures.
                uiState.currentWeek.error?.let { error ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Text(
                            text = error.toUserMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp).testTag("timetableErrorBanner")
                        )
                    }
                }
            }
        }

        // Selected lecture dialog
        uiState.selectedLecture?.let { lecture ->
            LectureDetailsDialog(
                lecture = lecture,
                onDismiss = { store.dispatch(TimetableIntent.LectureDismissed) }
            )
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
private fun WeekLabelDisplay(
    start: LocalDateTime?,
    end: LocalDateTime?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    initialPage: Int,
    pagerState: androidx.compose.foundation.pager.PagerState
) {
    // The dates come from the store, which got them from the same calendar arithmetic the
    // repository uses. The label is a pure function of them.
    val weekLabel = if (start != null && end != null) {
        formatWeekLabel(start)
    } else {
        stringResource(Res.string.this_week)
    }
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
        onRefresh = onRefresh,
        isRefreshing = isRefreshing,
        modifier = Modifier.padding(8.dp)
    )
}

/** "17 - 21 Aug", or "29 Dec - 02 Jan" when the week straddles a month. */
@Composable
private fun formatWeekLabel(start: LocalDateTime): String {
    // The bar shows the working week, so it ends on Friday rather than on the range's Sunday.
    val friday = start.date.plus(4, DateTimeUnit.DAY)
    val startMonth = stringResource(getMonthResource(start.month))
    val endMonth = stringResource(getMonthResource(friday.month))

    val startDay = start.day.toString().padStart(2, '0')
    val endDay = friday.day.toString().padStart(2, '0')

    return if (start.month == friday.month) {
        "$startDay - $endDay $startMonth"
    } else {
        "$startDay $startMonth - $endDay $endMonth"
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
