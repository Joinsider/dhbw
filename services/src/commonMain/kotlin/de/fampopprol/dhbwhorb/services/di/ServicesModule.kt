/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.di

import de.fampopprol.dhbwhorb.services.LectureService
import de.fampopprol.dhbwhorb.services.LogoutUseCase
import de.fampopprol.dhbwhorb.services.notifications.LectureChangeMonitor
import de.fampopprol.dhbwhorb.services.notifications.NotificationManager
import de.fampopprol.dhbwhorb.services.widget.DatabaseWidgetRepository
import de.fampopprol.dhbwhorb.services.widget.WidgetLectureRepository
import de.fampopprol.dhbwhorb.services.widget.WidgetTimetableUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform-specific service bindings: the notification dispatcher everywhere, plus the background
 * scheduler on the platforms that have one.
 */
expect fun servicesPlatformModule(): Module

val servicesModule = module {

    single {
        LectureService(
            database = get(),
            dualisLectureServiceFactory = { get() }
        )
    }

    single {
        LogoutUseCase(
            sessionManager = get(),
            credentialsProvider = get(),
            database = get()
        )
    }

    single {
        LectureChangeMonitor(
            dualisLectureServiceFactory = { get() },
            lectureEventDao = get(),
            lectureLecturerCrossRefDao = get()
        )
    }

    single {
        NotificationManager(
            monitor = get(),
            dispatcher = get(),
            preferences = get()
        )
    }

    // Widget data comes straight from the database: background refreshes must not need a session
    // or a network round-trip. LectureService also implements WidgetLectureRepository but would
    // hit the network, so the database-only implementation is the bound one.
    single<WidgetLectureRepository> { DatabaseWidgetRepository(dao = get()) }
    single { WidgetTimetableUseCase(repository = get()) }
}
