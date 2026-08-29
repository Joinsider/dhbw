/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared

/**
 * Umbrella module for the Apple targets. It exports `:core:common`, `:domain`, `:data`,
 * `:services` and `:presentation` as `Shared.framework` — deliberately without `:composeApp`,
 * so the framework carries no Compose UI and SwiftUI can own the presentation layer from P7.
 *
 * The module needs at least one source file, otherwise its Kotlin/Native compilation is
 * NO-SOURCE and no framework is produced. `initKoin()` moves in here in P2 and replaces this
 * placeholder.
 */
object SharedFramework {

    /** Version of the shared layer, for diagnostics from the Swift side. */
    const val VERSION: String = "3.0.0-p1"
}
