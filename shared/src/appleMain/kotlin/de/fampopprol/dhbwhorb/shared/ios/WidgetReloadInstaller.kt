/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import org.koin.core.Koin

/**
 * Hands Swift's widget-reload closure to the Kotlin side, where background work can call it.
 *
 * Only iOS has a widget extension to reload; the macOS actual does nothing. Declared here so the
 * Swift-facing [SharedApp.setWidgetReload] can stay in appleMain with one signature.
 */
internal expect fun installWidgetReload(koin: Koin, reload: () -> Unit)
