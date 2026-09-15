package com.petern.gtgstrength.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.petern.gtgstrength.data.BarbellEquipment
import com.petern.gtgstrength.data.DayPlan
import com.petern.gtgstrength.data.PlannedExercise
import com.petern.gtgstrength.data.PlateStock
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private enum class ProgressionTarget {
    DEADLIFT,
    RDL
}

@Composable
fun SettingsScreen(
    uiState: GtgUiState,
    modifier: Modifier = Modifier,
    onDeadliftOneRmChange: (Float) -> Unit,
    onRdlOneRmChange: (Float) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDayPlanChange: (DayPlan) -> Unit,
    onDeadliftIncreaseFive: () -> Unit,
    onRdlIncreaseFive: () -> Unit,
    onEquipmentChange: (BarbellEquipment) -> Unit
) {
    var editingDay by remember { mutableStateOf<DayPlan?>(null) }
    var editingEquipment by remember { mutableStateOf(false) }
    var pendingProgression by remember { mutableStateOf<ProgressionTarget?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Weekly program", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Training week: Sunday–Saturday · Saturday is always a rest day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Weekly progression", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Increase one exercise across every programmed workout Sunday–Friday. Sets, reps and Saturday rest stay unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { pendingProgression = ProgressionTarget.DEADLIFT },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Deadlift +5%")
                }
                Button(
                    onClick = { pendingProgression = ProgressionTarget.RDL },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RDL +5%")
                }
            }
        }

        uiState.settings.weeklyProgram.orderedDays.forEach { dayPlan ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            dayPlan.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (dayPlan.dayOfWeek != DayOfWeek.SATURDAY) {
                            OutlinedButton(onClick = { editingDay = dayPlan }) { Text("Edit") }
                        }
                    }
                    if (dayPlan.isRestDay) {
                        Text("Rest day", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "No Deadlift or RDL scheduled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Deadlift  ${dayPlan.deadlift.sets} × ${dayPlan.deadlift.reps} @ ${formatPlanWeight(dayPlan.deadlift)} kg")
                        Text("RDL       ${dayPlan.rdl.sets} × ${dayPlan.rdl.reps} @ ${formatPlanWeight(dayPlan.rdl)} kg")
                    }
                }
            }
        }

        EquipmentCard(
            equipment = uiState.settings.barbellEquipment,
            onEdit = { editingEquipment = true }
        )

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

    if (editingEquipment) {
        EquipmentDialog(
            equipment = uiState.settings.barbellEquipment,
            onDismiss = { editingEquipment = false },
            onSave = {
                onEquipmentChange(it)
                editingEquipment = false
            }
        )
    }

    pendingProgression?.let { target ->
        val exerciseName = if (target == ProgressionTarget.DEADLIFT) "Deadlift" else "RDL"
        AlertDialog(
            onDismissRequest = { pendingProgression = null },
            title = { Text("Increase $exerciseName by 5%?") },
            text = {
                Text(
                    "Every programmed $exerciseName weight from Sunday through Friday will be multiplied by 1.05 and rounded to 0.01 kg. Weight ranges keep both their minimum and maximum. Sets, reps and Saturday rest day will not change. The plate calculator will still show the nearest load you can build."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (target == ProgressionTarget.DEADLIFT) onDeadliftIncreaseFive()
                        else onRdlIncreaseFive()
                        pendingProgression = null
                    }
                ) {
                    Text("Apply +5%")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingProgression = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EquipmentCard(
    equipment: BarbellEquipment,
    onEdit: () -> Unit
) {
    val stocked = equipment.plates.filter { it.count > 0 }.sortedByDescending { it.weightKg }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Bar & plates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onEdit) { Text("Edit") }
            }
            Text("Bar: ${formatKg(equipment.barWeightKg)} kg", fontWeight = FontWeight.SemiBold)

            if (stocked.isEmpty()) {
                Text(
                    "No plate stock entered yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                stocked.forEach { plate ->
                    Text("${formatKg(plate.weightKg)} kg × ${plate.count}")
                }
            }

            Text(
                "Enter total individual plate quantities. The calculator only uses matched left/right pairs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class EquipmentRow(
    val id: Int,
    val weight: String,
    val count: String
)

@Composable
private fun EquipmentDialog(
    equipment: BarbellEquipment,
    onDismiss: () -> Unit,
    onSave: (BarbellEquipment) -> Unit
) {
    var barText by remember(equipment) { mutableStateOf(formatKg(equipment.barWeightKg)) }
    var nextId by remember(equipment) { mutableStateOf(equipment.plates.size) }
    var rows by remember(equipment) {
        mutableStateOf(
            equipment.plates.mapIndexed { index, plate ->
                EquipmentRow(index, formatKg(plate.weightKg), plate.count.toString())
            }
        )
    }

    val barWeight = barText.replace(',', '.').toDoubleOrNull()
    val parsedPlates = rows.mapNotNull { row ->
        val weight = row.weight.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        val count = row.count.toIntOrNull() ?: return@mapNotNull null
        if (weight <= 0.0 || weight > 100.0 || count !in 0..100) return@mapNotNull null
        PlateStock(weightKg = weight, count = count)
    }
    val canSave = barWeight != null && barWeight in 0.1..100.0 && parsedPlates.size == rows.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bar & plate stock") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = barText,
                    onValueChange = { barText = it.take(7) },
                    label = { Text("Bar weight") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Text("Plates in stock", fontWeight = FontWeight.Bold)
                Text(
                    "Quantity = total plates, not pairs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                rows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = row.weight,
                                onValueChange = { value ->
                                    rows = rows.map {
                                        if (it.id == row.id) it.copy(weight = value.take(7)) else it
                                    }
                                },
                                label = { Text("Plate kg") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = row.count,
                                onValueChange = { value ->
                                    rows = rows.map {
                                        if (it.id == row.id) it.copy(count = value.filter(Char::isDigit).take(3)) else it
                                    }
                                },
                                label = { Text("Qty") },
                                singleLine = true,
                                modifier = Modifier.weight(0.65f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        TextButton(
                            onClick = { rows = rows.filterNot { it.id == row.id } }
                        ) {
                            Text("Remove ${row.weight.ifBlank { "plate" }} kg row")
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        rows = rows + EquipmentRow(nextId, "", "0")
                        nextId += 1
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add plate size")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        BarbellEquipment(
                            barWeightKg = barWeight!!,
                            plates = parsedPlates
                        ).normalized()
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
