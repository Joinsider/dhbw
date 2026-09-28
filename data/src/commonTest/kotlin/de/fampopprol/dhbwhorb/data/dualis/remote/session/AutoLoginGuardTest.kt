/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.session

import androidx.room.InvalidationTracker
import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.repository.AuthRepositoryImpl
import de.fampopprol.dhbwhorb.data.repository.SessionRepositoryImpl
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.database.dao.documents.CachedDocumentDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeCacheMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LecturerDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.SyncMetadataDao
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.testutil.InMemoryCachedDocumentDao
import de.fampopprol.dhbwhorb.testutil.MockGradeCacheMetadataDao
import de.fampopprol.dhbwhorb.testutil.MockGradeDao
import de.fampopprol.dhbwhorb.testutil.MockLectureEventDao
import de.fampopprol.dhbwhorb.testutil.MockLectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.testutil.MockLecturerDao
import de.fampopprol.dhbwhorb.testutil.MockSyncMetadataDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A wrong stored password must not be tried against Dualis around the clock: after two rejections
 * the automatic login stops, and it stays stopped — across restarts — until the user logs in.
 */
class AutoLoginGuardTest {

    private companion object {
        const val USER = "max.mustermann@hb.dhbw-stuttgart.de"
    }

    /** Answers every login with [result] and counts how many reached "Dualis". */
    private class ScriptedAuthService(
        sessionManager: SessionManager,
        var result: Outcome<Session>
    ) : AuthenticationService(
        sessionManager = sessionManager,
        client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
    ) {
        var loginCount = 0
            private set

        override suspend fun login(username: String, password: String): Outcome<Session> {
            loginCount++
            if (result is Outcome.Ok) sessionManager.storeCredentials(username, password)
            return result
        }
    }

    /** Counts how often the cache was wiped, or refuses to wipe it. */
    private class ClearCountingDatabase : AppDatabase() {
        var clears = 0
        var failing = false
        private val documents = object : InMemoryCachedDocumentDao() {
            override suspend fun deleteAll() {
                if (failing) throw IllegalStateException("disk full")
                clears++
            }
        }

        override fun lectureDao(): LectureEventDao = MockLectureEventDao()
        override fun lecturerDao(): LecturerDao = MockLecturerDao()
        override fun lectureLecturerCrossRefDao(): LectureLecturerCrossRefDao =
            MockLectureLecturerCrossRefDao()
        override fun gradeDao(): GradeDao = MockGradeDao()
        override fun gradeCacheMetadataDao(): GradeCacheMetadataDao = MockGradeCacheMetadataDao()
        override fun cachedDocumentDao(): CachedDocumentDao = documents
        override fun syncMetadataDao(): SyncMetadataDao = MockSyncMetadataDao()
        override fun createInvalidationTracker(): InvalidationTracker = throw NotImplementedError()

        @Suppress("NOTHING_TO_OVERRIDE")
        override fun clearAllTables() { /* no-op */ }
    }

    private val storage = FakeSecureStorage()
    private val sessionManager = SessionManager(storage).apply { storeCredentials(USER, "old-password") }
    private val authService = ScriptedAuthService(sessionManager, Outcome.Err(AppError.InvalidCredentials))
    private val reAuthenticator = ReAuthenticator(sessionManager, authService)
    private val database = ClearCountingDatabase()
    private val authRepository = AuthRepositoryImpl(
        authenticationService = authService,
        reAuthenticator = reAuthenticator,
        credentialsProvider = CredentialsStorageProvider(storage),
        database = database
    )

    private suspend fun rejectTwice() {
        repeat(AutoLoginGuard.MAX_FAILURES) { reAuthenticator.reAuthenticate() }
    }

    @Test
    fun afterTwoRejections_noFurtherAutomaticLoginReachesDualis() = runTest {
        rejectTwice()
        assertEquals(2, authService.loginCount)

        repeat(5) {
            assertEquals(Outcome.Err(AppError.InvalidCredentials), reAuthenticator.reAuthenticate())
        }

        assertEquals(2, authService.loginCount, "the guard answers without asking Dualis")
        assertTrue(sessionManager.autoLoginGuard.isBlocked)
    }

    @Test
    fun oneRejection_stillAllowsTheNextAttempt() = runTest {
        reAuthenticator.reAuthenticate()

        assertFalse(sessionManager.autoLoginGuard.isBlocked)
        reAuthenticator.reAuthenticate()
        assertEquals(2, authService.loginCount)
    }

    @Test
    fun aSuccessInBetween_meansTheRejectionsWereNotInARow() = runTest {
        reAuthenticator.reAuthenticate()
        authService.result = Outcome.Ok(Session(userFullName = null))
        reAuthenticator.reAuthenticate()
        authService.result = Outcome.Err(AppError.InvalidCredentials)
        reAuthenticator.reAuthenticate()

        assertFalse(sessionManager.autoLoginGuard.isBlocked)
        assertEquals(1, sessionManager.autoLoginGuard.failures)
    }

    @Test
    fun beingOffline_doesNotCount() = runTest {
        // Nothing about the password is learnt from a missing connection or a server in
        // maintenance; closing the guard for that would log everybody out after a bad night.
        authService.result = Outcome.Err(AppError.Offline)
        repeat(3) { reAuthenticator.reAuthenticate() }
        authService.result = Outcome.Err(AppError.Http(503))
        repeat(3) { reAuthenticator.reAuthenticate() }

        assertFalse(sessionManager.autoLoginGuard.isBlocked)
        assertEquals(6, authService.loginCount)
    }

