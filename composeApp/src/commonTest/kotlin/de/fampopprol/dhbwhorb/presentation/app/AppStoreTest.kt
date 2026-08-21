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
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.collectEffects
import de.fampopprol.dhbwhorb.testutil.fakes.FakeAuthRepository
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
        auth: FakeAuthRepository = FakeAuthRepository()
    ) = AppStore(
        sessionRepository = session,
        logout = Logout(auth),
        scope = TestScopes.immediate()
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
        assertEquals(AppScreen.TIMETABLE, state.screen)
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
        assertEquals(AppScreen.LOGIN, state.screen)
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
    fun aSessionFoundWhileOnAnotherScreen_doesNotYankTheUserAway() {
        val onGrades = reduceApp(AppState(), AppMsg.Navigated(AppScreen.GRADES))

        val restored = reduceApp(onGrades, AppMsg.SessionRestored("Max Mustermann", isDemo = false))

        assertEquals(AppScreen.GRADES, restored.screen)
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
        assertEquals(AppScreen.LOGIN, state.screen)
        assertNull(state.userFullName)
        assertFalse(state.isRestoring, "Logging out is not a restore")
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
    fun navigating_onlyChangesTheScreen() {
        val loggedIn = reduceApp(AppState(), AppMsg.SessionRestored("Max", isDemo = false))

        val navigated = reduceApp(loggedIn, AppMsg.Navigated(AppScreen.DOCUMENTS))

        assertEquals(AppScreen.DOCUMENTS, navigated.screen)
        assertTrue(navigated.isLoggedIn)
        assertEquals("Max", navigated.userFullName)
    }
}
