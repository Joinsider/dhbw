package de.joinside.dhbw.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.data.storage.preferences.ThemeMode
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.settings_title
import de.joinside.dhbw.ui.navigation.BottomNavItem
import de.joinside.dhbw.ui.navigation.BottomNavigationBar
import de.joinside.dhbw.ui.settings.DesignSelectionCard
import de.joinside.dhbw.ui.settings.HelpSelectionCard
import de.joinside.dhbw.util.isMobilePlatform
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun SettingsPage(
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToGrades: () -> Unit = {},
    onLogout: () -> Unit = {},
    isLoggedIn: Boolean = true,
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    materialYouEnabled: Boolean = true,
    onMaterialYouChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {

    Scaffold(
        modifier = if (isMobilePlatform()) {
            modifier
                .statusBarsPadding()
        } else {
            modifier
        },
        bottomBar = {
            if (isLoggedIn) {
                BottomNavigationBar(
                    currentItem = BottomNavItem.SETTINGS,
                    onItemSelected = { item ->
                        when (item) {
                            BottomNavItem.TIMETABLE -> onNavigateToTimetable()
                            BottomNavItem.GRADES -> onNavigateToGrades()
                            BottomNavItem.SETTINGS -> { /* Already here */ }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("settingsPageTitle")
                        .padding(top = 16.dp, bottom = 24.dp)
                )

                // Design Selection Card
                DesignSelectionCard(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    materialYouEnabled = materialYouEnabled,
                    onMaterialYouChange = onMaterialYouChange
                )

                HelpSelectionCard(onLogout)
            }
        }
    }
}
