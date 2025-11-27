package de.joinside.dhbw

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.data.dualis.remote.DualisApiClient
import de.joinside.dhbw.data.dualis.remote.parser.GradeParser
import de.joinside.dhbw.data.dualis.remote.parser.HtmlParser
import de.joinside.dhbw.data.dualis.remote.services.DualisGradeService
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.storage.credentials.CredentialsStorageProvider
import de.joinside.dhbw.data.storage.credentials.SecureStorage
import de.joinside.dhbw.data.storage.credentials.SecureStorageWrapper
import de.joinside.dhbw.data.storage.database.AppDatabase
import de.joinside.dhbw.data.storage.preferences.ThemeMode
import de.joinside.dhbw.data.storage.preferences.ThemePreferences
import de.joinside.dhbw.data.storage.preferences.NotificationPreferences
import de.joinside.dhbw.data.storage.preferences.NotificationPreferencesInteractor
import de.joinside.dhbw.services.notifications.NotificationDispatcher
import de.joinside.dhbw.ui.pages.GradesPage
import de.joinside.dhbw.ui.pages.SettingsPage
import de.joinside.dhbw.ui.pages.Startpage
import de.joinside.dhbw.ui.pages.TimetablePage
import de.joinside.dhbw.ui.schedule.viewModels.TimetableViewModel
import de.joinside.dhbw.ui.grades.viewModels.GradesViewModel
import de.joinside.dhbw.ui.theme.DHBWHorbTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

enum class AppScreen {
    WELCOME,
    LOGIN,
    TIMETABLE,
    GRADES,
    SETTINGS
}

