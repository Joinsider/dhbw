/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.di

import de.fampopprol.dhbwhorb.domain.session.SessionDataCleaner
import de.fampopprol.dhbwhorb.services.notifications.LectureChangeMonitor
import de.fampopprol.dhbwhorb.services.notifications.NotificationManager
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderPlanner
import de.fampopprol.dhbwhorb.services.session.AppSessionDataCleaner
import de.fampopprol.dhbwhorb.services.session.CachedFileCleaner
import de.fampopprol.dhbwhorb.services.widget.WidgetRefresher
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
            syncMetadataDao = get()
        )
    }

    single {
        NotificationManager(
            monitor = get(),
            dispatcher = get(),
            preferences = get(),
            reminders = get(),
            // Android binds a Glance refresher in :composeApp, iOS one that calls into Swift.
            // Desktop binds none, and nothing about that is a failure.
            widgetRefresher = getOrNull<WidgetRefresher>()
        )
    }

    // What logout has to undo outside the database. Resolved by the Logout use case, which lives
    // in :domain and cannot see any of these types.
    single<SessionDataCleaner> {
        AppSessionDataCleaner(
            reminders = get(),
            notifications = get(),
            widgetRefresher = getOrNull<WidgetRefresher>(),
            cachedFiles = getOrNull<CachedFileCleaner>(),
        )
    }

    // The widget reads the local cache through TimetableRepository, so a background refresh needs
    // neither a session nor a network round-trip.
    single { WidgetTimetableUseCase(repository = get()) }

    // Reminders are planned from the cache and handed to the system's own scheduler, so they
    // survive the app being closed.
    single {
        LectureReminderPlanner(
            lectureEventDao = get(),
            preferences = get(),
            scheduler = get()
        )
    }
}
