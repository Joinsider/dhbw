package de.joinside.dhbw.ui.grades.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.joinside.dhbw.data.dualis.remote.services.DualisGradeService
import de.joinside.dhbw.data.storage.database.dao.grades.GradeDao
import de.joinside.dhbw.data.storage.database.entities.grades.GradeEntity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

data class GradesUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val semesters: Map<String, String> = emptyMap(), // Name -> ID
    val selectedSemesterId: String? = null,
    val grades: List<GradeEntity> = emptyList(),
    val semesterGpa: Double? = null,
    val error: String? = null
)

class GradesViewModel(
    private val gradeService: DualisGradeService,
    private val gradeDao: GradeDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "GradesViewModel"
    }

    var uiState by mutableStateOf(GradesUiState())
        private set

    init {
        loadSemesters()
    }

    fun loadSemesters() {
        uiState = uiState.copy(isLoading = true, error = null)
        coroutineScope.launch {
            try {
                Napier.d("Loading semesters...", tag = TAG)
                val result = gradeService.getSemesters()

                result.onSuccess { semesters ->
                    Napier.d("Loaded ${semesters.size} semesters", tag = TAG)
                    // Select the first semester (usually the most recent one) by default if nothing is selected
                    val defaultSemesterId = semesters.values.firstOrNull()
                    
                    uiState = uiState.copy(
                        semesters = semesters,
                        selectedSemesterId = defaultSemesterId,
                        isLoading = false
                    )

                    if (defaultSemesterId != null) {
                        loadGradesForSemester(defaultSemesterId, semesters.entries.first { it.value == defaultSemesterId }.key)
                    }
                }.onFailure { e ->
                    Napier.e("Failed to load semesters: ${e.message}", e, tag = TAG)
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "Failed to load semesters: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                Napier.e("Error loading semesters: ${e.message}", e, tag = TAG)
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun selectSemester(semesterId: String) {
        val semesterName = uiState.semesters.entries.find { it.value == semesterId }?.key ?: return
        uiState = uiState.copy(selectedSemesterId = semesterId)
        loadGradesForSemester(semesterId, semesterName)
    }

    fun refreshGrades() {
        val semesterId = uiState.selectedSemesterId ?: return
        val semesterName = uiState.semesters.entries.find { it.value == semesterId }?.key ?: return
        
        uiState = uiState.copy(isRefreshing = true)
        loadGradesForSemester(semesterId, semesterName, isRefresh = true)
    }

    private fun loadGradesForSemester(semesterId: String, semesterName: String, isRefresh: Boolean = false) {
        coroutineScope.launch {
            try {
                Napier.d("Loading grades for semester: $semesterName ($semesterId), isRefresh: $isRefresh", tag = TAG)
                
                // 1. Load from DB first if not forcing refresh
                if (!isRefresh) {
                    // Note: We assume studentId is handled implicitly or we need to get it.
                    // For now, GradeDao needs studentId. 
                    // Since we don't have easy access to studentId in VM without SessionManager,
                    // we might iterate all or rely on the service to handle the caching logic better.
                    // However, DualisGradeService.getGradesForSemester fetches from network.
                    // We should probably add a 'getGradesFromDb' method to service or use DAO directly 
                    // but we need the studentID.
                    
                    // Let's assume for this iteration we fetch from network mostly, 
                    // OR we try to fetch from DB if we knew the student ID.
                    // But wait, DualisGradeService uses the stored credentials to get studentID.
                    // Let's trust the service to do the work for now, but we want to be fast.
                    // ideally we would show cached data first.
                }

                // For now, we'll just call the service which does the network request.
                // Optimization: The service *could* return flow or we could add a DB fetch here if we had studentId.
                
                val result = gradeService.getGradesForSemester(semesterId, semesterName)
                
                result.onSuccess { grades ->
                    val gpa = calculateGpa(grades)
                    uiState = uiState.copy(
                        grades = grades,
                        semesterGpa = gpa,
                        isRefreshing = false,
                        isLoading = false,
                        error = null
                    )
                }.onFailure { e ->
                     Napier.e("Failed to load grades: ${e.message}", e, tag = TAG)
                     uiState = uiState.copy(
                        isRefreshing = false,
                        isLoading = false,
                        error = "Failed to load grades: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                 Napier.e("Error loading grades: ${e.message}", e, tag = TAG)
                 uiState = uiState.copy(
                    isRefreshing = false,
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    private fun calculateGpa(grades: List<GradeEntity>): Double? {
        var totalWeightedPoints = 0.0
        var totalCredits = 0.0

        for (grade in grades) {
            // Check if grade is numeric (e.g. "1,3")
            val gradeValueStr = grade.grade?.replace(",", ".")
            val gradeValue = gradeValueStr?.toDoubleOrNull()
            
            if (gradeValue != null && grade.credits > 0) {
                totalWeightedPoints += gradeValue * grade.credits
                totalCredits += grade.credits
            }
        }

        return if (totalCredits > 0) {
            // Round to 1 decimal place like Dualis often does, or 2.
            (totalWeightedPoints / totalCredits)
        } else {
            null
        }
    }
}
