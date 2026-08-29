package de.fampopprol.dhbwhorb.data.storage.database.entities.timetable

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "lecture"
)
data class LectureEventEntity(
    @PrimaryKey(autoGenerate = true) val lectureId: Long,
    val shortSubjectName: String,
    val fullSubjectName: String? = null, // TODO: Add tests for the fullLectureName
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val location: String,
    val isTest: Boolean = false,
    val fetchedAt: LocalDateTime? = null
) {
    // Transient field: only used for in-memory comparison, not stored in database
    @Ignore
    var lecturers: List<String>? = null
}
