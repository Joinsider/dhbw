/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.testutil

import androidx.compose.runtime.Composable
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisDocumentService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisGradeService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisPageGateway
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.repository.AuthRepositoryImpl
import de.fampopprol.dhbwhorb.data.repository.DocumentRepositoryImpl
import de.fampopprol.dhbwhorb.data.repository.GradeRepositoryImpl
import de.fampopprol.dhbwhorb.data.repository.PreferencesRepositoryImpl
import de.fampopprol.dhbwhorb.data.repository.SessionRepositoryImpl
import de.fampopprol.dhbwhorb.data.repository.TimetableRepositoryImpl
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.domain.repository.AuthRepository
import de.fampopprol.dhbwhorb.domain.repository.DocumentRepository
import de.fampopprol.dhbwhorb.domain.repository.GradeRepository
import de.fampopprol.dhbwhorb.domain.repository.PreferencesRepository
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.repository.TimetableRepository
import de.fampopprol.dhbwhorb.domain.usecase.AwaitFullWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.domain.usecase.DownloadDocument
import de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades
import de.fampopprol.dhbwhorb.domain.usecase.GetCachedLectures
import de.fampopprol.dhbwhorb.domain.usecase.GetGradesForSemester
import de.fampopprol.dhbwhorb.domain.usecase.GetSemesters
import de.fampopprol.dhbwhorb.domain.usecase.GetWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.ListDocuments
import de.fampopprol.dhbwhorb.domain.usecase.LoginWithCredentials
import de.fampopprol.dhbwhorb.domain.usecase.Logout
import de.fampopprol.dhbwhorb.domain.usecase.RefreshTimetable
import de.fampopprol.dhbwhorb.domain.usecase.RestoreSession
import de.fampopprol.dhbwhorb.presentation.app.AppStore
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsStore
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.compose.KoinApplication
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The object graph the UI tests run against: mock database, mock HTTP engine, fake secure storage.
 *
 * Since dependencies are injected rather than passed down as parameters, a test overrides the graph
 * instead of the call site — which is why `App()` no longer needs `test…` parameters.
 */
fun testAppModule(authenticated: Boolean = false): Module = module {
    single<SecureStorageInterface> { FakeSecureStorage() }
    single<AppDatabase> { MockAppDatabase() }

    single {
        HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }) {
            expectSuccess = false
        }
    }

    single { SessionManager(secureStorage = get()) }
    single<AuthenticationService> { MockAuthenticationService(authenticated) }
    single<CredentialsStorageProvider> { MockCredentialsProvider() }
    single { DualisApiClient(client = get()) }

    single { get<AppDatabase>().lectureDao() }
    single { get<AppDatabase>().lecturerDao() }
    single { get<AppDatabase>().lectureLecturerCrossRefDao() }
    single { get<AppDatabase>().gradeDao() }
    single { get<AppDatabase>().gradeCacheMetadataDao() }
    single { get<AppDatabase>().syncMetadataDao() }

    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { ReAuthenticator(sessionManager = get(), authenticationService = get()) }
    single { DualisPageGateway(apiClient = get(), sessionManager = get(), reAuthenticator = get()) }

    single {
        DualisLectureService(
            apiClient = get(), sessionManager = get(), gateway = get(),
            lectureEventDao = get(), lecturerDao = get(), lectureLecturerCrossRefDao = get()
        )
    }
    single {
        DualisGradeService(
            gateway = get(), sessionManager = get(),
            gradeDao = get(), gradeCacheMetadataDao = get()
        )
    }
    single {
        DualisDocumentService(
            apiClient = get(), sessionManager = get(), reAuthenticator = get(), gateway = get()
        )
    }

    single { ThemePreferences(storage = get()) }
    single { NotificationPreferences(storage = get()) }
    single { NotificationPreferencesInteractor(preferences = get()) }

    single<AuthRepository> {
        AuthRepositoryImpl(
            authenticationService = get(),
            reAuthenticator = get(),
            credentialsProvider = get(),
            database = get()
        )
    }
    single<SessionRepository> { SessionRepositoryImpl(sessionManager = get()) }
    single<TimetableRepository> {
        TimetableRepositoryImpl(
            lectureService = get(), lectureEventDao = get(), syncMetadataDao = get(), scope = get()
        )
    }
    single<GradeRepository> { GradeRepositoryImpl(gradeService = get()) }
    single<DocumentRepository> { DocumentRepositoryImpl(documentService = get()) }
    single<PreferencesRepository> {
        PreferencesRepositoryImpl(themePreferences = get(), notificationPreferences = get())
    }

    factory { LoginWithCredentials(authRepository = get()) }
    factory { RestoreSession(sessionRepository = get(), authRepository = get()) }
    factory { Logout(authRepository = get()) }
    factory { GetWeekTimetable(repository = get()) }
    factory { AwaitFullWeekTimetable(repository = get()) }
    factory { RefreshTimetable(repository = get()) }
    factory { GetCachedLectures(repository = get()) }
    factory { GetSemesters(repository = get()) }
    factory { GetGradesForSemester(repository = get()) }
    factory { GetAllGrades(getSemesters = get(), getGradesForSemester = get()) }
    factory { ComputeGpa() }
    factory { ListDocuments(repository = get()) }
    factory { DownloadDocument(repository = get()) }

    single { AppStore(sessionRepository = get(), logout = get(), scope = get()) }
    single { AuthStore(loginWithCredentials = get(), scope = get()) }
    single {
        TimetableStore(
            getWeekTimetable = get(), awaitFullWeekTimetable = get(), refreshTimetable = get(),
            scope = get()
        )
    }
    single {
        GradesStore(
            getSemesters = get(), getGradesForSemester = get(), getAllGrades = get(),
            computeGpa = get(), sessionRepository = get(), scope = get()
        )
    }
    single {
        DocumentsStore(
            listDocuments = get(), downloadDocument = get(), sessionRepository = get(),
            scope = get()
        )
    }
    single { SettingsStore(preferences = get(), scope = get()) }
}

/**
 * The same graph outside a composition, for plain unit tests that construct a ViewModel directly.
 */
fun testKoin(authenticated: Boolean = false): Koin =
    koinApplication { modules(testAppModule(authenticated)) }.koin

/**
 * Scopes Koin to the composition instead of starting it globally, so tests stay independent of
 * each other and need no teardown.
 */
@Composable
fun WithTestKoin(
    authenticated: Boolean = false,
    overrides: Module? = null,
    content: @Composable () -> Unit
) {
    KoinApplication(application = {
        modules(listOfNotNull(testAppModule(authenticated), overrides))
    }) {
        content()
    }
}
