package com.petern.gtgstrength.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petern.gtgstrength.domain.Exercise
import com.petern.gtgstrength.domain.ExerciseTarget
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    uiState: GtgUiState,
    modifier: Modifier = Modifier,
    onDeadliftOneRmChange: (Float) -> Unit,
    onRdlOneRmChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDeadliftTargetSetsChange: (Int) -> Unit,
    onDeadliftRepsChange: (Int) -> Unit,
    onRdlTargetSetsChange: (Int) -> Unit,
    onRdlRepsChange: (Int) -> Unit,
    onQuickLog: (Exercise) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Grease the groove with fresh, submaximal sets spread through the day.",
            style = MaterialTheme.typography.bodyLarge
        )

        SettingsCard(
            deadliftOneRm = uiState.settings.deadliftOneRmKg,
            rdlOneRm = uiState.settings.rdlOneRmKg,
            intensity = uiState.settings.intensityPercent,
            deadliftTargetSets = uiState.settings.deadliftTargetSets,
            deadliftReps = uiState.settings.deadliftRepsPerSet,
            rdlTargetSets = uiState.settings.rdlTargetSets,
            rdlReps = uiState.settings.rdlRepsPerSet,
            onDeadliftOneRmChange = onDeadliftOneRmChange,
            onRdlOneRmChange = onRdlOneRmChange,
            onIntensityChange = onIntensityChange,
            onDeadliftTargetSetsChange = onDeadliftTargetSetsChange,
            onDeadliftRepsChange = onDeadliftRepsChange,
            onRdlTargetSetsChange = onRdlTargetSetsChange,
            onRdlRepsChange = onRdlRepsChange
        )

        TargetCard(
            title = "Deadlift target",
            target = uiState.deadliftTarget
        )

        TargetCard(
            title = "Romanian Deadlift target",
            target = uiState.rdlTarget
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Combined daily target",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text("${uiState.combinedDailyReps} total reps")
                Text("${formatKg(uiState.combinedDailyTonnageKg)} kg total tonnage")
            }
        }

        Text(
            text = "Quick log",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = { onQuickLog(Exercise.DEADLIFT) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "+1 Deadlift set  •  ${uiState.deadliftTarget.repsPerSet} reps @ " +
                    "${formatKg(uiState.deadliftTarget.workingWeightKg)} kg"
            )
        }

        Button(
            onClick = { onQuickLog(Exercise.RDL) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "+1 RDL set  •  ${uiState.rdlTarget.repsPerSet} reps @ " +
                    "${formatKg(uiState.rdlTarget.workingWeightKg)} kg"
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Text(
                text = "GTG rule: every rep should stay crisp. Stop the set if bar speed or technique noticeably deteriorates; this plan is not meant to be taken to fatigue.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsCard(
    deadliftOneRm: Float,
    rdlOneRm: Float,
    intensity: Float,
    deadliftTargetSets: Int,
    deadliftReps: Int,
    rdlTargetSets: Int,
    rdlReps: Int,
    onDeadliftOneRmChange: (Float) -> Unit,
    onRdlOneRmChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDeadliftTargetSetsChange: (Int) -> Unit,
    onDeadliftRepsChange: (Int) -> Unit,
    onRdlTargetSetsChange: (Int) -> Unit,
    onRdlRepsChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Training calculator",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OneRmField(
                label = "Deadlift 1RM",
                value = deadliftOneRm,
                onValueChange = onDeadliftOneRmChange
            )

            OneRmField(
                label = "RDL 1RM",
                value = rdlOneRm,
                onValueChange = onRdlOneRmChange
            )

            Text(
                text = "Intensity: ${intensity.roundToInt()}% of 1RM",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = intensity,
                onValueChange = { onIntensityChange(it.roundToInt().toFloat()) },
                valueRange = 50f..70f,
                steps = 19
            )
            Text(
                text = "Recommended GTG range: 50–70%. Default is 60%.",
                style = MaterialTheme.typography.bodySmall
            )

            ExerciseVolumeControls(
                title = "Deadlift",
                targetSets = deadliftTargetSets,
                reps = deadliftReps,
                onTargetSetsChange = onDeadliftTargetSetsChange,
                onRepsChange = onDeadliftRepsChange
            )

            ExerciseVolumeControls(
                title = "Romanian Deadlift",
                targetSets = rdlTargetSets,
                reps = rdlReps,
                onTargetSetsChange = onRdlTargetSetsChange,
                onRepsChange = onRdlRepsChange
            )
        }
    }
}

@Composable
private fun ExerciseVolumeControls(
    title: String,
    targetSets: Int,
    reps: Int,
    onTargetSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Target sets: $targetSets / day",
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = targetSets.toFloat(),
            onValueChange = { onTargetSetsChange(it.roundToInt()) },
            valueRange = 1f..10f,
            steps = 8
        )

        Text(
            text = "Reps per set",
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (3..5).forEach { repOption ->
                FilterChip(
                    selected = reps == repOption,
                    onClick = { onRepsChange(repOption) },
                    label = { Text("$repOption reps") }
                )
            }
        }
    }
}

@Composable
private fun OneRmField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(formatInput(value)) }

    LaunchedEffect(value) {
        val parsed = text.replace(',', '.').toFloatOrNull()
        if (parsed == null || kotlin.math.abs(parsed - value) > 0.001f) {
            text = formatInput(value)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.length <= 7) {
                text = newText
                newText.replace(',', '.').toFloatOrNull()?.let { parsed ->
                    if (parsed in 0f..1000f) onValueChange(parsed)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text("kg") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun TargetCard(
    title: String,
    target: ExerciseTarget
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            SpecRow("Working weight", "${formatKg(target.workingWeightKg)} kg")
            SpecRow("Reps / set", target.repsPerSet.toString())
            SpecRow("Sets / day", target.targetSets.toString())
            SpecRow("Daily rep target", target.totalReps.toString())
            SpecRow("Daily tonnage", "${formatKg(target.totalTonnageKg)} kg")
            Text(
                text = "${formatKg(target.workingWeightKg)} kg × ${target.repsPerSet} reps × ${target.targetSets} sets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatInput(value: Float): String =
    if (value % 1f == 0f) value.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.1f", value)

internal fun formatKg(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value)
