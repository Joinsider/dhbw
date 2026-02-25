/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.services.notifications

import io.github.aakira.napier.Napier
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Toolkit

/**
 * Desktop (JVM) implementation of NotificationDispatcher using AWT SystemTray.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class NotificationDispatcher {

    companion object {
        private const val TAG = "NotificationDispatcher"
    }

    private var trayIcon: TrayIcon? = null

    init {
        Napier.d("NotificationDispatcher (desktop) instance created", tag = TAG)
        setupSystemTray()
    }

    /**
     * Setup system tray icon for notifications.
     */
    private fun setupSystemTray() {
        Napier.d("Setting up system tray (desktop)", tag = TAG)
        if (!SystemTray.isSupported()) {
            Napier.w("System tray not supported on this platform", tag = TAG)
            return
        }

        try {
            val tray = SystemTray.getSystemTray()

            // Create a simple tray icon (use a default icon for now)
            val image = Toolkit.getDefaultToolkit().createImage(ByteArray(0))
            trayIcon = TrayIcon(image, "DHBW Next").apply {
                isImageAutoSize = true
            }

            // Only add if not already added
            if (tray.trayIcons.isEmpty()) {
                tray.add(trayIcon)
            }

            Napier.d("System tray initialized (desktop)", tag = TAG)
        } catch (e: Exception) {
            Napier.e("Failed to setup system tray: ${e.message}", tag = TAG)
        }
    }

    /**
     * Request notification permission (always granted on desktop).
     */
    actual suspend fun requestPermission(): Boolean {
        val supported = SystemTray.isSupported()
        Napier.d("requestPermission (desktop) -> $supported", tag = TAG)
        return supported
    }

    /**
     * Check if notification permission is granted (always true if system tray supported).
     */
    actual suspend fun hasPermission(): Boolean {
        val supported = SystemTray.isSupported()
        Napier.d("hasPermission (desktop) -> $supported", tag = TAG)
        return supported
    }

    /**
     * Show a notification using system tray.
     */
    actual suspend fun showNotification(title: String, message: String, lectureId: Long) {
        Napier.d("showNotification (desktop) requested: lectureId=$lectureId, title='$title', message='$message'", tag = TAG)
        val icon = trayIcon
        if (icon == null) {
            Napier.w("Cannot show notification: system tray not available", tag = TAG)
            return
        }

        try {
            icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
            Napier.d("Notification shown for lecture $lectureId (desktop)", tag = TAG)
        } catch (e: Exception) {
            Napier.e("Failed to show notification: ${e.message}", tag = TAG)
        }
    }

    /**
     * Show a summary notification for multiple lecture changes.
     */
    actual suspend fun showSummaryNotification(title: String, message: String, changeCount: Int) {
        Napier.d("showSummaryNotification (desktop) requested: count=$changeCount, title='$title', message='$message'", tag = TAG)
        val icon = trayIcon
        if (icon == null) {
            Napier.w("Cannot show notification: system tray not available", tag = TAG)
            return
        }

        try {
            icon.displayMessage(title, "$message ($changeCount changes)", TrayIcon.MessageType.INFO)
            Napier.d("Summary notification shown for $changeCount changes (desktop)", tag = TAG)
        } catch (e: Exception) {
            Napier.e("Failed to show summary notification: ${e.message}", tag = TAG)
        }
    }
}
