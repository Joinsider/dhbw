package de.joinside.dhbw.data.database.entities.timetable.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import de.joinside.dhbw.data.database.entities.timetable.LectureEntity
import de.joinside.dhbw.data.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.database.entities.timetable.SubjectEntity
import de.joinside.dhbw.data.database.entities.timetable.LecturersToLecturesRef

data class LectureWithLecturers(
    @Embedded val lecture: LectureEntity,
    @Relation(
        parentColumn = "lectureId",
        entityColumn = "lecturerId",
        associateBy = Junction(LecturersToLecturesRef::class)
    )
    val lecturers: List<LecturerEntity>,
    @Relation(
        parentColumn = "subjectId",
        entityColumn = "subjectId"
    )
    val subject: SubjectEntity
)
