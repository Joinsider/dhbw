package de.fampopprol.dhbwhorb.data.dualis.models

/**
 * Represents a grade for an exam with its state
 */
class ExamGrade private constructor(
    val state: ExamGradeState,
    val gradeValue: String
) {
    companion object {
        fun failed(): ExamGrade = ExamGrade(ExamGradeState.FAILED, "")

        fun notGraded(): ExamGrade = ExamGrade(ExamGradeState.NOT_GRADED, "")

        fun passed(): ExamGrade = ExamGrade(ExamGradeState.PASSED, "")

        fun graded(grade: String): ExamGrade = ExamGrade(ExamGradeState.GRADED, grade)

        fun fromString(grade: String): ExamGrade {
            return when {
                grade == "noch nicht gesetzt" || grade.isEmpty() -> notGraded()
                grade == "b" -> passed()
                // TODO: Determine the value when an exam is in the "failed" state
                else -> graded(grade)
            }
        }
    }
}

/**
 * State of a grade
 */
enum class ExamGradeState {
    NOT_GRADED,
    GRADED,
    PASSED,
    FAILED
}
