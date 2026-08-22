/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.credentials

import de.fampopprol.dhbwhorb.data.storage.database.IOS_APP_GROUP_IDENTIFIER
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSFileCreationDate
import platform.Foundation.NSFileManager
import platform.Foundation.timeIntervalSince1970

private const val TAG = "InstallStamp"

/**
 * When the App Group container was created — which is when the app was installed.
 *
 * iOS creates the container on install and deletes it with the last app of the group, so its
 * creation date is stable across updates and new after a reinstall. That is the whole requirement.
 *
 * **The App Group and not the app's own home directory**, even though the app's own one would be
 * simpler to reach: the widget extension runs [currentInstallStamp] too — it starts the same object
 * graph — and it has a *different* home directory. Comparing against that would make the extension
 * decide, every single time it renders, that the credentials came from another installation.
 *
 * `null` when the container is unavailable, which on a real device means the App Group is not
 * registered in the developer portal yet. Nothing is purged then; a guard that cannot tell must not
 * guess.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun currentInstallStamp(): String? {
    val container = NSFileManager.defaultManager
        .containerURLForSecurityApplicationGroupIdentifier(IOS_APP_GROUP_IDENTIFIER)
        ?.path
        ?: run {
            Napier.w("No app group container — install guard disabled", tag = TAG)
            return null
        }

    val created = NSFileManager.defaultManager
        .attributesOfItemAtPath(container, error = null)
        ?.get(NSFileCreationDate) as? NSDate
        ?: run {
            Napier.w("App group container has no creation date — install guard disabled", tag = TAG)
            return null
        }

    return created.timeIntervalSince1970.toString()
}
