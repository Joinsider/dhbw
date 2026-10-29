/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
import de.fampopprol.dhbwhorb.testutil.TestPlatformSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [AppBadge] on its own, and [BadgingNotificationDispatcher] as every caller sees it: the badge
 * counts what goes out without any caller asking it to.
 */
class AppBadgeTest {

    private class RecordingDisplay : BadgeDisplay {
        val shown = mutableListOf<Int>()
        override suspend fun show(count: Int) {
            shown += count
        }
    }

    private class RecordingDispatcher(private val permission: Boolean = true) : NotificationDispatcher {
        var singles = 0
        var summaries = 0
        var cancelled = 0
        override suspend fun requestPermission() = permission
        override suspend fun hasPermission() = permission
        override suspend fun showNotification(title: String, message: String, notificationKey: String) {
            if (permission) singles++
        }
        override suspend fun showSummaryNotification(title: String, message: String, changeCount: Int) {
            if (permission) summaries++
        }
        override suspend fun cancelAllDelivered() {
            cancelled++
        }
    }

    private lateinit var platformSettings: TestPlatformSettings
    private lateinit var display: RecordingDisplay

    private fun store() = NotificationPreferencesInteractor(
        NotificationPreferences(SettingsStorage(platformSettings, FakeSecureStorage()))
    )

    private fun badge() = AppBadge(store(), display)

    @BeforeTest
    fun setup() {
        platformSettings = TestPlatformSettings()
        display = RecordingDisplay()
    }

    @Test
    fun add_accumulatesAndShowsTheTotal() = runTest {
        val badge = badge()

        badge.add()
        badge.add(3)

        assertEquals(4, badge.count())
        assertEquals(listOf(1, 4), display.shown)
    }

    @Test
    fun add_nothing_leavesTheIconAlone() = runTest {
        val badge = badge()

        badge.add(0)

        assertEquals(0, badge.count())
        assertEquals(emptyList(), display.shown)
    }

    @Test
    fun clear_resetsTheCountAndTheIcon() = runTest {
        val badge = badge()
        badge.add(2)

        badge.clear()

        assertEquals(0, badge.count())
        assertEquals(0, display.shown.last())
    }

    @Test
    fun count_survivesANewInstance_likeTheNextLaunch() = runTest {
        badge().add(2)

        assertEquals(2, badge().count(), "the hourly check may run in a process of its own")
    }

    @Test
    fun add_concurrently_losesNothing() = runTest {
        val badge = badge()

        List(20) { async { yield(); badge.add() } }.awaitAll()

        assertEquals(20, badge.count())
    }

    @Test
    fun dispatcher_countsSinglesAndSummaries_withoutTheCallerAsking() = runTest {
        val badge = badge()
        val dispatcher = BadgingNotificationDispatcher(RecordingDispatcher(), badge)

        dispatcher.showNotification("t", "m", "key")
        dispatcher.showSummaryNotification("t", "m", changeCount = 3)

        assertEquals(4, badge.count())
    }

    @Test
    fun dispatcher_withoutPermission_countsNothing() = runTest {
        val badge = badge()
        val dispatcher = BadgingNotificationDispatcher(RecordingDispatcher(permission = false), badge)

        dispatcher.showNotification("t", "m", "key")
        dispatcher.showSummaryNotification("t", "m", changeCount = 3)

        assertEquals(0, badge.count(), "a notification the system refused was never seen")
    }

    @Test
    fun dispatcher_cancelAllDelivered_clearsTheBadge() = runTest {
        val badge = badge()
        val platform = RecordingDispatcher()
        val dispatcher = BadgingNotificationDispatcher(platform, badge)
        dispatcher.showNotification("t", "m", "key")

        dispatcher.cancelAllDelivered()

        assertEquals(1, platform.cancelled)
        assertEquals(0, badge.count(), "logout takes the previous user's count with it")
    }
}
