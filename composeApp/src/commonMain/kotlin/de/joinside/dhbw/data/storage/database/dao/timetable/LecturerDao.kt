package de.joinside.dhbw.data.storage.database.dao.timetable

import androidx.room.*
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LecturerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lecturer: LecturerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lecturers: List<LecturerEntity>)

    @Update
    suspend fun update(lecturer: LecturerEntity)

    @Delete
    suspend fun delete(lecturer: LecturerEntity)

    @Query("SELECT * FROM lecturer WHERE lecturerId = :id")
    suspend fun getById(id: Long): LecturerEntity?

    @Query("SELECT * FROM lecturer")
    fun getAllFlow(): Flow<List<LecturerEntity>>

    @Query("SELECT * FROM lecturer")
    suspend fun getAll(): List<LecturerEntity>

    @Query("SELECT * FROM lecturer WHERE lecturerName LIKE '%' || :searchQuery || '%'")
    suspend fun searchByName(searchQuery: String): List<LecturerEntity>

    @Query("DELETE FROM lecturer WHERE lecturerId = :id")
    suspend fun deleteById(id: Long)
}
