package de.joinside.dhbw.data.database.dao.timetable

import androidx.room.*
import de.joinside.dhbw.data.database.entities.timetable.LectureLecturerCrossRef

@Dao
interface LectureLecturerCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: LectureLecturerCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<LectureLecturerCrossRef>)

    @Delete
    suspend fun delete(crossRef: LectureLecturerCrossRef)

    @Query("DELETE FROM lecture_lecturer_cross_ref WHERE lectureId = :lectureId")
    suspend fun deleteByLectureId(lectureId: Long)

    @Query("DELETE FROM lecture_lecturer_cross_ref WHERE lecturerId = :lecturerId")
    suspend fun deleteByLecturerId(lecturerId: Long)

    @Query("DELETE FROM lecture_lecturer_cross_ref WHERE lectureId = :lectureId AND lecturerId = :lecturerId")
    suspend fun deleteByIds(lectureId: Long, lecturerId: Long)
}
