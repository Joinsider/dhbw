/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private const val TAG = "LectureReminderReceiver"

/**
 * Shows one reminder, from what the alarm carried.
 *
 * Deliberately reads nothing: no database, no preferences, no session. A broadcast receiver has a
 * few seconds and no guarantee that anything else in the app is alive, so everything it needs was
 * decided when the reminder was planned.
 */
class LectureReminderReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AndroidLectureReminderScheduler.EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(AndroidLectureReminderScheduler.EXTRA_BODY).orEmpty()
        val id = intent.getStringExtra(AndroidLectureReminderScheduler.EXTRA_ID).orEmpty()
        if (title.isEmpty()) {
            Napier.w("Reminder alarm without a title, ignoring", tag = TAG)
            return
        }

        // goAsync() keeps the receiver alive across the suspension; the dispatcher only builds and
        // posts a notification, so this is a matter of milliseconds.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                getKoin().get<NotificationDispatcher>().showNotification(title, body, id)
                Napier.d("Reminder shown: $id", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Could not show reminder $id: ${e.message}", e, tag = TAG)
            } finally {
                pending.finish()
            }
        }
    }
}
