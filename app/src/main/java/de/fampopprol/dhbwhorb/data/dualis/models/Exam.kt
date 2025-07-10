package de.fampopprol.dhbwhorb.data.dualis.models

/**
 * Represents an individual exam within a module
 */
data class Exam(
    val name: String,
    val grade: ExamGrade,
    val semester: String,
    val state: ExamState
)
