/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.database.dao.documents

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.fampopprol.dhbwhorb.data.storage.database.entities.documents.CachedDocumentEntity

@Dao
interface CachedDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: CachedDocumentEntity)

    @Query("SELECT * FROM cached_documents WHERE downloadUrl = :downloadUrl")
    suspend fun get(downloadUrl: String): CachedDocumentEntity?

    @Query("DELETE FROM cached_documents WHERE downloadUrl = :downloadUrl")
    suspend fun delete(downloadUrl: String)

    /**
     * Deletes everything cached at or before [cutoffTimestamp].
     *
     * "At or before", not "before": the read path treats an age of exactly the maximum as expired,
     * and the two have to agree on what four weeks old means.
     *
     * @return how many documents were dropped, for the log line.
     */
    @Query("DELETE FROM cached_documents WHERE cachedAtTimestamp <= :cutoffTimestamp")
    suspend fun deleteCachedAtOrBefore(cutoffTimestamp: Long): Int

    @Query("DELETE FROM cached_documents")
    suspend fun deleteAll()
}
