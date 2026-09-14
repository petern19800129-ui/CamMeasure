package com.petern.gtgstrength.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petern.gtgstrength.data.TrainingLogEntity
import com.petern.gtgstrength.domain.Exercise
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrainingLogScreen(
    uiState: GtgUiState,
    modifier: Modifier = Modifier,
    onUpdateLog: (TrainingLogEntity, Int, Double) -> Unit,
    onDeleteLog: (TrainingLogEntity) -> Unit
) {
    var editingLog by remember { mutableStateOf<TrainingLogEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WeeklyProgressSection(uiState)

                Text(
                    text = "Today's progress",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                ProgressCard(
                    exerciseName = "Deadlift",
                    completed = uiState.deadliftSetsToday,
                    target = uiState.settings.deadliftTargetSets
                )

                ProgressCard(
                    exerciseName = "Romanian Deadlift",
                    completed = uiState.rdlSetsToday,
                    target = uiState.settings.rdlTargetSets
                )

                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (uiState.logs.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No sets logged yet. Use Quick Log on the Dashboard to record your first set.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = uiState.logs,
                key = { it.id }
            ) { log ->
                LogCard(
                    log = log,
                    onEdit = { editingLog = log },
                    onDelete = { onDeleteLog(log) }
                )
            }
        }

        item { Text("", modifier = Modifier.padding(bottom = 8.dp)) }
    }

    editingLog?.let { log ->
        EditLogDialog(
            log = log,
            onDismiss = { editingLog = null },
            onSave = { reps, weight ->
                onUpdateLog(log, reps, weight)
                editingLog = null
            }
        )
    }
}

@Composable
private fun ProgressCard(
    exerciseName: String,
    completed: Int,
    target: Int
) {
    val progress = if (target <= 0) 0f else (completed.toFloat() / target).coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(exerciseName, fontWeight = FontWeight.SemiBold)
                Text("$completed / $target sets")
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            if (completed >= target) {
                Text(
                    text = "Daily set target reached",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun WeeklyProgressSection(uiState: GtgUiState) {
    Text(
        text = "Weekly progress",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    WeekProgressCard(
        title = "This week",
        stats = uiState.thisWeek,
        deadliftTargetSets = uiState.settings.deadliftTargetSets * 7,
        rdlTargetSets = uiState.settings.rdlTargetSets * 7
    )

    WeekProgressCard(
        title = "Last week",
        stats = uiState.lastWeek,
        deadliftTargetSets = uiState.settings.deadliftTargetSets * 7,
        rdlTargetSets = uiState.settings.rdlTargetSets * 7
    )
}

@Composable
private fun WeekProgressCard(
    title: String,
    stats: WeekStats,
    deadliftTargetSets: Int,
    rdlTargetSets: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            WeekExerciseProgress(
                name = "Deadlift",
                stats = stats.deadlift,
                targetSets = deadliftTargetSets
            )
            WeekExerciseProgress(
                name = "RDL",
                stats = stats.rdl,
                targetSets = rdlTargetSets
            )

            Text(
                text = "Total: ${stats.totalSets} sets • ${stats.totalReps} reps • ${formatKg(stats.totalTonnageKg)} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekExerciseProgress(
    name: String,
    stats: ExerciseWeekStats,
    targetSets: Int
) {
    val progress = if (targetSets <= 0) 0f else (stats.sets.toFloat() / targetSets).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text("${stats.sets} / $targetSets sets")
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${stats.reps} reps • ${formatKg(stats.tonnageKg)} kg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogCard(
    log: TrainingLogEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val exercise = Exercise.fromStoredName(log.exercise)
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy • HH:mm", Locale.getDefault())
    }
    val formattedTime = remember(log.timestamp) {
        Instant.ofEpochMilli(log.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = exercise.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text("${log.reps} reps @ ${formatKg(log.weightKg)} kg")
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit set")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete set")
                }
            }
        }
    }
}

@Composable
private fun EditLogDialog(
    log: TrainingLogEntity,
    onDismiss: () -> Unit,
    onSave: (Int, Double) -> Unit
) {
    var repsText by remember(log.id) { mutableStateOf(log.reps.toString()) }
    var weightText by remember(log.id) { mutableStateOf(formatKg(log.weightKg)) }

    val reps = repsText.toIntOrNull()
    val weight = weightText.replace(',', '.').toDoubleOrNull()
    val canSave = reps != null && reps > 0 && weight != null && weight >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit logged set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.take(7) },
                    label = { Text("Weight") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(reps!!, weight!!) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
