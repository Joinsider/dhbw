package de.fampopprol.dhbwhorb.ui.pages

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
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.settings_title
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavigationBar
import de.fampopprol.dhbwhorb.ui.settings.DesignSelectionCard
import de.fampopprol.dhbwhorb.ui.settings.HelpSelectionCard
import de.fampopprol.dhbwhorb.ui.settings.NotificationSettingsCard
import de.fampopprol.dhbwhorb.util.isMobilePlatform
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.fampopprol.dhbwhorb.presentation.settings.SettingsIntent
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.ui.store.collectState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun SettingsPage(
    onNavigate: (BottomNavItem) -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    store: SettingsStore = koinInject()
) {
    // Ten parameters used to be threaded down from the root composable, five of them callbacks
    // that only wrote a preference back. The screen reads its own state now.
    val settings by store.collectState()

    Scaffold(
        modifier = if (isMobilePlatform()) {
            modifier
                .statusBarsPadding()
        } else {
            modifier
        },
        bottomBar = {
            BottomNavigationBar(
                currentItem = BottomNavItem.SETTINGS,
                onItemSelected = onNavigate
            )
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("settingsPageTitle")
                        .padding(/*top = 16.dp,*/ bottom = 24.dp)
                )

                // Design Selection Card
                DesignSelectionCard(
                    currentThemeMode = settings.themeMode,
                    onThemeModeChange = { store.dispatch(SettingsIntent.ThemeModeChanged(it)) },
                    materialYouEnabled = settings.materialYouEnabled,
                    onMaterialYouChange = { store.dispatch(SettingsIntent.MaterialYouChanged(it)) },
                    currentSeedColor = Color(settings.seedColor.toInt()),
                    onSeedColorChange = {
                        store.dispatch(SettingsIntent.SeedColorChanged(it.toArgb().toLong()))
                    }
                )

                // Notification Settings Card
                NotificationSettingsCard(
                    notificationsEnabled = settings.notificationsEnabled,
                    onNotificationsEnabledChange = {
                        store.dispatch(SettingsIntent.NotificationsChanged(it))
                    },
                    lectureAlertsEnabled = settings.lectureAlertsEnabled,
                    onLectureAlertsEnabledChange = {
                        store.dispatch(SettingsIntent.LectureAlertsChanged(it))
                    },
                    reminderLeadMinutes = settings.reminderLeadMinutes,
                    onReminderLeadChange = {
                        store.dispatch(SettingsIntent.ReminderLeadChanged(it))
                    }
                )

                HelpSelectionCard(onLogout = onLogout, showLogout = true)
            }
        }
    }
}
