/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins which `UNAuthorizationStatus` values let a notification through. Before this rule existed,
 * the macOS dispatcher accepted only `authorized`, so a provisional grant was treated as denied.
 */
class NotificationAuthorizationTest {

    @Test
    fun grantedStatusesAllowDelivery() {
        assertTrue(NotificationAuthorization.allowsDelivery(NotificationAuthorization.AUTHORIZED))
        assertTrue(NotificationAuthorization.allowsDelivery(NotificationAuthorization.PROVISIONAL))
        assertTrue(NotificationAuthorization.allowsDelivery(NotificationAuthorization.EPHEMERAL))
    }

    @Test
    fun undecidedOrDeniedStatusesBlockDelivery() {
        assertFalse(NotificationAuthorization.allowsDelivery(NotificationAuthorization.NOT_DETERMINED))
        assertFalse(NotificationAuthorization.allowsDelivery(NotificationAuthorization.DENIED))
    }

    @Test
    fun rawValuesMatchAppleEnum() {
        // UNAuthorizationStatus in UserNotifications.h; a drift here would silently block everything.
        assertTrue(NotificationAuthorization.NOT_DETERMINED == 0L)
        assertTrue(NotificationAuthorization.DENIED == 1L)
        assertTrue(NotificationAuthorization.AUTHORIZED == 2L)
        assertTrue(NotificationAuthorization.PROVISIONAL == 3L)
        assertTrue(NotificationAuthorization.EPHEMERAL == 4L)
    }
}
