package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClassReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ClassReminderWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val reminderText = inputData.getString("reminder_text")
            val notificationId = inputData.getInt("notification_id", 0)

            if (reminderText == null) {
                Log.e(TAG, "No reminder text provided")
                return@withContext Result.failure()
            }

            // Check if class reminder notifications are still enabled
            val preferencesManager = NotificationPreferencesManager(applicationContext)
            val notificationsEnabled = preferencesManager.getNotificationsEnabledBlocking()
            val classRemindersEnabled = preferencesManager.getClassReminderNotificationsEnabledBlocking()

            if (!notificationsEnabled || !classRemindersEnabled) {
                Log.d(TAG, "Class reminder notifications are disabled, skipping")
                return@withContext Result.success()
            }

            // Show the notification
            val notificationManager = DHBWNotificationManager(applicationContext)
            notificationManager.showClassReminderNotification(reminderText, notificationId)

            Log.d(TAG, "Class reminder notification shown: $reminderText")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing class reminder notification", e)
            Result.failure()
        }
    }
}
