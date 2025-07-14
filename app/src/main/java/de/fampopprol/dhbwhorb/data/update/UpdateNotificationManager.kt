/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.fampopprol.dhbwhorb.MainActivity
import de.fampopprol.dhbwhorb.R

class UpdateNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID_UPDATE = "app_updates"
        private const val NOTIFICATION_ID_UPDATE = 3000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updateChannel = NotificationChannel(
            CHANNEL_ID_UPDATE,
            "App Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for app updates"
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(updateChannel)
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showUpdateNotification(updateInfo: UpdateInfo) {
        if (!hasNotificationPermission() || !updateInfo.isUpdateAvailable || updateInfo.release == null) {
            return
        }

        val release = updateInfo.release

        // Create intent for downloading the update
        val downloadIntent = if (release.downloadUrl.isNotEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
        } else {
            // Fallback to GitHub releases page
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/fampopprol/DHBWHorb2/releases/latest"))
        }

        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            0,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for opening the app
        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Update Available")
            .setContentText("Version ${release.tagName} is now available")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("A new version (${release.tagName}) of DHBW Horb is available!\n\nCurrent version: ${updateInfo.currentVersion}\n\nTap to download the update.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(downloadPendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Download",
                downloadPendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Later",
                openAppPendingIntent
            )
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, notification)
        } catch (e: SecurityException) {
            android.util.Log.w("UpdateNotificationManager", "Failed to show update notification: permission denied", e)
        }
    }

    fun cancelUpdateNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_UPDATE)
    }
}
