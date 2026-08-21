/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import android.app.Application
import de.fampopprol.dhbwhorb.di.androidAppModule
import de.fampopprol.dhbwhorb.presentation.di.presentationModule
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

        initKoin(extraModules = listOf(presentationModule, androidAppModule)) {
            androidContext(this@DualisApplication)
        }

        Napier.d("DualisApplication initialised", tag = "DualisApplication")
    }
}
