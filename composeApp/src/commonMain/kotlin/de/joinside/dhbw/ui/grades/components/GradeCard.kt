package de.joinside.dhbw.ui.grades.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.data.storage.database.entities.grades.GradeEntity
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.status_unknown
import org.jetbrains.compose.resources.stringResource

@Composable
fun GradeCard(
    grade: GradeEntity,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = grade.moduleName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${grade.moduleNumber} • ${grade.credits} Credits",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                 Text(
                    text = grade.status ?: stringResource(Res.string.status_unknown),
                    style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.secondary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = grade.grade ?: "-",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (grade.grade == "5,0") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
