package de.fampopprol.dhbwhorb.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.nav_grades
import de.fampopprol.dhbwhorb.resources.nav_settings
import de.fampopprol.dhbwhorb.resources.nav_timetable
import org.jetbrains.compose.resources.stringResource

enum class BottomNavItem(
    val icon: ImageVector
) {
    TIMETABLE(Icons.Default.DateRange),
    GRADES(Icons.Default.Star),
    SETTINGS(Icons.Default.Settings)
}

@Composable
fun getNavText(item: BottomNavItem): String {
    return when (item) {
        BottomNavItem.TIMETABLE -> stringResource(Res.string.nav_timetable)
        BottomNavItem.GRADES -> stringResource(Res.string.nav_grades)
        BottomNavItem.SETTINGS -> stringResource(Res.string.nav_settings)
    }
}

@Composable
fun BottomNavigationBar(
    currentItem: BottomNavItem,
    onItemSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    NavigationBar(
        modifier = modifier
    ) {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                selected = currentItem == item,
                onClick = {
                    onItemSelected(item)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = getNavText(item)
                    )
                },
                label = {
                    Text(text = getNavText(item))
                }
            )
        }
    }
}

