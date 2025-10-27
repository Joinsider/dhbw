package de.joinside.dhbw.data.storage.database.entities.grades.relations

import androidx.room.Embedded
import androidx.room.Relation
import de.joinside.dhbw.data.storage.database.entities.grades.GradesEntity
import de.joinside.dhbw.data.storage.database.entities.grades.SemesterEntity

data class SemesterWithGrades(
    @Embedded val semester: SemesterEntity,
    @Relation(
        parentColumn = "semesterName",
        entityColumn = "semesterName"
    )
    val grades: List<GradesEntity>
)