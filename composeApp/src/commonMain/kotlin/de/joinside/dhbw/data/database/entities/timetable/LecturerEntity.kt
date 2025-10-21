package de.joinside.dhbw.data.database.entities.timetable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lecturer")
data class LecturerEntity(
    @PrimaryKey(autoGenerate = true) val lecturerId: Long,
    val lecturerName: String,
)
