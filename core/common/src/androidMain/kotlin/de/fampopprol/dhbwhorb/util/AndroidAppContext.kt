/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.util

import android.content.Context

/**
 * Holds the application [Context] for Android code inside the library modules.
 *
 * Extracted from `DualisApplication` during the module split: `SecureStorage.android` needs a
 * Context, but `DualisApplication` lives in `:composeApp` and depends on `:data` — reading the
 * Context from it directly would make `:data` depend on the app module and close a cycle.
 *
 * This is a service locator and shares that pattern's problems. It disappears in P2, when Koin
 * provides the Context through the platform module.
 */
object AndroidAppContext {

    @Volatile
    private var context: Context? = null

    /** Called once from `DualisApplication.onCreate()`. */
    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun requireContext(): Context = context
        ?: error("AndroidAppContext was not initialised — DualisApplication.onCreate() has to run first")

    fun contextOrNull(): Context? = context
}
