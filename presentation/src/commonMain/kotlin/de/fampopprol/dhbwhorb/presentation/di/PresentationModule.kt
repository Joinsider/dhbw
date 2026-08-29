/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.di

import de.fampopprol.dhbwhorb.presentation.app.AppStore
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsStore
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableStore
import org.koin.dsl.module

/**
 * Stores are singles, on the application's coroutine scope.
 *
 * That is what makes switching tabs cost nothing: the store keeps the weeks and semesters it has
 * already loaded, so returning to a screen shows them without a request. The previous ViewModels
 * were singles for the same reason.
 *
 * A shorter, navigation-scoped lifetime arrives with P5, which brings the navigation graph that
 * would define such a scope. Introducing a per-screen holder now would reintroduce exactly the
 * reload-on-tab-switch this phase removes.
 */
val presentationModule = module {

    single {
        AppStore(
            sessionRepository = get(),
            logout = get(),
            purgeExpiredDocuments = get(),
            scope = get(),
            sessionScopedStores = {
                listOf(get<TimetableStore>(), get<GradesStore>(), get<DocumentsStore>())
            }
        )
    }

    single {
        AuthStore(loginWithCredentials = get(), scope = get())
    }

    single {
        TimetableStore(
            getWeekTimetable = get(),
            awaitFullWeekTimetable = get(),
            refreshTimetable = get(),
            scope = get()
        )
    }

    single {
        GradesStore(
            getAllGrades = get(),
            getModuleDetails = get(),
            computeGpa = get(),
            sessionRepository = get(),
            scope = get()
        )
    }

    single {
        DocumentsStore(
            listDocuments = get(),
            downloadDocument = get(),
            sessionRepository = get(),
            scope = get()
        )
    }

    single {
        SettingsStore(preferences = get(), scope = get())
    }
}
