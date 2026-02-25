/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.data.storage.database.dao.grades

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.joinside.dhbw.data.storage.database.entities.grades.GradeCacheMetadata

@Dao
interface GradeCacheMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: GradeCacheMetadata)

    @Query("SELECT * FROM grade_cache_metadata WHERE studentId = :studentId AND semesterId = :semesterId")
    suspend fun getMetadata(studentId: String, semesterId: String): GradeCacheMetadata?

    @Query("DELETE FROM grade_cache_metadata WHERE studentId = :studentId AND semesterId = :semesterId")
    suspend fun deleteMetadata(studentId: String, semesterId: String)

    @Query("DELETE FROM grade_cache_metadata")
    suspend fun deleteAll()
}

