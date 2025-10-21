package de.joinside.dhbw.data.database.dao.timetable

import androidx.room.*
import de.joinside.dhbw.data.database.entities.timetable.LectureEntity
import de.joinside.dhbw.data.database.entities.timetable.relations.LectureWithLecturers
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lecture: LectureEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lectures: List<LectureEntity>)

    @Update
    suspend fun update(lecture: LectureEntity)

    @Delete
    suspend fun delete(lecture: LectureEntity)

    @Query("SELECT * FROM lecture WHERE lectureId = :id")
    suspend fun getById(id: Long): LectureEntity?

    @Query("SELECT * FROM lecture")
    fun getAllFlow(): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lecture")
    suspend fun getAll(): List<LectureEntity>

    @Transaction
    @Query("SELECT * FROM lecture WHERE lectureId = :id")
    suspend fun getWithLecturers(id: Long): LectureWithLecturers?

    @Transaction
    @Query("SELECT * FROM lecture")
    fun getAllWithLecturersFlow(): Flow<List<LectureWithLecturers>>

    @Query("SELECT * FROM lecture WHERE subjectId = :subjectId")
    suspend fun getBySubject(subjectId: Long): List<LectureEntity>

    @Query("DELETE FROM lecture WHERE lectureId = :id")
    suspend fun deleteById(id: Long)
}