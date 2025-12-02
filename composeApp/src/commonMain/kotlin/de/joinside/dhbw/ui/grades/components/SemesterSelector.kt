package de.joinside.dhbw.ui.grades.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.all_semesters
import de.joinside.dhbw.ui.grades.viewModels.ALL_SEMESTERS_ID
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSelector(
    semesters: Map<String, String>,
    selectedSemesterId: String?,
    onSemesterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val semesterList = semesters.entries.toList()
    val hapticFeedback = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    // Add "All Semesters" option at the end
    val allSemestersOption = Pair(stringResource(Res.string.all_semesters), ALL_SEMESTERS_ID)
    val allOptions: List<Pair<String, String>> =
        semesterList.map { Pair(it.key, it.value) } + allSemestersOption

    // Find the currently selected semester name
    val selectedSemesterName = allOptions.find { it.second == selectedSemesterId }?.first ?: ""

    Box(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedSemesterName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Semester") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                leadingIcon = if (selectedSemesterId == ALL_SEMESTERS_ID) {
                    {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null,
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allOptions.forEach { (name, id) ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (id == ALL_SEMESTERS_ID) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(name)
                            }
                        },
                        onClick = {
                            onSemesterSelected(id)
                            expanded = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}
