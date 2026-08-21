/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.di

import de.fampopprol.dhbwhorb.ui.auth.viewModel.LoginFormViewModel
import de.fampopprol.dhbwhorb.ui.documents.viewModels.DocumentsViewModel
import de.fampopprol.dhbwhorb.ui.grades.viewModels.GradesViewModel
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import org.koin.dsl.module

/**
 * ViewModels are singles, not factories: they cache loaded weeks and semesters, and recreating one
 * on every navigation is exactly the reload-on-tab-switch behaviour this refactor removes.
 * Their lifetime is the application's, like the services they wrap.
 */
val presentationModule = module {

    single {
        TimetableViewModel(
            lectureService = get(),
            lecturerDao = get(),
            lectureLecturerCrossRefDao = get()
        )
    }

    single {
        GradesViewModel(
            gradeService = get(),
            gradeDao = get()
        )
    }

    single {
        DocumentsViewModel(
            dualisDocumentService = get()
        )
    }

    factory { LoginFormViewModel() }
}
