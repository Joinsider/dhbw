/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.Intent.ACTION_MY_PACKAGE_REPLACED
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private const val TAG = "LectureReminderBootReceiver"

/** Every action is a protected broadcast: only the system can send it. */
private val HANDLED_ACTIONS = setOf(
    ACTION_BOOT_COMPLETED,
    ACTION_MY_PACKAGE_REPLACED,
    ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
)

/**
 * Replans the reminders after a reboot, an app update, or exact alarms being allowed.
 *
 * Android drops every pending alarm on both, silently. Without this the reminders would stop at the
 * next restart and only come back the next time the hourly check happened to run — which, on a
 * phone that was just switched on, can be an hour of missed lectures.
 *
 * Allowing exact alarms is the third case: the reminders planned before were set inexactly, and
 * they would stay that way until the next hourly check replanned them. The system sends
 * [ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED] only when the permission is granted, not
 * when it is taken away (that kills the app's alarms itself).
 *
 * Replanning reads the cache, so it needs neither a session nor the network.
 */
class LectureReminderBootReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        // This receiver is exported so the system can deliver BOOT_COMPLETED, but exported also
        // means any app could in principle send it an intent — every action handled here is a
        // protected broadcast only the system can send, and this check rejects anything else
        // rather than trusting the action a caller claims.
        if (intent.action !in HANDLED_ACTIONS) {
            Napier.w("Ignoring unexpected intent action: ${intent.action}", tag = TAG)
            return
        }
        Napier.d("Replanning reminders after ${intent.action}", tag = TAG)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                getKoin().get<LectureReminderPlanner>().reschedule()
            } catch (e: Exception) {
                Napier.e("Could not replan reminders: ${e.message}", e, tag = TAG)
            } finally {
                pending.finish()
            }
        }
    }
}
