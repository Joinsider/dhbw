package de.joinside.dhbw

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.joinside.dhbw.data.dualis.remote.DualisApiClient
import de.joinside.dhbw.data.dualis.remote.parser.HtmlParser
import de.joinside.dhbw.data.dualis.remote.parser.TimetableParser
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.dualis.remote.services.DualisLectureService
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.storage.credentials.SecureStorage
import de.joinside.dhbw.data.storage.credentials.SecureStorageWrapper
import de.joinside.dhbw.data.storage.database.createRoomDatabase
import de.joinside.dhbw.data.storage.database.getDatabaseBuilder
import de.joinside.dhbw.services.LectureService
import de.joinside.dhbw.ui.pages.TimetableViewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies

fun main() {
    // Initialize Napier for JVM logging
    Napier.base(DebugAntilog())
    Napier.d("JVM Desktop application starting", tag = "Main")

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
        lecturerDao = database.lecturerDao()
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
        lectureService = lectureService
    )
    Napier.d("TimetableViewModel initialized", tag = "Main")

    Napier.i("All services initialized successfully!", tag = "Main")

    application {
        Napier.d("Creating main window", tag = "Main")
        Window(
            onCloseRequest = {
                Napier.d("Application closing", tag = "Main")
                exitApplication()
            },
            title = "dhbw",
        ) {
            App(
                testAuthenticationService = authenticationService,
                timetableViewModel = timetableViewModel,
                database = database
            )
        }
    }
}