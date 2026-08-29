/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.fampopprol.dhbwhorb.services.R
import io.github.aakira.napier.Napier
import androidx.core.graphics.createBitmap

/**
 * Android implementation of [NotificationDispatcher] using NotificationCompat.
 */
class AndroidNotificationDispatcher(private val context: Context) : NotificationDispatcher {

    companion object {
        private const val TAG = "NotificationDispatcher"
        private const val CHANNEL_ID = "lecture_changes"
        private const val CHANNEL_NAME = "Lecture Changes"
        private const val CHANNEL_DESCRIPTION = "Notifications for lecture time and content changes"
        private const val NOTIFICATION_ID_BASE = 10000
    }

    /**
     * Create notification channel for Android O and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }

            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Napier.d("Notification channel created", tag = TAG)
            } catch (e: Exception) {
                Napier.w("Could not create notification channel: ${e.message}", tag = TAG)
            }
        }
    }

    /**
     * Request notification permission from the user (Android 13+).
     */
    override suspend fun requestPermission(): Boolean {
        createNotificationChannel() // Ensure channel exists when requesting permission
        Napier.d("requestPermission called (returns current permission state)", tag = TAG)
        // Permission request must be initiated from an Activity
        // This method returns current permission state
        // The actual permission request should be done in the UI layer
        val cur = hasPermission()
        Napier.d("requestPermission -> current=${cur}", tag = TAG)
        return cur
    }

    /**
     * Check if notification permission is currently granted.
     */
    override suspend fun hasPermission(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Before Android 13, notifications don't require runtime permission
            true
        }
        Napier.d("hasPermission -> $granted", tag = TAG)
        return granted
    }

    /**
     * Show a notification for a single lecture change.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun showNotification(title: String, message: String, notificationKey: String) {
        if (!hasPermission()) {
            Napier.w("Cannot show notification: permission not granted", tag = TAG)
            return
        }

        try {
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_school) // small icon required
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            // Create a large icon bitmap with school emoji to resemble Icons.Default.School
            try {
                val largeIcon = createSchoolEmojiBitmap(96)
                notificationBuilder.setLargeIcon(largeIcon)
                Napier.d("Set large icon (school emoji) for notification", tag = TAG)
            } catch (e: Exception) {
                Napier.w("Failed to create large icon bitmap: ${e.message}", tag = TAG)
            }

            val notification = notificationBuilder.build()

            // Offset by one so a single change can never collide with the summary,
            // which always sits on NOTIFICATION_ID_BASE itself.
            val notificationId = NOTIFICATION_ID_BASE + 1 + (notificationKey.hashCode().mod(1000))
            NotificationManagerCompat.from(context).notify(notificationId, notification)

            Napier.d("Notification shown for $notificationKey (id=$notificationId)", tag = TAG)
        } catch (e: SecurityException) {
            Napier.e("SecurityException showing notification: ${e.message}", tag = TAG)
        }
    }

    /**
     * Show a summary notification for multiple lecture changes.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun cancelAllDelivered() {
        Napier.d("Cancelling all delivered notifications", tag = TAG)
        NotificationManagerCompat.from(context).cancelAll()
    }

    override suspend fun showSummaryNotification(title: String, message: String, changeCount: Int) {
        if (!hasPermission()) {
            Napier.w("Cannot show notification: permission not granted", tag = TAG)
            return
        }

        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_school)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setNumber(changeCount)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE, notification)

            Napier.d("Summary notification shown for $changeCount changes (id=$NOTIFICATION_ID_BASE)", tag = TAG)
        } catch (e: SecurityException) {
            Napier.e("SecurityException showing notification: ${e.message}", tag = TAG)
        }
    }

    /**
     * Create a bitmap with a school emoji to use as large icon.
     * Size is in pixels. Uses Paint drawing with emoji character.
     */
    private fun createSchoolEmojiBitmap(sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.7f
            // Use default typeface which usually supports emoji on Android
            typeface = Typeface.DEFAULT
        }

        val emoji = "\uD83C\uDFEB" // school emoji 🏫
        val bounds = Rect()
        paint.getTextBounds(emoji, 0, emoji.length, bounds)
        val x = sizePx / 2f
        val y = sizePx / 2f - bounds.exactCenterY()
        canvas.drawText(emoji, x, y, paint)

        return bitmap
    }
}
