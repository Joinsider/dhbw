package de.joinside.dhbw.data.storage.database.entities.timetable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table for many-to-many relationship between lectures and lecturers.
 * A lecture can have multiple lecturers, and a lecturer can teach multiple lectures.
 */
@Entity(
    tableName = "lecture_lecturer_cross_ref",
    primaryKeys = ["lectureId", "lecturerId"],
    indices = [
        Index(value = ["lectureId"]),
        Index(value = ["lecturerId"])
    ],
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
    ]
)
data class LectureLecturerCrossRef(
    val lectureId: Long,
    val lecturerId: Long
)

