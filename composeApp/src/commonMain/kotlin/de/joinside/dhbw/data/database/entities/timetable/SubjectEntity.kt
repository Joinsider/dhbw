package de.joinside.dhbw.data.database.entities.timetable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subject")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val subjectId: Long,
    val name: String
)
