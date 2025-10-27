package de.joinside.dhbw.data.storage.database.entities.timetable.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.SubjectEntity

data class LectureWithLecturers(
    @Embedded val lecture: LectureEventEntity,
    @Relation(
        parentColumn = "lectureId",
        entityColumn = "lecturerId",
        associateBy = Junction(LectureLecturerCrossRef::class)
    )
    val lecturers: List<LecturerEntity>,
    @Relation(
        parentColumn = "subjectId",
        entityColumn = "subjectId"
    )
    val subject: SubjectEntity
)
