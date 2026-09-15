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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.petern.gtgstrength.data.DayPlan
import com.petern.gtgstrength.data.PlannedExercise
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: GtgUiState,
    modifier: Modifier = Modifier,
    onDeadliftOneRmChange: (Float) -> Unit,
    onRdlOneRmChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDayPlanChange: (DayPlan) -> Unit
) {
    var editingDay by remember { mutableStateOf<DayPlan?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Weekly program", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        uiState.settings.weeklyProgram.days.forEach { dayPlan ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            dayPlan.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(onClick = { editingDay = dayPlan }) { Text("Edit") }
                    }
                    Text("Deadlift  ${dayPlan.deadlift.sets} × ${dayPlan.deadlift.reps} @ ${formatPlanWeight(dayPlan.deadlift)} kg")
                    Text("RDL       ${dayPlan.rdl.sets} × ${dayPlan.rdl.reps} @ ${formatPlanWeight(dayPlan.rdl)} kg")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Training calculator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SettingsOneRmField("Deadlift 1RM", uiState.settings.deadliftOneRmKg, onDeadliftOneRmChange)
                SettingsOneRmField("RDL 1RM", uiState.settings.rdlOneRmKg, onRdlOneRmChange)
                Text("Intensity: ${uiState.settings.intensityPercent.roundToInt()}% of 1RM")
                Slider(
                    value = uiState.settings.intensityPercent,
                    onValueChange = { onIntensityChange(it.roundToInt().toFloat()) },
                    valueRange = 50f..70f,
                    steps = 19
                )
                Text("Suggested Deadlift: ${formatKg(uiState.suggestedDeadliftWeightKg)} kg")
                Text("Suggested RDL: ${formatKg(uiState.suggestedRdlWeightKg)} kg")
                Text(
                    "Calculator values are reference only and do not overwrite the weekly program.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    editingDay?.let { plan ->
        DayPlanDialog(
            plan = plan,
            onDismiss = { editingDay = null },
            onSave = {
                onDayPlanChange(it)
                editingDay = null
            }
        )
    }
}

@Composable
private fun DayPlanDialog(plan: DayPlan, onDismiss: () -> Unit, onSave: (DayPlan) -> Unit) {
    var dlSets by remember(plan) { mutableStateOf(plan.deadlift.sets.toString()) }
    var dlReps by remember(plan) { mutableStateOf(plan.deadlift.reps.toString()) }
    var dlMin by remember(plan) { mutableStateOf(formatKg(plan.deadlift.minWeightKg)) }
    var dlMax by remember(plan) { mutableStateOf(if (plan.deadlift.isRange) formatKg(plan.deadlift.maxWeightKg) else "") }
    var rdlSets by remember(plan) { mutableStateOf(plan.rdl.sets.toString()) }
    var rdlReps by remember(plan) { mutableStateOf(plan.rdl.reps.toString()) }
    var rdlMin by remember(plan) { mutableStateOf(formatKg(plan.rdl.minWeightKg)) }
    var rdlMax by remember(plan) { mutableStateOf(if (plan.rdl.isRange) formatKg(plan.rdl.maxWeightKg) else "") }

    fun exercise(sets: String, reps: String, min: String, max: String): PlannedExercise? {
        val s = sets.toIntOrNull() ?: return null
        val r = reps.toIntOrNull() ?: return null
        val lo = min.replace(',', '.').toDoubleOrNull() ?: return null
        val hi = max.replace(',', '.').toDoubleOrNull() ?: lo
        if (s !in 1..20 || r !in 1..50 || lo < 0 || hi < lo) return null
        return PlannedExercise(s, r, lo, hi).normalized()
    }

    val dl = exercise(dlSets, dlReps, dlMin, dlMax)
    val rdl = exercise(rdlSets, rdlReps, rdlMin, rdlMax)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${plan.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Deadlift", fontWeight = FontWeight.Bold)
                PlanField("Sets", dlSets) { dlSets = it }
                PlanField("Reps", dlReps) { dlReps = it }
                PlanField("Weight kg", dlMin) { dlMin = it }
                PlanField("Max kg (blank = fixed)", dlMax) { dlMax = it }
                Text("RDL", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                PlanField("Sets", rdlSets) { rdlSets = it }
                PlanField("Reps", rdlReps) { rdlReps = it }
                PlanField("Weight kg", rdlMin) { rdlMin = it }
                PlanField("Max kg (blank = fixed)", rdlMax) { rdlMax = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = dl != null && rdl != null,
                onClick = { onSave(plan.copy(deadlift = dl!!, rdl = rdl!!)) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlanField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(7)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun SettingsOneRmField(label: String, value: Float, onValueChange: (Float) -> Unit) {
    var text by rememberSaveable { mutableStateOf(settingsFormatInput(value)) }
    LaunchedEffect(value) {
        val parsed = text.replace(',', '.').toFloatOrNull()
        if (parsed == null || kotlin.math.abs(parsed - value) > 0.001f) text = settingsFormatInput(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.length <= 7) {
                text = newText
                newText.replace(',', '.').toFloatOrNull()?.let { if (it in 0f..1000f) onValueChange(it) }
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
    if (value % 1f == 0f) value.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", value)
