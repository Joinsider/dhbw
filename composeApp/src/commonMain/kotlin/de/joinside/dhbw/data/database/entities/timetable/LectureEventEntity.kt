package de.joinside.dhbw.data.database.entities.timetable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "lecture",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subjectId"])
    ]
)
data class LectureEventEntity(
    @PrimaryKey(autoGenerate = true) val lectureId: Long,
    val subjectId: Long, // Foreign Key zu Subject Tabelle
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val location: String,
    val isTest: Boolean = false
)
