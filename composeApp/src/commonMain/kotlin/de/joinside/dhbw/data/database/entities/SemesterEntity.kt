package de.joinside.dhbw.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "semester")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = false) val semesterName: String
)
