/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.credentials

/**
 * No stamp, and none needed: `dualis_secure_prefs` lives in the app's own data directory, which
 * Android deletes with the app. The one way credentials could come back is a restore from Auto
 * Backup, and that is closed in the manifest's `dataExtractionRules` / `fullBackupContent` rather
 * than here — an exclusion keeps them from ever leaving the device, which a purge at startup
 * cannot do.
 */
actual fun currentInstallStamp(): String? = null
