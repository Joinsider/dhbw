package de.joinside.dhbw.data.database.entities.grades

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grades", foreignKeys = [ForeignKey(
        entity = SemesterEntity::class,
        parentColumns = ["semesterName"],
        childColumns = ["semesterName"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index(value = ["semesterName"])] // Verbessert Query-Performance
)
data class GradesEntity(
    @PrimaryKey(autoGenerate = true) val gradesId: Long = 0,
    val name: String,
    @ColumnInfo(name = "grade_value") val gradeValue: Double,
    val semesterName: String, // Foreign Key zu Semester Tabelle
)
