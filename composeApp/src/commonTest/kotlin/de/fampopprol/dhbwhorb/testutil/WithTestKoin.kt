/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.testutil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.Module
import org.koin.dsl.koinApplication

// The graph itself is in :core:testing, where every module's tests can reach it. This wrapper
// stays here: it is a composable, and :core:testing has no Compose — deliberately, since
// :presentation's tests depend on it and :presentation is what has to stay Compose-free.

/**
 * Scopes Koin to the composition instead of starting it globally, so tests stay independent of
 * each other and need no teardown.
 *
 * `org.koin.compose.KoinApplication` looks like it does this, but it calls `startKoin` under the
 * hood and only nulls its local reference when the composition is disposed — it never stops that
 * global context. The next test's `WithTestKoin` then finds a Koin instance already running and
 * silently reattaches to it instead of building its own, so every test after the first in a given
 * JVM ran against whichever graph (and `authenticated`/`overrides`) the first one happened to set
 * up. `KoinIsolatedContext` sidesteps the global context entirely: each call gets its own `Koin`.
 */
@Composable
fun WithTestKoin(
    authenticated: Boolean = false,
    overrides: Module? = null,
    content: @Composable () -> Unit
) {
    val koinApp = remember(authenticated, overrides) {
        koinApplication {
            modules(listOfNotNull(testAppModule(authenticated), overrides))
        }
    }
    KoinIsolatedContext(context = koinApp) {
        content()
    }
}
