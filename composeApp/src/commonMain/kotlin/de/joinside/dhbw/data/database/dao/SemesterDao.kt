package de.joinside.dhbw.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.joinside.dhbw.data.database.entities.grades.SemesterEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface SemesterDao {
    @Insert
    suspend fun insertSemester(semester: SemesterEntity)

    @Query("SELECT * FROM semester")
    fun getAllSemesters(): Flow<List<SemesterEntity>>
}