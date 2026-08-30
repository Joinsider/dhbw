/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.grades.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import kotlin.math.round

@Composable
fun SemesterGroupCard(
    semesterName: String,
    grades: List<GradeEntry>,
    semesterGpa: Double?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("semesterGroupCard_$semesterName")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SemesterCardHeader(
                semesterName = semesterName,
                grades = grades,
                semesterGpa = semesterGpa,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded }
            )

            SemesterGradesList(expanded = expanded, grades = grades)
        }
    }
}

@Composable
private fun SemesterCardHeader(
    semesterName: String,
    grades: List<GradeEntry>,
    semesterGpa: Double?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("semesterCardHeader")
            .clickable(onClick = onToggleExpanded)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = semesterName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${grades.size} modules • ${grades.sumOf { it.credits }} credits",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SemesterGpaLabel(semesterGpa)
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
    }
}

@Composable
private fun SemesterGpaLabel(semesterGpa: Double?) {
    if (semesterGpa == null) return

    Text(
        text = "Ø ${(round(semesterGpa * 100) / 100)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SemesterGradesList(expanded: Boolean, grades: List<GradeEntry>) {
    // Expandable content
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column {
            HorizontalDivider()
            grades.forEach { grade ->
                GradeRow(grade = grade)
                if (grade != grades.last()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun GradeRow(
    grade: GradeEntry,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gradeRow_${grade.moduleNumber}")
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = grade.moduleName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${grade.moduleNumber} • ${grade.credits} Credits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = grade.grade ?: "-",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (grade.grade == "5,0") {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

