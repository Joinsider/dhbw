/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shared desktop/macOS scheduler (see build.gradle.kts's `desktopAndMacosMain` source set).
 *
 * No [NotificationManager] is bound in these tests, so every run takes the "not in the graph"
 * branch in [LectureMonitorScheduler] — the same thing a platform without the full DI graph wired
 * up yet would see. What's under test here is the scheduler's own state machine: starting,
 * ignoring a second start, and stopping — not what a completed check does.
 */
class LectureMonitorSchedulerTest {

    @BeforeTest
    fun startEmptyKoin() {
        startKoin { modules(module { }) }
    }

    @AfterTest
    fun stopKoinAfterTest() {
        stopKoin()
    }

    @Test
    fun isScheduled_falseBeforeScheduling() {
        val scheduler = LectureMonitorScheduler(TestScope())
        assertFalse(scheduler.isScheduled())
    }

    @Test
    fun schedule_marksItActive_andRunsAtLeastOneCheck() = runTest {
        val scheduler = LectureMonitorScheduler(this)

        scheduler.schedule()
        assertTrue(scheduler.isScheduled())

        // Lets the launched loop reach its first checkForLectureChanges() call and suspend on the
        // 60-minute delay after it — this is what exercises runMonitoringCheck() and the "no
        // NotificationManager in the graph" branch of checkForLectureChanges().
        runCurrent()

        scheduler.cancel()
        assertFalse(scheduler.isScheduled())
    }

    @Test
    fun schedule_calledWhileAlreadyActive_doesNotStartASecondLoop() = runTest {
        val scheduler = LectureMonitorScheduler(this)

        scheduler.schedule()
        scheduler.schedule()
        runCurrent()

        assertTrue(scheduler.isScheduled())
        scheduler.cancel()
    }

    @Test
    fun cancel_beforeScheduling_isANoOp() {
        val scheduler = LectureMonitorScheduler(TestScope())
        scheduler.cancel()
        assertFalse(scheduler.isScheduled())
    }
}
