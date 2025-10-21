package de.joinside.dhbw.data.database.entities.timetable

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "lecturers_to_lectures_ref",
    primaryKeys = ["lectureId", "lecturerId"],
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["lectureId"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        ), ForeignKey(
            entity = LecturerEntity::class,
            parentColumns = ["lecturerId"],
            childColumns = ["lecturerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LecturersToLecturesRef(
    val lectureId: Long,
    val lecturerId: Long
)