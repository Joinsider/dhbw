/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.services.notifications

/**
 * Example integration of the notification system.
 *
 * This file shows how to initialize and use the notification system
 * in the main application. Actual integration should be done in
 * MainActivity (Android) or equivalent platform entry points.
 */

// Example Android initialization in MainActivity:
/*
class MainActivity : ComponentActivity() {
    private lateinit var notificationManager: NotificationManager
    private lateinit var lectureMonitorScheduler: LectureMonitorScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies
        val secureStorage = SecureStorage()
        val secureStorageWrapper = SecureStorageWrapper(secureStorage)
        val notificationPreferences = NotificationPreferences(secureStorageWrapper)
        val notificationPreferencesInteractor = NotificationPreferencesInteractor(notificationPreferences)

        val database = AppDatabase.getDatabase(this)
        val sessionManager = SessionManager(secureStorageWrapper)
        val httpClient = HttpClient {
            expectSuccess = false
            install(HttpCookies)
        }

        val dualisApiClient = DualisApiClient(httpClient)
        val htmlParser = HtmlParser()
        val timetableParser = TimetableParser()
        val authService = AuthenticationService(sessionManager, httpClient)

        val dualisLectureService = DualisLectureService(
            apiClient = dualisApiClient,
            sessionManager = sessionManager,
            authenticationService = authService,
            timetableParser = timetableParser,
            htmlParser = htmlParser,
            lectureEventDao = database.lectureEventDao(),
            lecturerDao = database.lecturerDao(),
            lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
        )

        val lectureChangeMonitor = LectureChangeMonitor(
            dualisLectureService = dualisLectureService,
            lectureEventDao = database.lectureEventDao(),
            lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
        )

        val notificationDispatcher = NotificationDispatcher(this)

        notificationManager = NotificationManager(
            monitor = lectureChangeMonitor,
            dispatcher = notificationDispatcher,
            preferences = notificationPreferencesInteractor
        )

        // Schedule periodic checks
        lectureMonitorScheduler = LectureMonitorScheduler(this)

        // Observe preferences to start/stop scheduler
        lifecycleScope.launch {
            notificationPreferencesInteractor.notificationsEnabled.collect { enabled ->
                if (enabled) {
                    lectureMonitorScheduler.schedule()
                } else {
                    lectureMonitorScheduler.cancel()
                }
            }
        }

        // Manual check on app startup (optional)
        lifecycleScope.launch {
            delay(5000) // Wait 5 seconds after startup
            val success = notificationManager.checkAndNotify()
            if (!success) {
                // Handle failure if needed
            }
        }
    }
}
*/

// Example iOS/macOS initialization:
/*
// In your Swift AppDelegate or main entry point:

class AppDelegate: NSObject, UIApplicationDelegate {
    var notificationManager: NotificationManager?
    var scheduler: LectureMonitorScheduler?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        // Initialize Kotlin/Native components
        // This requires proper KMP setup and bridging

        // Register background tasks
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: "de.joinside.dhbw.lecture-monitor",
            using: nil
        ) { task in
            self.handleBackgroundTask(task as! BGAppRefreshTask)
        }

        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        scheduleBackgroundTask()
    }

    func scheduleBackgroundTask() {
        let request = BGAppRefreshTaskRequest(identifier: "de.joinside.dhbw.lecture-monitor")
        request.earliestBeginDate = Date(timeIntervalSinceNow: 2 * 60 * 60) // 2 hours

        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Could not schedule background task: \(error)")
        }
    }

    func handleBackgroundTask(_ task: BGAppRefreshTask) {
        scheduleBackgroundTask() // Schedule next check

        task.expirationHandler = {
            // Clean up when time expires
        }

        // Call Kotlin notification manager
        // This requires proper async bridging
        Task {
            // await notificationManager?.checkAndNotify()
            task.setTaskCompleted(success: true)
        }
    }
}
*/

// Example Desktop/macOS (JVM) initialization:
/*
fun main() = application {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Initialize services (similar to Android)
    val notificationManager = initializeNotificationManager()
    val scheduler = LectureMonitorScheduler(scope)

    // Start scheduler
    scheduler.schedule()

    // Clean up on exit
    Runtime.getRuntime().addShutdownHook(Thread {
        scheduler.cancel()
        scope.cancel()
    })

    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
*/

// Scheduler integration with Worker (Android):
/*
class LectureMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Get application context and retrieve notification manager
            val app = applicationContext as? Application ?: return Result.failure()

            // This requires proper DI or singleton pattern
            val notificationManager = getNotificationManager(app)

            // Perform check
            val success = notificationManager.checkAndNotify()

            if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Napier.e("Worker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun getNotificationManager(app: Application): NotificationManager {
        // Retrieve from DI container or singleton
        // Example: return (app as MyApplication).notificationManager
        TODO("Implement DI integration")
    }
}
*/

