package de.joinside.dhbw.data.storage.database.dao.grades

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.joinside.dhbw.data.storage.database.entities.grades.GradeEntity

@Dao
interface GradeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(grade: GradeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(grades: List<GradeEntity>)

    @Query("SELECT * FROM grades WHERE studentId = :studentId AND semesterId = :semesterId")
    suspend fun getGradesForSemester(studentId: String, semesterId: String): List<GradeEntity>

    @Query("DELETE FROM grades WHERE studentId = :studentId AND semesterId = :semesterId")
    suspend fun deleteGradesForSemester(studentId: String, semesterId: String)

    @Query("DELETE FROM grades")
    suspend fun deleteAll()
}
