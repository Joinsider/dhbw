package de.joinside.dhbw.data.database.entities.timetable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "lecture_lecturer_cross_ref",
    primaryKeys = ["lectureId", "lecturerId"],
    foreignKeys = [
        ForeignKey(
            entity = LectureEventEntity::class,
            parentColumns = ["lectureId"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LecturerEntity::class,
            parentColumns = ["lecturerId"],
            childColumns = ["lecturerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["lectureId"]),
        Index(value = ["lecturerId"])
    ]
)
data class LectureLecturerCrossRef(
    val lectureId: Long,
    val lecturerId: Long
)
