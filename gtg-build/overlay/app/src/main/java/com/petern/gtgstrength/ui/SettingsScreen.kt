package com.petern.gtgstrength.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: GtgUiState,
    modifier: Modifier = Modifier,
    onDeadliftOneRmChange: (Float) -> Unit,
    onRdlOneRmChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDeadliftTargetSetsChange: (Int) -> Unit,
    onDeadliftRepsChange: (Int) -> Unit,
    onRdlTargetSetsChange: (Int) -> Unit,
    onRdlRepsChange: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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

                SettingsOneRmField(
                    label = "Deadlift 1RM",
                    value = uiState.settings.deadliftOneRmKg,
                    onValueChange = onDeadliftOneRmChange
                )

                SettingsOneRmField(
                    label = "RDL 1RM",
                    value = uiState.settings.rdlOneRmKg,
                    onValueChange = onRdlOneRmChange
                )

                Text(
                    text = "Intensity: ${uiState.settings.intensityPercent.roundToInt()}% of 1RM",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = uiState.settings.intensityPercent,
                    onValueChange = { onIntensityChange(it.roundToInt().toFloat()) },
                    valueRange = 50f..70f,
                    steps = 19
                )
                Text(
                    text = "Recommended GTG range: 50–70%. Default is 60%.",
                    style = MaterialTheme.typography.bodySmall
                )

                SettingsExerciseVolumeControls(
                    title = "Deadlift",
                    targetSets = uiState.settings.deadliftTargetSets,
                    reps = uiState.settings.deadliftRepsPerSet,
                    onTargetSetsChange = onDeadliftTargetSetsChange,
                    onRepsChange = onDeadliftRepsChange
                )

                SettingsExerciseVolumeControls(
                    title = "Romanian Deadlift",
                    targetSets = uiState.settings.rdlTargetSets,
                    reps = uiState.settings.rdlRepsPerSet,
                    onTargetSetsChange = onRdlTargetSetsChange,
                    onRepsChange = onRdlRepsChange
                )
            }
        }
    }
}

@Composable
private fun SettingsExerciseVolumeControls(
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
private fun SettingsOneRmField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(settingsFormatInput(value)) }

    LaunchedEffect(value) {
        val parsed = text.replace(',', '.').toFloatOrNull()
        if (parsed == null || kotlin.math.abs(parsed - value) > 0.001f) {
            text = settingsFormatInput(value)
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

private fun settingsFormatInput(value: Float): String =
    if (value % 1f == 0f) value.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.1f", value)
