/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import de.fampopprol.dhbwhorb.services.widget.IosWidgetRefresher
import org.koin.core.Koin

internal actual fun installWidgetReload(koin: Koin, reload: () -> Unit) {
    koin.get<IosWidgetRefresher>().reload = reload
}
