/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.app

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.domain.usecase.Logout
import de.fampopprol.dhbwhorb.domain.usecase.PurgeExpiredDocuments
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.collectEffects
import de.fampopprol.dhbwhorb.presentation.store.SessionScopedStore
import de.fampopprol.dhbwhorb.testutil.fakes.FakeAuthRepository
import de.fampopprol.dhbwhorb.testutil.fakes.FakeDocumentRepository
import de.fampopprol.dhbwhorb.testutil.fakes.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStoreTest {

    private fun store(
        session: FakeSessionRepository,
        auth: FakeAuthRepository = FakeAuthRepository(),
        documents: FakeDocumentRepository = FakeDocumentRepository(),
        sessionScopedStores: () -> List<SessionScopedStore> = { emptyList() }
    ) = AppStore(
        sessionRepository = session,
        logout = Logout(auth),
        purgeExpiredDocuments = PurgeExpiredDocuments(documents),
        scope = TestScopes.immediate(),
        sessionScopedStores = sessionScopedStores
    )

    @Test
    fun beforeTheSessionIsChecked_theRootIsRestoring() {
        // The first frame must not guess. Guessing is what sent users with an expired session to
        // the timetable and then bounced them back.
        assertTrue(AppState().isRestoring)
        assertFalse(AppState().isLoggedIn)
    }

    @Test
    fun aStoredSession_opensTheTimetable() = runTest {
        val store = store(FakeSessionRepository(session = Session(userFullName = "Max Mustermann")))

        store.dispatch(AppIntent.Started)

        val state = store.state.value
        assertTrue(state.isLoggedIn)
        assertFalse(state.isRestoring)
        assertEquals("Max Mustermann", state.userFullName)
        store.close()
    }

    @Test
    fun noStoredSession_opensTheLogin() = runTest {
        val store = store(FakeSessionRepository(session = null))

        store.dispatch(AppIntent.Started)

        val state = store.state.value
        assertFalse(state.isLoggedIn)
        assertFalse(state.isRestoring)
        store.close()
    }

    @Test
    fun aDemoSession_isMarkedAsOne() = runTest {
        val store = store(
            FakeSessionRepository(session = Session(userFullName = null, isDemo = true))
        )

        store.dispatch(AppIntent.Started)

        assertTrue(store.state.value.isDemo)
        store.close()
    }

    @Test
    fun loggingOut_returnsToTheLoginAndForgetsTheUser() = runTest {
        val auth = FakeAuthRepository()
        val store = store(FakeSessionRepository(session = Session("Max Mustermann")), auth)

        store.dispatch(AppIntent.Started)
        store.dispatch(AppIntent.LogoutRequested)

        val state = store.state.value
        assertEquals(1, auth.logoutCount)
        assertFalse(state.isLoggedIn)
        assertNull(state.userFullName)
        assertFalse(state.isRestoring, "Logging out is not a restore")
        store.close()
    }

    @Test
    fun loggingOut_emptiesTheScreensThatHoldTheAccountsData() = runTest {
        // The stores are singletons: without this the next person to log in on this device sees
        // the previous one's grades until the first fetch comes back.
        var resets = 0
        val screens = List(3) { SessionScopedStore { resets++ } }
        val store = store(
            FakeSessionRepository(session = Session("Max Mustermann")),
            sessionScopedStores = { screens }
        )

        store.dispatch(AppIntent.LogoutRequested)

        assertEquals(3, resets)
        store.close()
    }

    @Test
    fun aLogoutThatCouldNotClearTheCache_stillLogsOutButSaysSo() = runTest {
        val auth = FakeAuthRepository(
            logoutResult = Outcome.Err(AppError.Storage("clearing cached data on logout"))
        )
        val store = store(FakeSessionRepository(session = Session("Max Mustermann")), auth)
        val effects = mutableListOf<AppEffect>()
        val collector = collectEffects(store) { effects += it }

        store.dispatch(AppIntent.LogoutRequested)

        assertFalse(store.state.value.isLoggedIn, "The session is gone either way")
        assertEquals(listOf<AppEffect>(AppEffect.CacheNotCleared), effects)
        collector.cancel()
        store.close()
    }

    @Test
    fun theStoreDoesNotTrackAScreen() {
        // Since P5 the navigation graph's back stack is the only answer to "where am I". A second
        // one here could disagree with it, which is why AppState no longer has a screen at all.
        val restored = reduceApp(AppState(), AppMsg.SessionRestored("Max", isDemo = false))

        assertTrue(restored.isLoggedIn)
        assertEquals("Max", restored.userFullName)
    }

    @Test
    fun startingTheApp_deletesDocumentsPastTheirDeadline() = runTest {
        // The cache has a four-week limit. Enforcing it only when the documents screen opens
        // would leave the files of someone who never opens it again on the device forever.
        val documents = FakeDocumentRepository()
        val store = store(FakeSessionRepository(session = null), documents = documents)

        store.dispatch(AppIntent.Started)

        assertEquals(1, documents.purges)
        store.close()
    }
}
