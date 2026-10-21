/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

/**
 * The raw values of Apple's `UNAuthorizationStatus`, and the one decision made from them.
 *
 * Kept free of platform imports so the rule can be tested on the desktop. A notification may be
 * shown when the system has granted it outright, or granted it quietly (provisional and
 * ephemeral, which iOS and macOS use for App Clips and quiet delivery). Only `notDetermined` and
 * `denied` mean "do not show".
 */
object NotificationAuthorization {
    const val NOT_DETERMINED = 0L
    const val DENIED = 1L
    const val AUTHORIZED = 2L
    const val PROVISIONAL = 3L
    const val EPHEMERAL = 4L

    fun allowsDelivery(status: Long): Boolean =
        status == AUTHORIZED || status == PROVISIONAL || status == EPHEMERAL
}
