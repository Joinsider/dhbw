/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.widget

import android.content.Context

/**
 * Lets background work in `:services` ask for a widget refresh without knowing how widgets work.
 *
 * The actual refresh runs through Glance and therefore lives in `:composeApp`; calling it directly
 * from here would point `:services` at the app module. `DualisApplication.onCreate()` registers
 * the implementation, which always runs before any WorkManager worker in the same process.
 *
 * Another service locator that P2 replaces with a Koin-provided interface.
 */
object WidgetRefreshTrigger {

    @Volatile
    private var handler: ((Context) -> Unit)? = null

    fun register(handler: (Context) -> Unit) {
        this.handler = handler
    }

    /** No-op when nothing is registered — a missing widget refresh must not fail the caller. */
    fun requestImmediateRefresh(context: Context) {
        handler?.invoke(context)
    }
}
