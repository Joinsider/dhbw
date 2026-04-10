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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import java.io.File

private const val TAG = "Main"

private fun configureDesktopTrustStore() {
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("mac")) {
        // On macOS, KeychainStore is often better as it uses system certificates.
        // However, we only set it if not already configured.
        if (System.getProperty("javax.net.ssl.trustStoreType").isNullOrBlank()) {
            System.setProperty("javax.net.ssl.trustStoreType", "KeychainStore")
            Napier.d("Using macOS KeychainStore for SSL trust", tag = TAG)
            return
        }
    }

    val configuredTrustStore = System.getProperty("javax.net.ssl.trustStore")
    if (!configuredTrustStore.isNullOrBlank()) {
        Napier.d("Using preconfigured trustStore: $configuredTrustStore", tag = TAG)
        return
    }

    val javaHome = System.getProperty("java.home")
    val candidates = listOf(
        "$javaHome/lib/security/cacerts",
        "$javaHome/jre/lib/security/cacerts"
    )

    val detectedTrustStore = candidates.firstOrNull { File(it).exists() }
    if (detectedTrustStore != null) {
        System.setProperty("javax.net.ssl.trustStore", detectedTrustStore)
        if (System.getProperty("javax.net.ssl.trustStoreType").isNullOrBlank()) {
            System.setProperty("javax.net.ssl.trustStoreType", "JKS")
        }
        Napier.d("Configured trustStore: $detectedTrustStore", tag = TAG)
    } else {
        Napier.w("No cacerts file found under java.home=$javaHome", tag = TAG)
    }
}

fun main() {
    // Initialize Napier for JVM logging
    Napier.base(DebugAntilog())
    Napier.d("JVM Desktop application starting", tag = TAG)
    configureDesktopTrustStore()

    // Create coroutine scope for background operations
    val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Initialize services
    Napier.d("Initializing services...", tag = TAG)

    // Create database
    val database = createRoomDatabase(
        getDatabaseBuilder()
    )
    Napier.d("Database initialized", tag = TAG)

    // Create shared HttpClient for cookie sharing
    val sharedHttpClient = AuthenticationService.createSharedHttpClient()
    Napier.d("Shared HttpClient created using AuthenticationService.createSharedHttpClient()", tag = TAG)

    // Create session manager
    val secureStorage = SecureStorage()
    val secureStorageWrapper = SecureStorageWrapper(secureStorage)
    val sessionManager = SessionManager(secureStorageWrapper)

    // Create authentication service
    val authenticationService = AuthenticationService(
        sessionManager = sessionManager,
        client = sharedHttpClient
    )
    Napier.d("AuthenticationService initialized", tag = TAG)

    // Create API client
    val dualisApiClient = DualisApiClient(client = sharedHttpClient)

    // Create Dualis lecture service
    val dualisLectureService = DualisLectureService(
        apiClient = dualisApiClient,
        sessionManager = sessionManager,
        authenticationService = authenticationService,
        lectureEventDao = database.lectureDao(),
        lecturerDao = database.lecturerDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
    )
    Napier.d("DualisLectureService initialized", tag = TAG)

    // Create lecture service
    val lectureService = LectureService(
        database = database,
        dualisLectureServiceFactory = { dualisLectureService }
    )
    Napier.d("LectureService initialized", tag = TAG)

    // Create timetable ViewModel
    val timetableViewModel = TimetableViewModel(
        lectureService = lectureService,
        lecturerDao = database.lecturerDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
    )
    Napier.d("TimetableViewModel initialized", tag = TAG)

    // Initialize notification system
    val notificationPreferences = NotificationPreferences(secureStorageWrapper)
    val notificationPreferencesInteractor = NotificationPreferencesInteractor(notificationPreferences)
    Napier.d("NotificationPreferencesInteractor initialized", tag = TAG)

    val lectureChangeMonitor = LectureChangeMonitor(
        dualisLectureServiceFactory = { dualisLectureService },
        lectureEventDao = database.lectureDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
    )
    Napier.d("LectureChangeMonitor initialized", tag = TAG)

    val notificationDispatcher = NotificationDispatcher()
    val notificationManager = NotificationManager(
        monitor = lectureChangeMonitor,
        dispatcher = notificationDispatcher,
        preferences = notificationPreferencesInteractor
    )
    NotificationServiceLocator.initialize(notificationManager)
    Napier.d("NotificationManager initialized and registered", tag = TAG)

    // Initialize scheduler
    val lectureMonitorScheduler = LectureMonitorScheduler(appScope)
    Napier.d("LectureMonitorScheduler initialized", tag = TAG)

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

            Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)
            Napier.d("🖥️  PREFERENCE CHANGE DETECTED (Desktop)", tag = TAG)
            Napier.d("   Master notifications toggle: $notificationsEnabled", tag = TAG)
            Napier.d("   Lecture alerts toggle: $lectureAlertsEnabled", tag = TAG)
            Napier.d("   → Should schedule: $shouldSchedule", tag = TAG)
            Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = TAG)

            if (shouldSchedule) {
                Napier.d("✅ Both toggles enabled → Starting lecture monitoring scheduler...", tag = TAG)
                lectureMonitorScheduler.schedule()
            } else {
                Napier.d("🛑 One or both toggles disabled → Stopping lecture monitoring scheduler...", tag = TAG)
                lectureMonitorScheduler.cancel()
            }
        }
    }

    Napier.i("All services initialized successfully!", tag = TAG)

    application {
        Napier.d("Creating main window", tag = TAG)
        Window(
            onCloseRequest = {
                Napier.d("Application closing", tag = TAG)
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