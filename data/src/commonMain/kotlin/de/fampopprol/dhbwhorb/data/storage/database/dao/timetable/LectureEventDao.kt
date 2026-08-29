package de.fampopprol.dhbwhorb.data.storage.database.dao.timetable

import androidx.room.*
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
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

    @Transaction
    @Query("SELECT * FROM lecture WHERE lectureId = :id")
    suspend fun getByIdWithLecturers(id: Long): LectureWithLecturers?

    @Transaction
    @Query("SELECT * FROM lecture")
    suspend fun getAllWithLecturers(): List<LectureWithLecturers>

    @Transaction
    @Query("SELECT * FROM lecture")
    fun getAllWithLecturersFlow(): Flow<List<LectureWithLecturers>>

    @Query("DELETE FROM lecture WHERE lectureId = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM lecture")
    suspend fun deleteAll()

    @Query("DELETE FROM lecture WHERE startTime >= :start AND endTime <= :end")
    suspend fun deleteInRange(start: kotlinx.datetime.LocalDateTime, end: kotlinx.datetime.LocalDateTime)

    /**
     * Drops lectures that ended before [cutoff].
     *
     * Nothing used to remove anything from this table, so it grew by every week the user ever
     * paged to and kept them forever. A timetable from two months ago answers no question anyone
     * asks; it only costs disk and makes every full scan slower.
     */
    @Query("DELETE FROM lecture WHERE endTime < :cutoff")
    suspend fun deleteEndedBefore(cutoff: kotlinx.datetime.LocalDateTime)
}