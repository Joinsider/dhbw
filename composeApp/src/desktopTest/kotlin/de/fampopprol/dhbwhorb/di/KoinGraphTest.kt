/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.di

import de.fampopprol.dhbwhorb.core.di.coreModule
import de.fampopprol.dhbwhorb.data.di.dataModule
import de.fampopprol.dhbwhorb.data.di.dataPlatformModule
import de.fampopprol.dhbwhorb.presentation.di.presentationModule
import de.fampopprol.dhbwhorb.services.di.servicesModule
import de.fampopprol.dhbwhorb.services.di.servicesPlatformModule
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisDocumentService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisGradeService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.domain.repository.AuthRepository
import de.fampopprol.dhbwhorb.domain.repository.DocumentRepository
import de.fampopprol.dhbwhorb.domain.repository.GradeRepository
import de.fampopprol.dhbwhorb.domain.repository.PreferencesRepository
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.repository.TimetableRepository
import de.fampopprol.dhbwhorb.domain.usecase.GetWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.LoginWithCredentials
import de.fampopprol.dhbwhorb.domain.usecase.Logout
import de.fampopprol.dhbwhorb.services.notifications.NotificationManager
import de.fampopprol.dhbwhorb.services.widget.WidgetTimetableUseCase
import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Guards the dependency graph.
 *
 * A missing Koin binding is not a compile error — it surfaces as a crash the first time a screen
 * asks for it, which can be minutes into a session. `verify()` walks every definition's constructor
 * parameters against the declared bindings without instantiating anything, so a forgotten
 * registration fails here instead of on a user's device.
 */
class KoinGraphTest {

    @Test
    fun coreModule_isComplete() {
        coreModule.verify()
    }

    @Test
    fun dataModule_isComplete() {
        dataModule.verify(extraTypes = dataPlatformTypes)
    }

    @Test
    fun servicesModule_isComplete() {
        servicesModule.verify(extraTypes = servicesExtraTypes)
    }

    @Test
    fun presentationModule_isComplete() {
        presentationModule.verify(extraTypes = presentationExtraTypes)
    }

    /**
     * verify() only walks constructor parameters; it never builds anything. This resolves the real
     * graph — real Room database, real secure storage, real Ktor client — so a binding that type
     * checks but blows up on construction fails here.
     *
     * The ViewModels are deliberately left out: their init blocks start loading immediately, which
     * would put a live request to dualis.dhbw.de into the test suite. They stay covered by verify()
     * and by the UI tests running against the mock graph.
     */
    @Test
    fun graph_actuallyBuilds() {
        val koin = koinApplication {
            modules(
                coreModule,
                dataModule,
                dataPlatformModule(),
                servicesModule,
                servicesPlatformModule()
            )
        }.koin

        try {
            assertNotNull(koin.get<SecureStorageInterface>())
            assertNotNull(koin.get<AppDatabase>())
            assertNotNull(koin.get<HttpClient>())
            assertNotNull(koin.get<SessionManager>())
            assertNotNull(koin.get<AuthenticationService>())
            assertNotNull(koin.get<DualisApiClient>())
            assertNotNull(koin.get<DualisLectureService>())
            assertNotNull(koin.get<DualisGradeService>())
            assertNotNull(koin.get<DualisDocumentService>())
            assertNotNull(koin.get<NotificationManager>())
            assertNotNull(koin.get<WidgetTimetableUseCase>())
            assertNotNull(koin.get<NotificationPreferencesInteractor>())
            assertNotNull(koin.get<ThemePreferences>())
            assertNotNull(koin.get<de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage>())

            // The repository interfaces are what everything above :data now depends on, so a
            // missing binding here would break every screen at once.
            assertNotNull(koin.get<AuthRepository>())
            assertNotNull(koin.get<SessionRepository>())
            assertNotNull(koin.get<TimetableRepository>())
            assertNotNull(koin.get<GradeRepository>())
            assertNotNull(koin.get<DocumentRepository>())
            assertNotNull(koin.get<PreferencesRepository>())

            assertNotNull(koin.get<LoginWithCredentials>())
            assertNotNull(koin.get<Logout>())
            assertNotNull(koin.get<GetWeekTimetable>())

            // The one instance that must not be duplicated: authentication and every subsequent
            // request share cookies through it.
            assertSame(
                koin.get<HttpClient>(),
                koin.get<HttpClient>(),
                "HttpClient has to be a singleton or the session cookie is lost after login"
            )
        } finally {
            koin.close()
        }
    }

    @Test
    fun platformModules_areComplete() {
        dataPlatformModule().verify()
        servicesPlatformModule().verify(extraTypes = listOf(kotlinx.coroutines.CoroutineScope::class))
    }

    private companion object {
        /**
         * Types that are constructed inline rather than resolved from Koin, so verify() cannot see
         * where they come from: the Ktor engine handed to HttpClient, and the `() -> Service`
         * lambdas used for lazy service creation.
         */
        val inlineTypes = listOf(
            io.ktor.client.engine.HttpClientEngine::class,
            Function0::class
        )

        // Provided by dataPlatformModule(), which verify() checks separately.
        val dataPlatformTypes = inlineTypes + listOf(
            de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface::class,
            de.fampopprol.dhbwhorb.data.storage.settings.PlatformSettings::class,
            de.fampopprol.dhbwhorb.data.storage.database.AppDatabase::class
        )
        val servicesExtraTypes = dataPlatformTypes + listOf(
            de.fampopprol.dhbwhorb.domain.repository.TimetableRepository::class,
            de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService::class,
            de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager::class,
            de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider::class,
            de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor::class,
            de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao::class,
            de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao::class,
            de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher::class
        )
        val presentationExtraTypes = servicesExtraTypes + listOf(
            de.fampopprol.dhbwhorb.domain.repository.SessionRepository::class,
            de.fampopprol.dhbwhorb.domain.repository.PreferencesRepository::class,
            de.fampopprol.dhbwhorb.domain.usecase.LoginWithCredentials::class,
            de.fampopprol.dhbwhorb.domain.usecase.Logout::class,
            // The stores' lifetime, provided by coreModule.
            kotlinx.coroutines.CoroutineScope::class,
            de.fampopprol.dhbwhorb.domain.usecase.GetWeekTimetable::class,
            de.fampopprol.dhbwhorb.domain.usecase.AwaitFullWeekTimetable::class,
            de.fampopprol.dhbwhorb.domain.usecase.RefreshTimetable::class,
            de.fampopprol.dhbwhorb.domain.usecase.GetSemesters::class,
            de.fampopprol.dhbwhorb.domain.usecase.GetGradesForSemester::class,
            de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades::class,
            de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa::class,
            de.fampopprol.dhbwhorb.domain.usecase.ListDocuments::class,
            de.fampopprol.dhbwhorb.domain.usecase.DownloadDocument::class
        )
    }
}
