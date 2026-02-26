package de.fampopprol.dhbwhorb.ui.grades.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisGradeService
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

data class GradesUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingSemesters: Boolean = false,
    val semesters: Map<String, String> = emptyMap(), // Name -> ID
    val selectedSemesterId: String? = null,
    val grades: List<GradeEntity> = emptyList(),
    val semesterGpa: Double? = null,
    val overallGpa: Double? = null, // GPA across all semesters
    val totalCreditsEarned: Double = 0.0,
    val error: String? = null,
    val isDataFromCache: Boolean = false,
    val requiresLogin: Boolean = false
)

// Special semester ID to indicate "All Semesters" view
const val ALL_SEMESTERS_ID = "ALL_SEMESTERS_VIEW"

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
        // If we cannot load due to missing credentials/session, set requiresLogin and stop
        if (!gradeService.hasCredentialsOrSession()) {
            Napier.d("Skipping loadSemesters: not authenticated and no stored credentials", tag = TAG)
            uiState = uiState.copy(
                isLoadingSemesters = false,
                isLoading = false,
                isRefreshing = false,
                error = null,
                requiresLogin = true
            )
            return
        }

        uiState = uiState.copy(isLoadingSemesters = true, error = null, requiresLogin = false)
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
                        isLoadingSemesters = false
                    )

                    if (defaultSemesterId != null) {
                        loadGradesForSemester(defaultSemesterId, semesters.entries.first { it.value == defaultSemesterId }.key)
                    }
                }.onFailure { e ->
                    Napier.e("Failed to load semesters: ${e.message}", e, tag = TAG)
                    uiState = uiState.copy(
                        isLoadingSemesters = false,
                        isLoading = false,
                        error = "Failed to load semesters: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                Napier.e("Error loading semesters: ${e.message}", e, tag = TAG)
                uiState = uiState.copy(
                    isLoadingSemesters = false,
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun selectSemester(semesterId: String) {
        uiState = uiState.copy(selectedSemesterId = semesterId)

        if (semesterId == ALL_SEMESTERS_ID) {
            loadAllGrades()
        } else {
            val semesterName = uiState.semesters.entries.find { it.value == semesterId }?.key ?: return
            loadGradesForSemester(semesterId, semesterName)
        }
    }

    private fun loadAllGrades(forceRefresh: Boolean = false) {
        if (!gradeService.hasCredentialsOrSession()) {
            Napier.d("Skipping loadAllGrades: login required", tag = TAG)
            uiState = uiState.copy(isLoading = false, isRefreshing = false, requiresLogin = true)
            return
        }
        uiState = uiState.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh)
        coroutineScope.launch {
            try {
                Napier.d("Loading grades for all semesters (forceRefresh: $forceRefresh)", tag = TAG)
                val allGrades = mutableListOf<GradeEntity>()

                // Load grades for each semester
                for ((semesterName, semesterId) in uiState.semesters) {
                    val result = gradeService.getGradesForSemester(
                        semesterId = semesterId,
                        semesterName = semesterName,
                        forceRefresh = forceRefresh
                    )
                    result.onSuccess { grades ->
                        allGrades.addAll(grades)
                    }.onFailure { e ->
                        Napier.w("Failed to load grades for $semesterName: ${e.message}", tag = TAG)
                    }
                }

                // Calculate overall statistics
                val overallGpa = calculateGpa(allGrades)
                val totalCredits = allGrades.filter { it.grade != null }.sumOf { it.credits }

                uiState = uiState.copy(
                    grades = allGrades.sortedWith(
                        compareByDescending<GradeEntity> { it.semesterName }
                            .thenBy { it.moduleName }
                    ),
                    overallGpa = overallGpa,
                    semesterGpa = null, // Clear single semester GPA
                    totalCreditsEarned = totalCredits,
                    isLoading = false,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                Napier.e("Error loading all grades: ${e.message}", e, tag = TAG)
                uiState = uiState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun refreshGrades() {
        if (!gradeService.hasCredentialsOrSession()) {
            Napier.d("Skipping refreshGrades: login required", tag = TAG)
            uiState = uiState.copy(isRefreshing = false, requiresLogin = true)
            return
        }
        val semesterId = uiState.selectedSemesterId ?: return

        Napier.d("Force refreshing grades (pull-to-refresh)", tag = TAG)

        if (semesterId == ALL_SEMESTERS_ID) {
            loadAllGrades(forceRefresh = true)
        } else {
            val semesterName = uiState.semesters.entries.find { it.value == semesterId }?.key ?: return
            loadGradesForSemester(semesterId, semesterName, isRefresh = true)
        }
    }

    private fun loadGradesForSemester(semesterId: String, semesterName: String, isRefresh: Boolean = false) {
        if (!gradeService.hasCredentialsOrSession()) {
            Napier.d("Skipping loadGradesForSemester: login required", tag = TAG)
            uiState = uiState.copy(isLoading = false, isRefreshing = false, requiresLogin = true)
            return
        }
        // Set loading state appropriately
        uiState = if (isRefresh) {
            uiState.copy(isRefreshing = true)
        } else {
            uiState.copy(isLoading = true)
        }

        coroutineScope.launch {
            try {
                Napier.d("Loading grades for semester: $semesterName ($semesterId), isRefresh: $isRefresh", tag = TAG)
                
                // Call the service with forceRefresh flag
                // When isRefresh is true (pull-to-refresh), we force reload from network
                val result = gradeService.getGradesForSemester(
                    semesterId = semesterId,
                    semesterName = semesterName,
                    forceRefresh = isRefresh
                )

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
