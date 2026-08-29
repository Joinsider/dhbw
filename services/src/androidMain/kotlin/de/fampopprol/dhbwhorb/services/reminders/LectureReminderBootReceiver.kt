/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private const val TAG = "LectureReminderBootReceiver"

/**
 * Replans the reminders after a reboot or an app update.
 *
 * Android drops every pending alarm on both, silently. Without this the reminders would stop at the
 * next restart and only come back the next time the hourly check happened to run — which, on a
 * phone that was just switched on, can be an hour of missed lectures.
 *
 * Replanning reads the cache, so it needs neither a session nor the network.
 */
class LectureReminderBootReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
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
