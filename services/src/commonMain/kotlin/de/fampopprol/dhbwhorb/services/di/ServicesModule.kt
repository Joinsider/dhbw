/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.di

import de.fampopprol.dhbwhorb.services.notifications.LectureChangeMonitor
import de.fampopprol.dhbwhorb.services.notifications.NotificationManager
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

    // The widget reads the local cache through TimetableRepository, so a background refresh needs
    // neither a session nor a network round-trip.
    single { WidgetTimetableUseCase(repository = get()) }
}
