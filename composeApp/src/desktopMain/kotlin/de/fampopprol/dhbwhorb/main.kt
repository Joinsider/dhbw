package de.fampopprol.dhbwhorb

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.TimetableParser
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorage
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageWrapper
import de.fampopprol.dhbwhorb.data.storage.database.createRoomDatabase
import de.fampopprol.dhbwhorb.data.storage.database.getDatabaseBuilder
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.services.LectureService
import de.fampopprol.dhbwhorb.services.notifications.LectureChangeMonitor
import de.fampopprol.dhbwhorb.services.notifications.LectureMonitorScheduler
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.services.notifications.NotificationManager
import de.fampopprol.dhbwhorb.services.notifications.NotificationServiceLocator
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine

fun main() {
    // Initialize Napier for JVM logging
    Napier.base(DebugAntilog())
    Napier.d("JVM Desktop application starting", tag = "Main")

    // Create coroutine scope for background operations
    val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Initialize services
    Napier.d("Initializing services...", tag = "Main")

    // Create database
    val database = createRoomDatabase(
        getDatabaseBuilder()
    )
    Napier.d("Database initialized", tag = "Main")

    // Create shared HttpClient for cookie sharing
    val sharedHttpClient = HttpClient {
        expectSuccess = false
        install(HttpCookies)
    }
    Napier.d("Shared HttpClient created", tag = "Main")

    // Create session manager
    val secureStorage = SecureStorage()
    val secureStorageWrapper = SecureStorageWrapper(secureStorage)
    val sessionManager = SessionManager(secureStorageWrapper)

    // Create authentication service
    val authenticationService = AuthenticationService(
        sessionManager = sessionManager,
        client = sharedHttpClient
    )
    Napier.d("AuthenticationService initialized", tag = "Main")

    // Create API client
    val dualisApiClient = DualisApiClient(client = sharedHttpClient)

    // Create parsers
    val htmlParser = HtmlParser()
    val timetableParser = TimetableParser()

    // Create Dualis lecture service
    val dualisLectureService = DualisLectureService(
        apiClient = dualisApiClient,
        sessionManager = sessionManager,
        authenticationService = authenticationService,
        timetableParser = timetableParser,
        htmlParser = htmlParser,
        lectureEventDao = database.lectureDao(),
        lecturerDao = database.lecturerDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
    )
    Napier.d("DualisLectureService initialized", tag = "Main")

    // Create lecture service
    val lectureService = LectureService(
        database = database,
        dualisLectureService = dualisLectureService
    )
    Napier.d("LectureService initialized", tag = "Main")

    // Create timetable ViewModel
    val timetableViewModel = TimetableViewModel(
        lectureService = lectureService,
        lecturerDao = database.lecturerDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
    )
    Napier.d("TimetableViewModel initialized", tag = "Main")

    // Initialize notification system
    val notificationPreferences = NotificationPreferences(secureStorageWrapper)
    val notificationPreferencesInteractor = NotificationPreferencesInteractor(notificationPreferences)
    Napier.d("NotificationPreferencesInteractor initialized", tag = "Main")

    val lectureChangeMonitor = LectureChangeMonitor(
        dualisLectureService = dualisLectureService,
        lectureEventDao = database.lectureDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
    )
    Napier.d("LectureChangeMonitor initialized", tag = "Main")

    val notificationDispatcher = NotificationDispatcher()
    val notificationManager = NotificationManager(
        monitor = lectureChangeMonitor,
        dispatcher = notificationDispatcher,
        preferences = notificationPreferencesInteractor
    )
    NotificationServiceLocator.initialize(notificationManager)
    Napier.d("NotificationManager initialized and registered", tag = "Main")

    // Initialize scheduler
    val lectureMonitorScheduler = LectureMonitorScheduler(appScope)
    Napier.d("LectureMonitorScheduler initialized", tag = "Main")

    // Observe BOTH preferences to start/stop scheduler
    // Combine both flows so scheduler reacts to changes in either toggle
    appScope.launch {
        combine(
            notificationPreferencesInteractor.notificationsEnabled,
            notificationPreferencesInteractor.lectureAlertsEnabled
        ) { notificationsEnabled, lectureAlertsEnabled ->
            Pair(notificationsEnabled, lectureAlertsEnabled)
        }.collect { (notificationsEnabled, lectureAlertsEnabled) ->
            val shouldSchedule = notificationsEnabled && lectureAlertsEnabled

            Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = "Main")
            Napier.d("🖥️  PREFERENCE CHANGE DETECTED (Desktop)", tag = "Main")
            Napier.d("   Master notifications toggle: $notificationsEnabled", tag = "Main")
            Napier.d("   Lecture alerts toggle: $lectureAlertsEnabled", tag = "Main")
            Napier.d("   → Should schedule: $shouldSchedule", tag = "Main")
            Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = "Main")

            if (shouldSchedule) {
                Napier.d("✅ Both toggles enabled → Starting lecture monitoring scheduler...", tag = "Main")
                lectureMonitorScheduler.schedule()
            } else {
                Napier.d("🛑 One or both toggles disabled → Stopping lecture monitoring scheduler...", tag = "Main")
                lectureMonitorScheduler.cancel()
            }
        }
    }

    Napier.i("All services initialized successfully!", tag = "Main")

    application {
        Napier.d("Creating main window", tag = "Main")
        Window(
            onCloseRequest = {
                Napier.d("Application closing", tag = "Main")
                lectureMonitorScheduler.cancel()
                appScope.cancel()
                exitApplication()
            },
            title = "dhbw",
        ) {
            App(
                testAuthenticationService = authenticationService,
                timetableViewModel = timetableViewModel,
                database = database,
                notificationPreferencesInteractor = notificationPreferencesInteractor
            )
        }
    }
}