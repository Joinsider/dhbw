package de.joinside.dhbw.data.storage.database.entities.timetable

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Data class representing a lecture with its associated lecturers.
 * Uses Room's @Relation with @Junction to automatically join lecturers through the junction table.
 * Supports many-to-many relationship: one lecture can have multiple lecturers.
 */
data class LectureWithLecturers(
    @Embedded val lecture: LectureEventEntity,
    @Relation(
        parentColumn = "lectureId",
        entityColumn = "lecturerId",
        associateBy = Junction(LectureLecturerCrossRef::class)
    )
    val lecturers: List<LecturerEntity>
)



