/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.domain.repository.DocumentRepository
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.navItemTestTag
import kotlinx.coroutines.awaitCancellation
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
class AppTest {

    private val testViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Test
    fun app_displaysAppContainer_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { App() }
            }
        }

        // Check that app container is displayed
        onNodeWithTag("appContainer").assertIsDisplayed()
    }

    @Test
    fun app_displaysAppTitle_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { App() }
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // Check that app title is displayed
        onNodeWithTag("appTitle").assertIsDisplayed()
    }

    @Test
    fun app_displaysLoginForm_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { App() }
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // Check that login form is displayed on welcome screen
        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    @Test
    fun app_showsLoginFormComponents() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { App() }
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // LoginForm should be displayed with its components
        onNodeWithTag("loginForm").assertIsDisplayed()
        onNodeWithTag("usernameField").assertIsDisplayed()
        onNodeWithTag("passwordField").assertIsDisplayed()
        onNodeWithTag("loginButton").assertIsDisplayed()
    }

    @Test
    fun app_usesCorrectTheme() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { App() }
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // Verify that app container is rendered (theme is applied)
        onNodeWithTag("appContainer").assertIsDisplayed()
    }

    @Test
    fun app_hasCorrectLayout_onWelcomeScreen() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { App() }
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // Verify all welcome screen elements are displayed
        onNodeWithTag("appContainer").assertIsDisplayed()
        onNodeWithTag("appTitle").assertIsDisplayed()
        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    // AppStore's effect handler runs on an unconfined test dispatcher, so it can run to
    // completion as part of the first composition itself — whether the session check has
    // already resolved by the time this assertion runs would otherwise be a Compose scheduling
    // detail, not something this test controls. Housekeeping is overridden to never return,
    // which keeps the store in its initial isRestoring = true state for the whole test.
    @Test
    fun app_showsRestoringIndicator_beforeSessionCheckCompletes() = runComposeUiTest {
        val sessionCheckNeverCompletes = module {
            single<DocumentRepository> {
                object : DocumentRepository {
                    override suspend fun listDocuments() = Outcome.Ok(emptyList<DualisDocument>())
                    override suspend fun downloadDocument(document: DualisDocument) =
                        Outcome.Ok(ByteArray(0))
                    override suspend fun purgeExpiredDocuments(): Int = awaitCancellation()
                }
            }
        }

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin(overrides = sessionCheckNeverCompletes) { App() }
            }
        }
        waitForIdle()

        onNodeWithTag("appRestoring").assertIsDisplayed()
    }

    @Test
    fun app_showsNavHost_whenAlreadyLoggedIn() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin(authenticated = true) { App() }
            }
        }
        waitForIdle()

        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertIsDisplayed()
        assertFailsWith<AssertionError> { onNodeWithTag("loginForm").assertIsDisplayed() }
        assertFailsWith<AssertionError> { onNodeWithTag("appRestoring").assertIsDisplayed() }
    }
}
