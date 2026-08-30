/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
import de.fampopprol.dhbwhorb.testutil.MockLectureEventDao
import de.fampopprol.dhbwhorb.testutil.TestPlatformSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `goAsync()`'s `PendingResult` only behaves like the real thing under Robolectric — a plain JVM
 * unit test sees the unimplemented android.jar stub and NPEs.
 */
@RunWith(RobolectricTestRunner::class)
class LectureReminderBootReceiverTest {

    private class FakeScheduler : LectureReminderScheduler {
        val replaceAllCalls = mutableListOf<List<LectureReminder>>()
        override suspend fun replaceAll(reminders: List<LectureReminder>) {
            replaceAllCalls += reminders
        }
        override fun firesExactly(): Boolean = true
    }

    private lateinit var scheduler: FakeScheduler

    @BeforeTest
    fun setUpKoin() {
        scheduler = FakeScheduler()
        val preferences = NotificationPreferencesInteractor(
            NotificationPreferences(SettingsStorage(TestPlatformSettings(), FakeSecureStorage()))
        )
        val planner = LectureReminderPlanner(
            lectureEventDao = MockLectureEventDao(),
            preferences = preferences,
            scheduler = scheduler,
        )
        startKoin { modules(module { single { planner } }) }
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    private fun awaitReschedule() {
        // onReceive launches the real replan on Dispatchers.Default and returns immediately —
        // poll briefly rather than assert synchronously.
        val latch = CountDownLatch(1)
        Thread {
            while (scheduler.replaceAllCalls.isEmpty()) Thread.sleep(5)
            latch.countDown()
        }.start()
        assertTrue(latch.await(2, TimeUnit.SECONDS), "reschedule() never ran")
    }

    @Test
    fun onReceive_bootCompleted_replansReminders() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver = LectureReminderBootReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        awaitReschedule()

        // Notifications are off by default, so a replan means "clear everything".
        assertEquals(listOf(emptyList<LectureReminder>()), scheduler.replaceAllCalls)
    }

    @Test
    fun onReceive_myPackageReplaced_replansReminders() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver = LectureReminderBootReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        awaitReschedule()

        assertEquals(1, scheduler.replaceAllCalls.size)
    }

    @Test
    fun onReceive_unexpectedAction_isIgnored() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver = LectureReminderBootReceiver()

        receiver.onReceive(context, Intent("com.evil.NOT_A_PROTECTED_BROADCAST"))

        assertTrue(scheduler.replaceAllCalls.isEmpty(), "an unrecognised action must not trigger a replan")
    }
}