    @Test
    fun theBlockSurvivesARestart() = runTest {
        rejectTwice()

        // A new process: new SessionManager, new ReAuthenticator, same secure storage.
        val restarted = SessionManager(storage)
        val restartedAuth = ScriptedAuthService(restarted, Outcome.Ok(Session(userFullName = null)))
        val result = ReAuthenticator(restarted, restartedAuth).reAuthenticate()

        assertEquals(Outcome.Err(AppError.InvalidCredentials), result)
        assertEquals(0, restartedAuth.loginCount)
        assertTrue(restarted.autoLoginGuard.blocked.value)
    }

    @Test
    fun whileBlocked_theAppShowsTheLoginScreen() = runTest {
        val sessions = SessionRepositoryImpl(sessionManager)
        assertFalse(sessions.autoLoginBlocked.value)

        rejectTwice()

        assertTrue(sessions.autoLoginBlocked.value, "a running app hears about it at once")
        assertNull(sessions.currentSession())
        assertFalse(sessions.canAuthenticate(), "stored credentials are no reason to try again")
        assertFalse(sessions.isLoggedIn())
    }

    @Test
    fun aManualLogin_liftsTheBlock() = runTest {
        rejectTwice()
        authService.result = Outcome.Ok(Session(userFullName = null))

        assertIs<Outcome.Ok<Session>>(authRepository.login(USER, "new-password"))

        assertFalse(sessionManager.autoLoginGuard.isBlocked)
        assertFalse(sessionManager.autoLoginGuard.blocked.value)
        assertIs<Outcome.Ok<Session>>(reAuthenticator.reAuthenticate())
        assertEquals(0, database.clears, "the same account keeps its cache")
    }

    @Test
    fun aFailedManualLogin_keepsTheBlock() = runTest {
        rejectTwice()

        authRepository.login(USER, "still-wrong")

        assertTrue(sessionManager.autoLoginGuard.isBlocked)
    }

    @Test
    fun anotherAccountLoggingInAfterABlock_doesNotInheritTheCache() = runTest {
        rejectTwice()
        authService.result = Outcome.Ok(Session(userFullName = null))

        authRepository.login("erika.musterfrau@hb.dhbw-stuttgart.de", "pw")

        assertEquals(1, database.clears)
    }

    @Test
    fun loggingOut_liftsTheBlock() = runTest {
        rejectTwice()

        authRepository.logout()

        assertFalse(sessionManager.autoLoginGuard.isBlocked)
        assertFalse(sessionManager.autoLoginGuard.blocked.value)
    }

    @Test
    fun aFirstManualLogin_hasNoPreviousAccountToClearAfter() = runTest {
        sessionManager.logout()
        authService.result = Outcome.Ok(Session(userFullName = null))

        assertIs<Outcome.Ok<Session>>(authRepository.login(USER, "pw"))

        assertEquals(0, database.clears)
    }

    @Test
    fun theSameAccountInAnotherCase_isNotAnAccountSwitch() = runTest {
        rejectTwice()
        authService.result = Outcome.Ok(Session(userFullName = null))

        authRepository.login(USER.uppercase(), "new-password")

        assertEquals(0, database.clears)
        assertFalse(sessionManager.autoLoginGuard.isBlocked)
    }

    @Test
    fun anAccountSwitchWhoseCacheCannotBeCleared_doesNotLogIn() = runTest {
        // Logging the new account in on top of the old one's grades is exactly what the clearing
        // is there to prevent, so a failure to clear stops the login before it is sent.
        rejectTwice()
        authService.result = Outcome.Ok(Session(userFullName = null))
        database.failing = true

        val result = authRepository.login("erika.musterfrau@hb.dhbw-stuttgart.de", "pw")

        assertIs<AppError.Storage>((result as Outcome.Err).error)
        assertEquals(2, authService.loginCount, "no request for the new account")
        assertTrue(sessionManager.autoLoginGuard.isBlocked)
    }

    @Test
    fun aLogoutWhoseCacheCannotBeCleared_stillLogsOutButSaysSo() = runTest {
        rejectTwice()
        database.failing = true

        val result = authRepository.logout()

        assertIs<AppError.Storage>((result as Outcome.Err).error)
        assertFalse(sessionManager.hasStoredCredentials())
        assertFalse(sessionManager.autoLoginGuard.isBlocked)
    }

    @Test
    fun anUnreadableCount_isTreatedAsNoRejections() {
        // Nothing but this class writes the key, but a store that hands back garbage must not
        // lock the user out — or crash the background check.
        storage.setString("dualis_auto_login_failures", "not a number")

        assertEquals(0, sessionManager.autoLoginGuard.failures)
        assertFalse(sessionManager.autoLoginGuard.isBlocked)
    }

    @Test
    fun aSuccessReportedWhileBlocked_doesNotOpenTheGuard() = runTest {
        // Only a manual login opens it: nothing that merely claims success may do so in passing.
        rejectTwice()

        sessionManager.autoLoginGuard.recordSuccess()

        assertTrue(sessionManager.autoLoginGuard.isBlocked)
    }
}
