package de.joinside.dhbw.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.joinside.dhbw.ui.navigation.BottomNavItem
import de.joinside.dhbw.ui.navigation.BottomNavigationBar
import de.joinside.dhbw.ui.schedule.modules.LectureModel
import de.joinside.dhbw.ui.schedule.views.WeeklyLecturesView
import de.joinside.dhbw.util.isMobilePlatform
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun TimetablePage(
    onNavigateToResult: () -> Unit = {},
    onNavigateToGrades: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isLoggedIn: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Track current week number
    var currentWeek by remember { mutableStateOf(45) } // Start with week 45 (early November 2025)

    // Future-todo: Fetch real timetable data and display it here from dualis
    // Week 1 lectures (even weeks)
    val week1Lectures = listOf(
        LectureModel(
            name = "Software Engineering",
            color = MaterialTheme.colorScheme.primary,
            start = LocalDateTime(2024, 6, 10, 9, 0),
            end = LocalDateTime(2024, 6, 10, 10, 30),
            lecturer = "Dr. John Doe",
            location = "Room 101"
        ),
        LectureModel(
            name = "Database Systems",
            color = MaterialTheme.colorScheme.secondary,
            start = LocalDateTime(2024, 6, 10, 11, 0),
            end = LocalDateTime(2024, 6, 10, 12, 30),
            lecturer = "Prof. Smith",
            location = "Room 202"
        ),
        LectureModel(
            name = "Web Development",
            color = MaterialTheme.colorScheme.tertiary,
            start = LocalDateTime(2024, 6, 10, 14, 0),
            end = LocalDateTime(2024, 6, 10, 15, 30),
            lecturer = "Dr. Brown",
            location = "Lab 3"
        ),
        LectureModel(
            name = "Algorithms",
            color = MaterialTheme.colorScheme.primary,
            start = LocalDateTime(2024, 6, 12, 16, 0),
            end = LocalDateTime(2024, 6, 12, 17, 30),
            lecturer = "Prof. Johnson",
            location = "Room 305"
        )
    )

    // Week 2 lectures (odd weeks)
    val week2Lectures = listOf(
        LectureModel(
            name = "Machine Learning",
            color = MaterialTheme.colorScheme.error,
            start = LocalDateTime(2024, 6, 10, 8, 0),
            end = LocalDateTime(2024, 6, 10, 9, 30),
            lecturer = "Dr. Williams",
            location = "Lab 1"
        ),
        LectureModel(
            name = "Computer Networks",
            color = MaterialTheme.colorScheme.tertiary,
            start = LocalDateTime(2024, 6, 10, 10, 0),
            end = LocalDateTime(2024, 6, 10, 11, 30),
            lecturer = "Prof. Davis",
            location = "Room 404"
        ),
        LectureModel(
            name = "Mobile Development",
            color = MaterialTheme.colorScheme.secondary,
            start = LocalDateTime(2024, 6, 11, 13, 0),
            end = LocalDateTime(2024, 6, 11, 14, 30),
            lecturer = "Dr. Martinez",
            location = "Lab 2"
        ),
        LectureModel(
            name = "Project Management",
            color = MaterialTheme.colorScheme.primary,
            start = LocalDateTime(2024, 6, 11, 15, 0),
            end = LocalDateTime(2024, 6, 11, 16, 30),
            lecturer = "Prof. Garcia",
            location = "Room 501"
        ),
        LectureModel(
            name = "Security",
            color = MaterialTheme.colorScheme.error,
            start = LocalDateTime(2024, 6, 13, 9, 0),
            end = LocalDateTime(2024, 6, 13, 10, 30),
            lecturer = "Dr. Lee",
            location = "Room 203"
        )
    )

    // Alternate between lecture sets based on week number
    val lectures = if (currentWeek % 2 == 0) week1Lectures else week2Lectures

    // Generate week label (format: "Week XX")
    val weekLabel = "Week $currentWeek"

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
            WeeklyLecturesView(
                lectures = lectures,
                weekLabel = weekLabel,
                onPreviousWeek = { currentWeek-- },
                onNextWeek = { currentWeek++ },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}