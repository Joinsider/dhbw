/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.grades

import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class GradesTrendTest {

    private fun grade(
        semester: String,
        module: String,
        value: String?,
        credits: Double,
        status: String = "bestanden"
    ) = GradeEntry(
        semesterId = semester,
        semesterName = semester,
        moduleNumber = module,
        moduleName = module,
        grade = value,
        credits = credits,
        status = status
    )

    @Test
    fun trend_isCreditWeightedAndOldestFirst() {
        // Newest semester first, as the grades arrive.
        val state = GradesState(
            grades = listOf(
                grade("WiSe 2025/26", "M1", "2,0", 5.0),
                grade("WiSe 2025/26", "M2", "1,0", 15.0),
                grade("SoSe 2025", "M3", "3,0", 5.0),
                grade("SoSe 2025", "M4", "2,0", 15.0),
                grade("WiSe 2024/25", "M5", "1,0", 5.0),
                grade("WiSe 2024/25", "M6", "2,0", 5.0)
            )
        )

        assertEquals(
            listOf(
                SemesterAverage("WiSe 2024/25", 1.5),
                SemesterAverage("SoSe 2025", 2.25),
                SemesterAverage("WiSe 2025/26", 1.25)
            ),
            state.semesterTrend
        )
    }

    @Test
    fun trend_leavesOutSemesterWithoutAnyGradeYet() {
        val state = GradesState(
            grades = listOf(
                grade("SoSe 2026", "M1", null, 5.0),
                grade("WiSe 2025/26", "M2", "2,0", 5.0)
            )
        )

        assertEquals(
            listOf(SemesterAverage("WiSe 2025/26", 2.0)),
            state.semesterTrend
        )
    }

    @Test
    fun trend_repeatedModuleCountsOnlyTheAttemptThatCounts() {
        val state = GradesState(
            grades = listOf(
                // Failed attempt in the later semester's list position: must not enter the average.
                grade("WiSe 2025/26", "M1", "4,6", 5.0, status = "unvollständig"),
                grade("WiSe 2025/26", "M2", "2,0", 5.0),
                grade("SoSe 2026", "M1", "3,2", 5.0, status = "bestanden (Wh.)")
            )
        )

        // Including the 4,6 would give WiSe 2025/26 an average of 2.8 instead of 2.0.
        assertEquals(
            listOf(
                SemesterAverage("WiSe 2025/26", 2.0),
                SemesterAverage("SoSe 2026", 3.2)
            ),
            state.semesterTrend
        )
    }
}
