/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import org.koin.core.Koin

/** macOS has no widget extension, so there is nothing to reload. Kept so Swift's call compiles. */
internal actual fun installWidgetReload(koin: Koin, reload: () -> Unit) {
    // Intentionally empty.
}
