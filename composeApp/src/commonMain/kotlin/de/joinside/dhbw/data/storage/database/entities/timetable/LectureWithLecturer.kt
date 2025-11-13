package de.joinside.dhbw.data.storage.database.entities.timetable

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Data class representing a lecture with its associated lecturer.
 * Uses Room's @Relation to automatically join the lecturer data.
 */
data class LectureWithLecturer(
    @Embedded val lecture: LectureEventEntity,
    @Relation(
        parentColumn = "lecturerId",
        entityColumn = "lecturerId"
    )
    val lecturer: LecturerEntity?
)

