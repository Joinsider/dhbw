package de.joinside.dhbw.data.storage.database.dao.timetable

import androidx.room.*
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lecture: LectureEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lectures: List<LectureEventEntity>)

    @Update
    suspend fun update(lecture: LectureEventEntity)

    @Delete
    suspend fun delete(lecture: LectureEventEntity)

    @Query("SELECT * FROM lecture WHERE lectureId = :id")
    suspend fun getById(id: Long): LectureEventEntity?

    @Query("SELECT * FROM lecture")
    fun getAllFlow(): Flow<List<LectureEventEntity>>

    @Query("SELECT * FROM lecture")
    suspend fun getAll(): List<LectureEventEntity>

    @Query("DELETE FROM lecture WHERE lectureId = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM lecture")
    suspend fun deleteAll()
}