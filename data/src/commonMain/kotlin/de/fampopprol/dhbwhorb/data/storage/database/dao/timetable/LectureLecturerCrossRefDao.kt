package de.fampopprol.dhbwhorb.data.storage.database.dao.timetable

import androidx.room.*
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureLecturerCrossRef

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

    @Query("DELETE FROM lecture_lecturer_cross_ref")
    suspend fun deleteAll()

    @Query("SELECT * FROM lecture_lecturer_cross_ref WHERE lectureId = :lectureId")
    suspend fun getByLectureId(lectureId: Long): List<LectureLecturerCrossRef>

    @Query("SELECT * FROM lecture_lecturer_cross_ref WHERE lecturerId = :lecturerId")
    suspend fun getByLecturerId(lecturerId: Long): List<LectureLecturerCrossRef>
}

