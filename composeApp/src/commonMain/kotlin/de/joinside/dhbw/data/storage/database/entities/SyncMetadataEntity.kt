package de.joinside.dhbw.data.storage.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["key"],
    indices = [
        Index(value = ["key"])
    ]
)
data class SyncMetadataEntity(
    @PrimaryKey(autoGenerate = false) val key: String, // "grades", "timetable", "exams", "notifications"
    val lastSyncTimestamp: Long
)