package de.fampopprol.dhbwhorb.data.storage.database.entities.grades

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grades",
    indices = [
        Index(value = ["studentId", "semesterId", "moduleNumber"], unique = true)
    ]
)
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: String,
    val semesterId: String,
    val semesterName: String,
    val moduleNumber: String,
    val moduleName: String,
    val grade: String?,
    val credits: Double,
    val status: String?
)
