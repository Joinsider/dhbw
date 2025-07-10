package de.fampopprol.dhbwhorb.data.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UpdateCheckWorker"
        const val WORK_NAME = "update_check"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting update check")

            val updatePreferences = UpdatePreferencesManager(applicationContext)

            // Check if user has enabled update checks
            if (!updatePreferences.isUpdateCheckEnabledBlocking()) {
                Log.d(TAG, "Update checking is disabled by user")
                return@withContext Result.success()
            }

            val updateChecker = UpdateChecker(applicationContext)
            val updateNotificationManager = UpdateNotificationManager(applicationContext)

            // Check for updates
            val updateInfo = updateChecker.checkForUpdates()

            if (updateInfo.isUpdateAvailable && updateInfo.release != null) {
                Log.d(TAG, "Update available: ${updateInfo.currentVersion} -> ${updateInfo.latestVersion}")

                // Check if we should show notification for this version
                val lastNotifiedVersion = updatePreferences.getLastNotifiedVersionBlocking()
                if (lastNotifiedVersion != updateInfo.latestVersion) {
                    updateNotificationManager.showUpdateNotification(updateInfo)
                    updatePreferences.setLastNotifiedVersionBlocking(updateInfo.latestVersion ?: "")
                    Log.d(TAG, "Update notification shown for version ${updateInfo.latestVersion}")
                } else {
                    Log.d(TAG, "User already notified about version ${updateInfo.latestVersion}")
                }
            } else {
                Log.d(TAG, "No update available")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in update check worker", e)
            Result.retry()
        }
    }
}
