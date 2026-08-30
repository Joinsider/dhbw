/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * [NotificationDispatcher.cancelAllDelivered] is default-empty for platforms — like desktop —
 * where a delivered notification can't be recalled. An implementation that doesn't override it
 * should be able to call it without effect.
 */
class NotificationDispatcherTest {

    private class MinimalDispatcher : NotificationDispatcher {
        override suspend fun requestPermission() = true
        override suspend fun hasPermission() = true
        override suspend fun showNotification(title: String, message: String, notificationKey: String) = Unit
        override suspend fun showSummaryNotification(title: String, message: String, changeCount: Int) = Unit
    }

    @Test
    fun cancelAllDelivered_defaultImplementation_doesNothing() = runTest {
        val dispatcher = MinimalDispatcher()
        dispatcher.cancelAllDelivered()
    }
}
