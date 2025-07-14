/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class UpdateScheduler(private val context: Context) {

    companion object {
        private const val TAG = "UpdateScheduler"
    }

    /**
     * Schedule periodic update checks every 24 hours
     */
    fun scheduleUpdateChecks() {
        val updateChecker = UpdateChecker(context)

        // Only schedule if not from Play Store
        if (updateChecker.isInstalledFromPlayStore()) {
            Log.d(TAG, "App installed from Play Store, not scheduling update checks")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateCheckRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            24, TimeUnit.HOURS, // Check every 24 hours
            2, TimeUnit.HOURS   // Flex interval of 2 hours
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            updateCheckRequest
        )

        Log.d(TAG, "Scheduled periodic update checks every 24 hours")
    }

    /**
     * Cancel periodic update checks
     */
    fun cancelUpdateChecks() {
        WorkManager.getInstance(context).cancelUniqueWork(UpdateCheckWorker.WORK_NAME)
        Log.d(TAG, "Cancelled periodic update checks")
    }

    /**
     * Trigger an immediate update check
     */
    suspend fun checkForUpdatesNow(): UpdateInfo? {
        return try {
            val updateChecker = UpdateChecker(context)
            val updateInfo = updateChecker.checkForUpdates()

            if (updateInfo.isUpdateAvailable && updateInfo.release != null) {
                val updateNotificationManager = UpdateNotificationManager(context)
                val updatePreferences = UpdatePreferencesManager(context)

                // Show notification regardless of last notified version for manual checks
                updateNotificationManager.showUpdateNotification(updateInfo)
                updatePreferences.setLastNotifiedVersion(updateInfo.latestVersion ?: "")

                Log.d(TAG, "Manual update check completed - update available: ${updateInfo.latestVersion}")
            } else {
                Log.d(TAG, "Manual update check completed - no update available")
            }

            updateInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error in manual update check", e)
            null
        }
    }

    /**
     * Check if update checks are currently scheduled
     */
    fun areUpdateChecksScheduled(): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UpdateCheckWorker.WORK_NAME)

        return try {
            val workInfoList = workInfos.get()
            workInfoList.any { !it.state.isFinished }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update schedule status", e)
            false
        }
    }
}
