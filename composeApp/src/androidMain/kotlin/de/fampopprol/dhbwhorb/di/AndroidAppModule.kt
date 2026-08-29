/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.di

import android.content.Context
import de.fampopprol.dhbwhorb.services.widget.WidgetRefresher
import de.fampopprol.dhbwhorb.widget.sync.WidgetSyncWorker
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Bindings that only the app module can provide, because they need Glance.
 */
val androidAppModule = module {
    single<WidgetRefresher> { GlanceWidgetRefresher(androidContext()) }
}

private class GlanceWidgetRefresher(private val context: Context) : WidgetRefresher {
    override fun requestRefresh() {
        WidgetSyncWorker.enqueueImmediate(context)
    }
}
