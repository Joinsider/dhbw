package de.joinside.dhbw

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
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
import de.joinside.dhbw.ui.schedule.viewModels.TimetableViewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies

fun MainViewController() = ComposeUIViewController {
    // Initialize Napier for iOS logging (only once)
    remember {
        Napier.base(DebugAntilog())
        Napier.d("iOS application starting", tag = "MainViewController")
    }

    // Create database (cached with remember)
    val database = remember {
        createRoomDatabase(getDatabaseBuilder()).also {
            Napier.d("Database initialized", tag = "MainViewController")
        }
    }

    // Create shared HttpClient for cookie sharing (cached with remember)
    val sharedHttpClient = remember {
        HttpClient {
            expectSuccess = false
            install(HttpCookies)
        }.also {
            Napier.d("Shared HttpClient created", tag = "MainViewController")
        }
    }

    // Create session manager (cached with remember)
    val sessionManager = remember {
        val secureStorage = SecureStorage()
        val secureStorageWrapper = SecureStorageWrapper(secureStorage)
        SessionManager(secureStorageWrapper)
    }

    // Create authentication service (cached with remember)
    val authenticationService = remember {
        AuthenticationService(
            sessionManager = sessionManager,
            client = sharedHttpClient
        ).also {
            Napier.d("AuthenticationService initialized", tag = "MainViewController")
        }
    }

    // Create Dualis lecture service (cached with remember)
    val dualisLectureService = remember {
        val dualisApiClient = DualisApiClient(client = sharedHttpClient)
        val htmlParser = HtmlParser()
        val timetableParser = TimetableParser()

        DualisLectureService(
            apiClient = dualisApiClient,
            sessionManager = sessionManager,
            authenticationService = authenticationService,
            timetableParser = timetableParser,
            htmlParser = htmlParser,
            lectureEventDao = database.lectureDao(),
            lecturerDao = database.lecturerDao(),
            lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
        ).also {
            Napier.d("DualisLectureService initialized", tag = "MainViewController")
        }
    }

    // Create lecture service (cached with remember)
    val lectureService = remember {
        LectureService(
            database = database,
            dualisLectureService = dualisLectureService
        ).also {
            Napier.d("LectureService initialized", tag = "MainViewController")
        }
    }

    // Create timetable ViewModel (cached with remember)
    val timetableViewModel = remember {
        TimetableViewModel(
            lectureService = lectureService,
            lecturerDao = database.lecturerDao(),
            lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
        ).also {
            Napier.d("TimetableViewModel initialized", tag = "MainViewController")
            Napier.i("All services initialized successfully!", tag = "MainViewController")
        }
    }

    App(
        testAuthenticationService = authenticationService,
        timetableViewModel = timetableViewModel,
        database = database
    )
}
