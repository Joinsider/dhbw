package de.joinside.dhbw.data.database.dao.timetable

import androidx.room.*
import de.joinside.dhbw.data.database.entities.timetable.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<SubjectEntity>)

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)

    @Query("SELECT * FROM subject WHERE subjectId = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subject")
    fun getAllFlow(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subject")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT * FROM subject WHERE name LIKE '%' || :searchQuery || '%'")
    suspend fun searchByName(searchQuery: String): List<SubjectEntity>

    @Query("DELETE FROM subject WHERE subjectId = :id")
    suspend fun deleteById(id: Long)
}

