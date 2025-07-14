/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Device booted or app updated, restarting notifications")

                // Use coroutine scope to handle async operations
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val preferencesManager = NotificationPreferencesManager(context)
                        val notificationsEnabled = preferencesManager.getNotificationsEnabledBlocking()

                        if (notificationsEnabled) {
                            val notificationScheduler = NotificationScheduler(context)
                            notificationScheduler.schedulePeriodicNotifications()
                            Log.d(TAG, "Notifications restarted successfully")
                        } else {
                            Log.d(TAG, "Notifications are disabled, not restarting")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restarting notifications", e)
                    }
                }
            }
        }
    }
}
