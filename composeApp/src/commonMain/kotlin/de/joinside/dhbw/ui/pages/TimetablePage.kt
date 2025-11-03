package de.joinside.dhbw.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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

    // Future-odo: Fetch real timetable data and display it here from dualis
    val lecture = LectureModel(
        name = "Sample Lecture",
        color = MaterialTheme.colorScheme.primary,
        start = LocalDateTime(2024, 6, 10, 9, 0),
        end = LocalDateTime(2024, 6, 10, 10, 30),
        lecturer = "Dr. John Doe",
        location = "Room 101"
    )

    val lectures: List<LectureModel> = listOf(
        lecture,
        lecture.copy(
            name = "Advanced Topics",
            start = LocalDateTime(2024, 6, 10, 11, 0),
            end = LocalDateTime(2024, 6, 10, 12, 30)
        ),
        lecture.copy(
            name = "Practical Session",
            start = LocalDateTime(2024, 6, 10, 14, 0),
            end = LocalDateTime(2024, 6, 10, 15, 30)
        ),
        lecture.copy(
            name = "Exam Preparation",
            start = LocalDateTime(2024, 6, 12, 16, 0),
            end = LocalDateTime(2024, 6, 12, 17, 30)
        )
    )

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
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}