/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
