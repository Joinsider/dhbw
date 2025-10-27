package de.joinside.dhbw.data.storage.database.dao.grades

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.joinside.dhbw.data.storage.database.entities.grades.SemesterEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface SemesterDao {
    @Insert
    suspend fun insertSemester(semester: SemesterEntity)

    @Query("SELECT * FROM semester")
    fun getAllSemesters(): Flow<List<SemesterEntity>>
}