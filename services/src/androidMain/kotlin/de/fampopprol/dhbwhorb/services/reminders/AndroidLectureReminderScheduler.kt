/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.fampopprol.dhbwhorb.data.storage.settings.PlatformSettings
import io.github.aakira.napier.Napier
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

private const val TAG = "LectureReminderScheduler"

/**
 * Android holds the reminders as alarms, one per lecture.
 *
 * An alarm rather than a `WorkManager` job: work is deferrable by design and a reminder that
 * arrives twenty minutes late is worse than none. `setExactAndAllowWhileIdle` is what survives
 * Doze, and it needs `SCHEDULE_EXACT_ALARM` — which from Android 14 the user grants by hand.
 * When it is missing the alarms are set inexactly instead of not at all; [firesExactly] reports
 * which of the two is happening so the settings screen can be honest about it.
 *
 * One alarm per reminder, not a chain where each fires the next: a chain is one dropped broadcast
 * away from silence for the rest of the term.
 */
class AndroidLectureReminderScheduler(
    private val context: Context,
    private val settings: PlatformSettings,
) : LectureReminderScheduler {
    companion object {
        /**
         * The ids currently scheduled, so they can be cancelled again.
         *
         * `AlarmManager` has no way to ask what is pending, and a cancel needs the same
         * `PendingIntent` that set it — so the set has to be remembered somewhere that survives the
         * process. Losing it would leave alarms for lectures that no longer exist.
         */
        private const val SCHEDULED_IDS_KEY = "lecture_reminder_scheduled_ids"

        internal const val EXTRA_ID = "reminder_id"
        internal const val EXTRA_TITLE = "reminder_title"
        internal const val EXTRA_BODY = "reminder_body"
    }

    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override suspend fun replaceAll(reminders: List<LectureReminder>) {
        cancelScheduled()

        val exact = firesExactly()
        reminders.forEach { reminder ->
            val triggerAt = reminder.fireAt
                .toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()

            val pending = pendingIntent(reminder.id, reminder.title, reminder.body)
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // A five-minute window: late enough for the system to batch it with something
                // else, early enough that "in an hour" is still true when it arrives.
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    5 * 60 * 1000L,
                    pending,
                )
            }
        }

        settings.setString(SCHEDULED_IDS_KEY, reminders.joinToString("\n") { it.id })
        Napier.d("Scheduled ${reminders.size} reminder(s), exact=$exact", tag = TAG)
    }

    override fun firesExactly(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun cancelScheduled() {
        val ids = settings.getStringOrNull(SCHEDULED_IDS_KEY)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()

        ids.forEach { id -> alarmManager.cancel(pendingIntent(id, "", "")) }
        settings.remove(SCHEDULED_IDS_KEY)
        if (ids.isNotEmpty()) Napier.d("Cancelled ${ids.size} previously scheduled reminder(s)", tag = TAG)
    }

    /**
     * The alarm carries its own text.
     *
     * `FLAG_UPDATE_CURRENT` plus a request code derived from the id makes rescheduling the same
     * lecture replace its alarm rather than add a second one. The extras are not part of what makes
     * two `PendingIntent`s equal, which is why cancelling only needs the id.
     */
    private fun pendingIntent(id: String, title: String, body: String): PendingIntent {
        val intent = Intent(context, LectureReminderReceiver::class.java).apply {
            action = id
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
