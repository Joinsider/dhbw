/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.widget

/**
 * Lets background work in `:services` ask for a widget refresh without knowing how widgets work.
 *
 * Two implementations, both outside this module's reach: Glance lives in `:composeApp` and
 * registers itself there, and on iOS `IosWidgetRefresher` forwards to a Swift closure because
 * `WidgetCenter` has no Objective-C interface. Desktop registers nothing.
 *
 * Background work resolves it optionally — a missing widget refresh must never fail the caller.
 */
fun interface WidgetRefresher {
    fun requestRefresh()
}
