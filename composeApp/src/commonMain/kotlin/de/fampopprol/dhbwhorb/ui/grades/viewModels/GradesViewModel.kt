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
import kotlinx.coroutines.Mutex
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val gradeService: DualisGradeService?,
    private val gradeDao: GradeDao?,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "GradesViewModel"
    }

    var uiState by mutableStateOf(GradesUiState())
        private set

    // Race condition prevention: Mutex serializes all load operations
    private val loadMutex = Mutex()

    // Three separate StateFlows for deterministic loading state
    private val _isLoading = MutableStateFlow<Boolean>(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _data = MutableStateFlow<GradesUiState?>(null)
    val data: StateFlow<GradesUiState?> = _data

    private val _isRefreshing = MutableStateFlow<Boolean>(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        loadSemesters()
    }

    /**
     * Retries a database or network operation for up to 5 seconds.
     * This ensures that if services are still initializing in the background,
     * the ViewModel will eventually get the data once they are ready.
     * 
     * Retry Strategy:
     * - Max attempts: 5
     * - Delay between attempts: 1 second
     * - Total duration: ~5 seconds
     */
    private suspend fun <T> getDataWithRetry(
        actionName: String,
        block: suspend () -> T?
    ): T? {
        val maxAttempts = 5
        val delayMillis = 1000L
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                // Check if services are ready (not null)
                // This is specifically for Task 1.2 and 2.3 requirements
                val result = block()
                if (result != null) return result
                
                Napier.d("Attempt $attempt for $actionName returned null (service might not be ready), retrying...", tag = TAG)
            } catch (e: Exception) {
                lastException = e
                Napier.w("Attempt $attempt for $actionName failed: ${e.message}", tag = TAG)
            }

            if (attempt < maxAttempts) {
                delay(delayMillis)
            }
        }

        Napier.e("All $maxAttempts attempts failed for $actionName. Last error: ${lastException?.message}", tag = TAG)
        return null
    }

    /**
     * Cleanup resources and cancel coroutine scope.
     */
    fun cleanup() {
        Napier.d("Cleaning up GradesViewModel", tag = TAG)
        coroutineScope.cancel()
    }

    fun loadSemesters() {
        uiState = uiState.copy(isLoadingSemesters = true, error = null, requiresLogin = false)
        
        coroutineScope.launch {
            try {
                // Use retry logic to wait for gradeService if it's not ready yet
                val service = getDataWithRetry("Grades Service Availability") {
                    gradeService
                }

                if (service == null) {
                    Napier.e("GradeService not ready after retry", tag = TAG)
                    uiState = uiState.copy(
                        isLoadingSemesters = false,
                        error = "Grades service not available. Please try again later."
                    )
                    return@launch
                }

                // If we cannot load due to missing credentials/session, set requiresLogin and stop
                if (!service.hasCredentialsOrSession()) {
                    Napier.d("Skipping loadSemesters: not authenticated and no stored credentials", tag = TAG)
                    uiState = uiState.copy(
                        isLoadingSemesters = false,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        requiresLogin = true
                    )
                    return@launch
                }

                Napier.d("Loading semesters...", tag = TAG)
                val result = service.getSemesters()

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
        uiState = uiState.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh)
        
        coroutineScope.launch {
            loadMutex.withLock {
                if (forceRefresh) {
                    _isRefreshing.value = true
                } else {
                    _isLoading.value = true
                }

                try {
                    // Use retry logic to wait for gradeService
                    val service = getDataWithRetry("Grades Service Availability (All)") {
                        gradeService
                    }

                    if (service == null) {
                        uiState = uiState.copy(isLoading = false, isRefreshing = false, error = "Service not ready")
                        return@loadMutex
                    }

                    if (!service.hasCredentialsOrSession()) {
                        Napier.d("Skipping loadAllGrades: login required", tag = TAG)
                        uiState = uiState.copy(isLoading = false, isRefreshing = false, requiresLogin = true)
                        return@loadMutex
                    }

                    Napier.d("Loading grades for all semesters (forceRefresh: $forceRefresh)", tag = TAG)
                    val allGrades = mutableListOf<GradeEntity>()

                    // Load grades for each semester
                    for ((semesterName, semesterId) in uiState.semesters) {
                        val result = service.getGradesForSemester(
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
                    _data.value = uiState
                } catch (e: Exception) {
                    Napier.e("Error loading all grades: ${e.message}", e, tag = TAG)
                    uiState = uiState.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Error: ${e.message}"
                    )
                } finally {
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun refreshGrades() {
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
        // Set loading state appropriately
        uiState = if (isRefresh) {
            uiState.copy(isRefreshing = true)
        } else {
            uiState.copy(isLoading = true)
        }

        coroutineScope.launch {
            loadMutex.withLock {
                if (isRefresh) {
                    _isRefreshing.value = true
                } else {
                    _isLoading.value = true
                }

                try {
                    // Use retry logic to wait for gradeService
                    val service = getDataWithRetry("Grades Service Availability ($semesterName)") {
                        gradeService
                    }

                    if (service == null) {
                        uiState = uiState.copy(isLoading = false, isRefreshing = false, error = "Service not ready")
                        return@loadMutex
                    }

                    if (!service.hasCredentialsOrSession()) {
                        Napier.d("Skipping loadGradesForSemester: login required", tag = TAG)
                        uiState = uiState.copy(isLoading = false, isRefreshing = false, requiresLogin = true)
                        return@loadMutex
                    }

                    Napier.d("Loading grades for semester: $semesterName ($semesterId), isRefresh: $isRefresh", tag = TAG)
                    
                    // Call the service with forceRefresh flag
                    // When isRefresh is true (pull-to-refresh), we force reload from network
                    val result = service.getGradesForSemester(
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
                        _data.value = uiState
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
                } finally {
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
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
