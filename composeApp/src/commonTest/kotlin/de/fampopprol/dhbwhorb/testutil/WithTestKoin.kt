/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.testutil

import androidx.compose.runtime.Composable
import org.koin.compose.KoinApplication
import org.koin.core.module.Module

// The graph itself is in :core:testing, where every module's tests can reach it. This wrapper
// stays here: it is a composable, and :core:testing has no Compose — deliberately, since
// :presentation's tests depend on it and :presentation is what has to stay Compose-free.

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
