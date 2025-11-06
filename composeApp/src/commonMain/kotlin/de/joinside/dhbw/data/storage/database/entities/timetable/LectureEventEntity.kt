package de.joinside.dhbw.data.storage.database.entities.timetable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "lecture",
    indices = [
        Index(value = ["lecturerId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = LecturerEntity::class,
            parentColumns = ["lecturerId"],
            childColumns = ["lecturerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LectureEventEntity(
    @PrimaryKey(autoGenerate = true) val lectureId: Long,
    val subjectName: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val location: String,
    val isTest: Boolean = false,
    val lecturerId: Long, // Foreign Key zu LecturerEntity
    val fetchedAt: LocalDateTime? = null
)