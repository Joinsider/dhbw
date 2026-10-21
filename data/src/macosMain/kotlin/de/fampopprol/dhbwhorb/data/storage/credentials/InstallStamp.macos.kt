/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.credentials

/**
 * `null`: the install guard is disabled on macOS, and no cheap stamp tells reinstall from update.
 *
 * The iOS stamp works because iOS deletes the App Group container with the last app of the group,
 * so its creation date changes on reinstall and stays put on update. macOS does not do that:
 *
 * * The sandbox container `~/Library/Containers/de.fampopprol.dhbw` survives deleting the app, so
 *   its creation date would be the same after a reinstall and the guard would never fire.
 * * The `.app` bundle's creation date changes on every update, so it would log users out on each
 *   update, which is worse than the gap it closes.
 *
 * So the data protection keychain items survive an uninstall on macOS, as they did before this
 * change. Closing that gap needs a different mechanism (for example a marker the installer writes),
 * not a timestamp.
 */
actual fun currentInstallStamp(): String? = null
