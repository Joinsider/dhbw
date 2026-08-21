/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.di

import de.fampopprol.dhbwhorb.testutil.testKoin
import de.fampopprol.dhbwhorb.ui.documents.viewModels.DocumentsViewModel
import de.fampopprol.dhbwhorb.ui.grades.viewModels.GradesViewModel
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * The screens resolve their ViewModel through `koinInject()`, so a missing binding shows up only
 * when that screen is opened. This resolves all three up front, against the mock graph rather than
 * the real one — the real one would use whatever credentials happen to sit in the developer's
 * keychain and could put a live Dualis request into the test suite.
 */
class ViewModelResolutionTest {

    private val koin = testKoin()

    @Test
    fun everyScreenViewModel_resolves() {
        assertNotNull(koin.get<TimetableViewModel>())
        assertNotNull(koin.get<GradesViewModel>())
        assertNotNull(koin.get<DocumentsViewModel>())
    }

    @Test
    fun viewModels_areSingletons() {
        // Not factories: they cache loaded weeks and semesters. A fresh instance per navigation is
        // exactly the reload-on-every-tab-switch behaviour this phase removes.
        assertSame(koin.get<TimetableViewModel>(), koin.get<TimetableViewModel>())
        assertSame(koin.get<GradesViewModel>(), koin.get<GradesViewModel>())
        assertSame(koin.get<DocumentsViewModel>(), koin.get<DocumentsViewModel>())
    }
}
