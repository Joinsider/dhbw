/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.database.entities.grades

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to track when grades for a specific semester were last fetched from the network.
 * Used to implement cache invalidation logic (e.g., refresh if data is older than 1 hour).
 */
@Entity(tableName = "grade_cache_metadata")
data class GradeCacheMetadata(
    @PrimaryKey
    val key: String, // Format: "grades_{studentId}_{semesterId}"
    val lastUpdatedTimestamp: Long, // Epoch milliseconds
    val studentId: String,
    val semesterId: String
)

