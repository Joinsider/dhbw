/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import android.app.Application
import de.fampopprol.dhbwhorb.di.androidAppModule
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.shared.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext

/**
 * Android entry point. Starts logging and the object graph — nothing else.
 *
 * Application.onCreate() runs before any Activity, Worker or widget provider in the process, so
 * everything that resolves from Koin can rely on it being ready.
 */
class DualisApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Napier.base(DebugAntilog())

        // NotificationDispatcher keeps its Context statically, because `expect class` forbids a
        // platform-specific constructor parameter. P2 moved the call that used to do this out of
        // MainActivity and did not replace it, so opening Settings crashed. Like AndroidAppContext
        // and WidgetRefreshTrigger, this is a bridge that goes away when the dispatcher becomes an
        // interface with a per-platform implementation.
        NotificationDispatcher.initialize(this)

        initKoin(extraModules = listOf(androidAppModule)) {
            androidContext(this@DualisApplication)
        }

        Napier.d("DualisApplication initialised", tag = "DualisApplication")
    }
}
