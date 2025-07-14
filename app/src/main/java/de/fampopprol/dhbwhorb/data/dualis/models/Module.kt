/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
