/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.widget

/**
 * Lets background work in `:services` ask for a widget refresh without knowing how widgets work.
 *
 * Glance lives in `:composeApp`, which registers the implementation in its Koin module. Background
 * work resolves it optionally — a missing widget refresh must never fail the caller.
 */
interface WidgetRefresher {
    fun requestRefresh()
}
