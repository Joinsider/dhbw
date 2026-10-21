/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LectureCheckTest {

    /**
     * The outcome is persisted by its enum name (see NotificationPreferences). Renaming a constant
     * would silently turn every stored check into "unreadable", so the names are pinned here.
     */
    @Test
    fun outcomeNames_areThePersistedFormat() {
        assertEquals(listOf("SUCCESS", "OFFLINE", "FAILED"), LectureCheckOutcome.entries.map { it.name })
    }

    @Test
    fun equality_isByTimeAndOutcome() {
        assertEquals(
            LectureCheck(1L, LectureCheckOutcome.SUCCESS),
            LectureCheck(1L, LectureCheckOutcome.SUCCESS),
        )
    }
}
