package de.joinside.dhbw.data.database.entities.relations

import androidx.room.Embedded
import androidx.room.Relation
import de.joinside.dhbw.data.database.entities.GradesEntity
import de.joinside.dhbw.data.database.entities.SemesterEntity

data class SemesterWithGrades(
    @Embedded val semester: SemesterEntity,
    @Relation(
        parentColumn = "semesterName",
        entityColumn = "semesterName"
    )
    val grades: List<GradesEntity>
)