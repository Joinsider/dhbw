/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

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
        // This receiver is exported so the system can deliver BOOT_COMPLETED, but exported also
        // means any app could in principle send it an intent — both actions handled here are
        // protected broadcasts only the system can send, and this check rejects anything else
        // rather than trusting the action a caller claims.
        if (intent.action != ACTION_BOOT_COMPLETED && intent.action != ACTION_MY_PACKAGE_REPLACED) {
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
