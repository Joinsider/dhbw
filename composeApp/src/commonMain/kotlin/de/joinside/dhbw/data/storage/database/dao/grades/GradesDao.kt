package de.joinside.dhbw.data.storage.database.dao.grades

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.joinside.dhbw.data.storage.database.entities.grades.GradesEntity
import de.joinside.dhbw.data.storage.database.entities.grades.relations.SemesterWithGrades
import kotlinx.coroutines.flow.Flow

@Dao
interface GradesDao {
    @Insert
    suspend fun insertGrade(grade: GradesEntity)

    @Query("SELECT * FROM grades WHERE semesterName = :semesterName")
    fun getGradesForSemester(semesterName: String): Flow<List<GradesEntity>>

    @Transaction
    @Query("SELECT * FROM semester")
    fun getAllSemestersWithGrades(): Flow<List<SemesterWithGrades>>

    @Query("DELETE FROM grades")
    suspend fun deleteAll()
}
