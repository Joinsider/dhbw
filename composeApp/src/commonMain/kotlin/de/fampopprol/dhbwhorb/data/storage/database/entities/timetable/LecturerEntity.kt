package de.fampopprol.dhbwhorb.data.storage.database.entities.timetable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lecturer")
data class LecturerEntity(
    @PrimaryKey(autoGenerate = true) val lecturerId: Long,
    val lecturerName: String,
    val lecturerEmail: String? = null,
    val lecturerPhoneNumber: String? = null
)
