/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.di

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
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsInstallGuard
import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
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
import de.fampopprol.dhbwhorb.net.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform-specific data bindings: [de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface]
 * and [AppDatabase], both of which need platform APIs (Android needs a Context, the others do not).
 */
expect fun dataPlatformModule(): Module

private const val NETWORK_TIMEOUT_MILLIS = 30_000L

/**
 * The single definition of the shared [HttpClient] and everything built on top of it.
 *
 * Before DI this graph was wired by hand in three entry points, and they had drifted apart —
 * iOS built its client without [HttpTimeout], so a hanging Dualis request never timed out there.
 * One definition removes that class of difference.
 */
val dataModule = module {

    // Cookie storage lives in the client, so authentication and every subsequent request must
    // share this instance — otherwise the session cookie is lost after login.
    single {
        HttpClientFactory.create {
            expectSuccess = false
            install(HttpCookies)
            install(HttpTimeout) {
                socketTimeoutMillis = NETWORK_TIMEOUT_MILLIS
                connectTimeoutMillis = NETWORK_TIMEOUT_MILLIS
                requestTimeoutMillis = NETWORK_TIMEOUT_MILLIS
            }
        }
    }

    single { DualisApiClient(client = get()) }
    single { SessionManager(secureStorage = get()) }
    single { AuthenticationService(sessionManager = get(), client = get()) }
    single { CredentialsStorageProvider(secureStorage = get()) }

    // One re-authenticator for the whole app: it is what makes concurrent 401s share a single
    // login instead of racing each other.
    single { ReAuthenticator(sessionManager = get(), authenticationService = get()) }

    single { DualisPageGateway(apiClient = get(), sessionManager = get(), reAuthenticator = get()) }

    // DAOs, so consumers depend on the DAO they use rather than the whole database.
    single { get<AppDatabase>().lectureDao() }
    single { get<AppDatabase>().lecturerDao() }
    single { get<AppDatabase>().lectureLecturerCrossRefDao() }
    single { get<AppDatabase>().gradeDao() }
    single { get<AppDatabase>().gradeCacheMetadataDao() }
    single { get<AppDatabase>().syncMetadataDao() }

    single {
        DualisLectureService(
            apiClient = get(),
            sessionManager = get(),
            gateway = get(),
            lectureEventDao = get(),
            lecturerDao = get(),
            lectureLecturerCrossRefDao = get()
        )
    }

    single {
        DualisGradeService(
            gateway = get(),
            sessionManager = get(),
            gradeDao = get(),
            gradeCacheMetadataDao = get()
        )
    }

    single {
        DualisDocumentService(
            apiClient = get(),
            sessionManager = get(),
            reAuthenticator = get(),
            gateway = get()
        )
    }

    // The settings storage is where the theme and the notification toggles live. They used to
    // share the secure storage with credentials, which cost a Keychain dialog per value on desktop.
    single { SettingsStorage(settings = get(), legacy = get()) }

    // Runs once from initKoin(), before anything reads a credential. See the class for why it is
    // a stored stamp and not a flag.
    single { CredentialsInstallGuard(storage = get()) }
    single { ThemePreferences(storage = get()) }
    single { NotificationPreferences(storage = get()) }
    single { NotificationPreferencesInteractor(preferences = get()) }

    // Repositories — the interfaces live in :domain, the implementations here. Everything above
    // this line is an implementation detail nothing outside :data should resolve.
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
            lectureService = get(),
            lectureEventDao = get(),
            syncMetadataDao = get(),
            scope = get()
        )
    }
    single<GradeRepository> { GradeRepositoryImpl(gradeService = get()) }
    single<DocumentRepository> { DocumentRepositoryImpl(documentService = get()) }
    single<PreferencesRepository> {
        PreferencesRepositoryImpl(themePreferences = get(), notificationPreferences = get())
    }

    // Use cases are factories: they hold no state, so there is nothing to share between callers.
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
}