@Composable
@Preview
fun App(
    testAuthenticationService: AuthenticationService? = null,
    testCredentialsProvider: CredentialsStorageProvider? = null,
    timetableViewModel: TimetableViewModel? = null,
    database: AppDatabase? = null,
    notificationPreferencesInteractor: NotificationPreferencesInteractor? = null
) {
    // Ensure Napier is initialized (fallback in case platform didn't initialize it)
    LaunchedEffect(Unit) {
        try {
            // Test if Napier is initialized by attempting to log
            Napier.d("App() composable started", tag = "App")
        } catch (_: Exception) {
            // If not initialized, initialize it now
            Napier.base(DebugAntilog())
            Napier.d("Napier initialized from App() composable", tag = "App")
        }
    }

    // Initialize SecureStorage, SessionManager, and Services
    // Use test dependencies if provided, otherwise create real ones
    val secureStorage = remember { SecureStorage() }
    val secureStorageWrapper = remember { SecureStorageWrapper(secureStorage) }
    val sessionManager = remember { SessionManager(secureStorageWrapper) }

    // Initialize theme preferences
    val themePreferences = remember { ThemePreferences(secureStorage) }
    var themeMode by remember { mutableStateOf(themePreferences.getThemeMode()) }
    var materialYouEnabled by remember { mutableStateOf(themePreferences.getMaterialYouEnabled()) }
    // Default to Purple40 (0xFF6650a4) which is 4284932260L
    var seedColorLong by remember { mutableStateOf(themePreferences.getCustomColor()) }
    val seedColor = remember(seedColorLong) { Color(seedColorLong.toInt()) }

    // Initialize notification preferences
    // Use passed parameter if provided (from MainActivity), otherwise create new one (for preview)
    val notificationPreferences = remember { NotificationPreferences(secureStorageWrapper) }
    val actualNotificationPreferencesInteractor = notificationPreferencesInteractor
        ?: remember { NotificationPreferencesInteractor(notificationPreferences) }

    // Observe notification preferences
    val notificationsEnabled by actualNotificationPreferencesInteractor.notificationsEnabled.collectAsState()
    val lectureAlertsEnabled by actualNotificationPreferencesInteractor.lectureAlertsEnabled.collectAsState()

    // Notification dispatcher (used later by schedulers/monitors)
    val notificationDispatcher = remember { NotificationDispatcher() }

    // Create shared HttpClient for all Dualis services (IMPORTANT for cookie sharing!)
    val sharedHttpClient = remember {
        HttpClient {
            expectSuccess = false
            install(HttpCookies)
            install(HttpTimeout) {
                socketTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                requestTimeoutMillis = 30000
            }
        }
    }

    // Initialize services with shared HttpClient
    val authenticationService = testAuthenticationService ?: remember {
        AuthenticationService(
            sessionManager = sessionManager,
            client = sharedHttpClient
        )
    }

    // Initialize GradesViewModel locally for now (TODO: Move to platform entry points like TimetableViewModel)
    // We need to reuse the same HttpClient (via authenticationService) or pass it to apiClient
    val gradesViewModel = remember(database, authenticationService, sharedHttpClient) {
        if (database != null) {
            val apiClient = DualisApiClient(sharedHttpClient)
            val gradeParser = GradeParser()
            val htmlParser = HtmlParser()
            val gradeService = DualisGradeService(
                apiClient = apiClient,
                sessionManager = sessionManager,
                authenticationService = authenticationService,
                gradeParser = gradeParser,
                htmlParser = htmlParser,
                gradeDao = database.gradeDao()
            )
            GradesViewModel(gradeService, database.gradeDao())
        } else {
            null
        }
    }

    // Keep CredentialsProvider for backward compatibility with existing UI
    val credentialsProvider =
        testCredentialsProvider ?: remember { CredentialsStorageProvider(secureStorageWrapper) }

    // Navigation state
    var currentScreen by remember { mutableStateOf(AppScreen.WELCOME) }
    var isLoggedIn by remember { mutableStateOf(false) }

    // Session check on startup
    LaunchedEffect(Unit) {
        isLoggedIn = authenticationService.isAuthenticated()
        if (isLoggedIn) {
            currentScreen = AppScreen.TIMETABLE
        }
    }

    // Logout handler
    val handleLogout: () -> Unit = {
        Napier.d("Logout initiated", tag = "App")

        // Clear session data
        sessionManager.logout()

        // Clear credentials
        credentialsProvider.clearCredentials()

        // Clear database if available
        database?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Napier.d("Clearing database...", tag = "App")
                    db.clearAllData()
                    Napier.d("Database cleared successfully", tag = "App")
                } catch (e: Exception) {
                    Napier.e("Error clearing database: ${e.message}", e, tag = "App")
                }
            }
        }

        // Update UI state
        isLoggedIn = false
        currentScreen = AppScreen.WELCOME

        Napier.d("Logout completed", tag = "App")
    }

    DHBWHorbTheme(
        darkTheme = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        },
        useMaterialYou = materialYouEnabled,
        seedColor = seedColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("appContainer")
        ) {
            when (currentScreen) {
                AppScreen.WELCOME -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeContentPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Startpage(
                            onLoginSuccess = {
                                isLoggedIn = true
                                currentScreen = AppScreen.TIMETABLE
                            },
                            authenticationService = authenticationService,
                            credentialsProvider = credentialsProvider,
                        )
                    }
                }

                AppScreen.LOGIN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeContentPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Startpage(
                            onLoginSuccess = {
                                isLoggedIn = true
                                currentScreen = AppScreen.TIMETABLE
                            },
                            authenticationService = authenticationService,
                            credentialsProvider = credentialsProvider,
                        )
                    }
                }

                AppScreen.TIMETABLE -> {
                    TimetablePage(
                        viewModel = timetableViewModel,
                        onNavigateToGrades = {
                            currentScreen = AppScreen.GRADES
                        },
                        onNavigateToSettings = {
                            currentScreen = AppScreen.SETTINGS
                        },
                        isLoggedIn = isLoggedIn,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp)
                    )
                }

                AppScreen.GRADES -> {
                    GradesPage(
                        viewModel = gradesViewModel,
                        onNavigateToTimetable = {
                            currentScreen = AppScreen.TIMETABLE
                        },
                        onNavigateToSettings = {
                            currentScreen = AppScreen.SETTINGS
                        },
                        isLoggedIn = isLoggedIn,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp)
                    )
                }

                AppScreen.SETTINGS -> {
                    SettingsPage(
                        onNavigateToTimetable = {
                            currentScreen = AppScreen.TIMETABLE
                        },
                        onNavigateToGrades = {
                            currentScreen = AppScreen.GRADES
                        },
                        onLogout = handleLogout,
                        isLoggedIn = isLoggedIn,
                        currentThemeMode = themeMode,
                        onThemeModeChange = { newMode ->
                            themeMode = newMode
                            themePreferences.setThemeMode(newMode)
                        },
                        materialYouEnabled = materialYouEnabled,
                        onMaterialYouChange = { enabled ->
                            materialYouEnabled = enabled
                            themePreferences.setMaterialYouEnabled(enabled)
                        },
                        currentSeedColor = seedColor,
                        onSeedColorChange = { newColor ->
                            // Store as ARGB Long (UInt)
                            val colorLong = newColor.toArgb().toLong()
                            seedColorLong = colorLong
                            themePreferences.setCustomColor(colorLong)
                        },
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsEnabledChange = { enabled ->
                            // This immediately updates StateFlow, triggering collectors in MainActivity/main.kt
                            actualNotificationPreferencesInteractor.setNotificationsEnabled(enabled)
                        },
                        lectureAlertsEnabled = lectureAlertsEnabled,
                        onLectureAlertsEnabledChange = { enabled ->
                            // This immediately updates StateFlow, triggering collectors in MainActivity/main.kt
                            actualNotificationPreferencesInteractor.setLectureAlertsEnabled(enabled)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp)
                    )
                }
            }
        }
    }
}