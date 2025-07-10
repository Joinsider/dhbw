package de.fampopprol.dhbwhorb.data.dualis.models

/**
 * Represents an academic module with its grades and details
 */
data class Module(
    val id: String,
    val name: String,
    val credits: String,
    val grade: String,
    val state: ExamState,
    val exams: List<Exam> = emptyList()
)
