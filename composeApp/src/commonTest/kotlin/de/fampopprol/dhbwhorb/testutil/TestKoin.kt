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
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.services.LectureService
import de.fampopprol.dhbwhorb.services.LogoutUseCase
import de.fampopprol.dhbwhorb.ui.documents.viewModels.DocumentsViewModel
import de.fampopprol.dhbwhorb.ui.grades.viewModels.GradesViewModel
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
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

    single {
        DualisLectureService(
            apiClient = get(), sessionManager = get(), authenticationService = get(),
            lectureEventDao = get(), lecturerDao = get(), lectureLecturerCrossRefDao = get()
        )
    }
    single {
        DualisGradeService(
            apiClient = get(), sessionManager = get(), authenticationService = get(),
            gradeDao = get(), gradeCacheMetadataDao = get()
        )
    }
    single { DualisDocumentService(apiClient = get(), sessionManager = get(), authenticationService = get()) }
    single { LectureService(database = get(), dualisLectureServiceFactory = { get() }) }

    single { ThemePreferences(storage = get()) }
    single { NotificationPreferences(storage = get()) }
    single { NotificationPreferencesInteractor(preferences = get()) }
    single { LogoutUseCase(sessionManager = get(), credentialsProvider = get(), database = get()) }

    single { TimetableViewModel(lectureService = get(), lecturerDao = get(), lectureLecturerCrossRefDao = get()) }
    single { GradesViewModel(gradeService = get(), gradeDao = get()) }
    single { DocumentsViewModel(dualisDocumentService = get()) }
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
