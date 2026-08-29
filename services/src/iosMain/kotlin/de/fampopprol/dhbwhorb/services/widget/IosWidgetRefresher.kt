/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.widget

import io.github.aakira.napier.Napier

private const val TAG = "IosWidgetRefresher"

/**
 * Lets background work ask for a widget refresh on iOS.
 *
 * `WidgetCenter` is Swift-only — it has no Objective-C interface, so Kotlin cannot call it at all,
 * and this was the one thing the background check could do on Android but not here. The way around
 * it is the same one `UIRootViewControllerHelper` uses: Swift hands a closure down at start-up and
 * Kotlin calls it when it needs to.
 *
 * Doing nothing is a valid state. The widget extension starts this graph too and has no business
 * reloading anything, and until Swift has installed [reload] there is nothing to reload for.
 */
class IosWidgetRefresher : WidgetRefresher {

    /** Set once from `iOSApp.init()`. */
    var reload: (() -> Unit)? = null

    override fun requestRefresh() {
        val reload = reload
        if (reload == null) {
            Napier.d("No widget reload installed — nothing to refresh", tag = TAG)
            return
        }
        Napier.d("Asking WidgetKit to reload", tag = TAG)
        reload()
    }
}
