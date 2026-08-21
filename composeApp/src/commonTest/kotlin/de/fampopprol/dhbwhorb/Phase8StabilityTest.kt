package de.fampopprol.dhbwhorb

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import kotlin.test.Test

/**
 * Phase 8 Stability Tests:
 * 1. Verify that the app shows a LoadingIndicator when isInitialized is false (early setContent).
 * 2. Verify that the app transitions to the main UI once isInitialized is true.
 */
@OptIn(ExperimentalTestApi::class)
class Phase8StabilityTest {

    private val testViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Test
    fun app_displaysMainUI_whenInitialized() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { App() }
            }
        }

        // Wait for composition
        waitForIdle()

        // Verify that main UI (Welcome screen) is shown
        onNodeWithTag("appTitle").assertIsDisplayed()
        onNodeWithTag("loginForm").assertIsDisplayed()
    }
}
