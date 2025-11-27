/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.services.notifications

import io.github.aakira.napier.Napier
import kotlin.concurrent.Volatile

/**
 * Simple service locator for NotificationManager.
 * Provides application-wide access to the notification system.
 */
object NotificationServiceLocator {
    private const val TAG = "NotificationServiceLocator"

    @Volatile
    private var notificationManager: NotificationManager? = null

    /**
     * Initialize the NotificationManager singleton.
     * Should be called once during application startup.
     */
    fun initialize(manager: NotificationManager) {
        if (notificationManager != null) {
            Napier.w("NotificationManager already initialized, overwriting", tag = TAG)
        }
        notificationManager = manager
        Napier.d("NotificationManager initialized in ServiceLocator", tag = TAG)
    }

    /**
     * Get the NotificationManager instance.
     * Throws IllegalStateException if not initialized.
     */
    fun getNotificationManager(): NotificationManager {
        return notificationManager
            ?: throw IllegalStateException(
                "NotificationManager not initialized. Call NotificationServiceLocator.initialize() first."
            )
    }

    /**
     * Check if NotificationManager is initialized.
     */
    fun isInitialized(): Boolean = notificationManager != null

    /**
     * Clear the NotificationManager (for testing or cleanup).
     */
    fun clear() {
        notificationManager = null
        Napier.d("NotificationManager cleared from ServiceLocator", tag = TAG)
    }
}

