package de.joinside.dhbw.data.storage.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Update
import de.joinside.dhbw.data.storage.database.entities.SyncMetadataEntity

@Dao
interface SyncMetadataDao {

    @Insert(onConflict = REPLACE)
    suspend fun insert(syncMetadataEntity: SyncMetadataEntity)

    @Insert(onConflict = REPLACE)
    suspend fun insertAll(syncMetadataEntities: List<SyncMetadataEntity>)

    @Update
    suspend fun update(syncMetadataEntity: SyncMetadataEntity)

    @Delete
    suspend fun delete(syncMetadataEntity: SyncMetadataEntity)

    @Query("SELECT lastSyncTimestamp FROM sync_metadata WHERE key = :key")
    suspend fun getSyncMetadata(key: String): SyncMetadataEntity?


    @Query("DELETE FROM sync_metadata")
    suspend fun clearAllSyncMetadata()

    @Query("SELECT * FROM sync_metadata")
    suspend fun getAllSyncMetadata(): List<SyncMetadataEntity>

    @Query("DELETE FROM sync_metadata WHERE key = :key")
    suspend fun deleteByKey(key: String)
}