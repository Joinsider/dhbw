/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.di

import de.fampopprol.dhbwhorb.presentation.app.AppStore
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsStore
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableStore
import de.fampopprol.dhbwhorb.testutil.testKoin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * The screens resolve their store through `koinInject()`, so a missing binding shows up only when
 * that screen is opened. This resolves all six up front, against the mock graph rather than the
 * real one — the real one would use whatever credentials happen to sit in the developer's keychain
 * and could put a live Dualis request into the test suite.
 */
class StoreResolutionTest {

    private val koin = testKoin()

    @Test
    fun everyStore_resolves() {
        assertNotNull(koin.get<AppStore>())
        assertNotNull(koin.get<AuthStore>())
        assertNotNull(koin.get<TimetableStore>())
        assertNotNull(koin.get<GradesStore>())
        assertNotNull(koin.get<DocumentsStore>())
        assertNotNull(koin.get<SettingsStore>())
    }

    @Test
    fun stores_areSingletons() {
        // Not factories: they hold the weeks and semesters already loaded. A fresh instance per
        // navigation is exactly the reload-on-every-tab-switch this phase removes.
        assertSame(koin.get<TimetableStore>(), koin.get<TimetableStore>())
        assertSame(koin.get<GradesStore>(), koin.get<GradesStore>())
        assertSame(koin.get<DocumentsStore>(), koin.get<DocumentsStore>())
        assertSame(koin.get<AppStore>(), koin.get<AppStore>())
    }
}
