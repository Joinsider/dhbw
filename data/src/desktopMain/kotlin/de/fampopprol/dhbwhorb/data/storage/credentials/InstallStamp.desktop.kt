/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.credentials

/**
 * There is no reinstall to detect on the desktop. Removing the application leaves the user's home
 * directory and the OS keychain exactly as they were, which is indistinguishable from an update —
 * so any stamp we invented here would either never change or change for the wrong reason.
 */
actual fun currentInstallStamp(): String? = null
