package de.joinside.dhbw.ui.navigation

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
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.nav_grades
import de.joinside.dhbw.resources.nav_settings
import de.joinside.dhbw.resources.nav_timetable
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
    NavigationBar(
        modifier = modifier
    ) {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                selected = currentItem == item,
                onClick = { onItemSelected(item) },
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

