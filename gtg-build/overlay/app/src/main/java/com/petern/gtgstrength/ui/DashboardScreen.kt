package com.petern.gtgstrength.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petern.gtgstrength.data.PlateCalculator
import com.petern.gtgstrength.data.PlateLoading
import com.petern.gtgstrength.data.PlannedExercise
import com.petern.gtgstrength.domain.Exercise
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    uiState: GtgUiState,
    modifier: Modifier = Modifier,
    onQuickLog: (Exercise, Double) -> Unit
) {
    val deadliftPlan = uiState.todayPlan.deadlift
    val rdlPlan = uiState.todayPlan.rdl

    if (uiState.todayPlan.isRestDay) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Quick log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "TODAY · ${uiState.todayDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Rest day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Saturday is your planned day off. No Deadlift or RDL sets are scheduled today.")
                    Text(
                        "Your next training week starts Sunday.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val equipment = uiState.settings.barbellEquipment
    var deadliftWeight by remember(deadliftPlan.minWeightKg, deadliftPlan.maxWeightKg, uiState.todayDate) {
        mutableStateOf(deadliftPlan.minWeightKg)
    }
    var rdlWeight by remember(rdlPlan.minWeightKg, rdlPlan.maxWeightKg, uiState.todayDate) {
        mutableStateOf(rdlPlan.minWeightKg)
    }

    val deadliftLoading = remember(deadliftWeight, equipment) {
        PlateCalculator.calculate(deadliftWeight, equipment)
    }
    val rdlLoading = remember(rdlWeight, equipment) {
        PlateCalculator.calculate(rdlWeight, equipment)
    }
    val deadliftLogWeight = effectiveLogWeight(deadliftWeight, deadliftLoading)
    val rdlLogWeight = effectiveLogWeight(rdlWeight, rdlLoading)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Quick log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "TODAY · ${uiState.todayDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (uiState.cooldownRemainingMillis > 0L) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Recovery timer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Next set in ${formatCooldown(uiState.cooldownRemainingMillis)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Quick Log is locked until the 1-hour timer finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        TodayExerciseCard(
            title = "Deadlift",
            plan = deadliftPlan,
            completed = uiState.deadliftSetsToday,
            actualTonnageKg = uiState.deadliftTonnageToday,
            selectedWeightKg = deadliftWeight,
            loading = deadliftLoading,
            canLog = uiState.cooldownRemainingMillis <= 0L,
            cooldownRemainingMillis = uiState.cooldownRemainingMillis,
            onWeightSelected = { deadliftWeight = it },
            onLog = { onQuickLog(Exercise.DEADLIFT, deadliftLogWeight) }
        )

        TodayExerciseCard(
            title = "Romanian Deadlift",
            plan = rdlPlan,
            completed = uiState.rdlSetsToday,
            actualTonnageKg = uiState.rdlTonnageToday,
            selectedWeightKg = rdlWeight,
            loading = rdlLoading,
            canLog = uiState.cooldownRemainingMillis <= 0L,
            cooldownRemainingMillis = uiState.cooldownRemainingMillis,
            onWeightSelected = { rdlWeight = it },
            onLog = { onQuickLog(Exercise.RDL, rdlLogWeight) }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Today's volume", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                VolumeRow(
                    "Deadlift",
                    uiState.deadliftTonnageToday,
                    deadliftPlan.sets * deadliftPlan.reps * deadliftLogWeight
                )
                VolumeRow(
                    "RDL",
                    uiState.rdlTonnageToday,
                    rdlPlan.sets * rdlPlan.reps * rdlLogWeight
                )
                VolumeRow(
                    "Total",
                    uiState.deadliftTonnageToday + uiState.rdlTonnageToday,
                    deadliftPlan.sets * deadliftPlan.reps * deadliftLogWeight +
                        rdlPlan.sets * rdlPlan.reps * rdlLogWeight
                )
            }
        }
    }
}

@Composable
private fun TodayExerciseCard(
    title: String,
    plan: PlannedExercise,
    completed: Int,
    actualTonnageKg: Double,
    selectedWeightKg: Double,
    loading: PlateLoading,
    canLog: Boolean,
    cooldownRemainingMillis: Long,
    onWeightSelected: (Double) -> Unit,
    onLog: () -> Unit
) {
    val progress = (completed.toFloat() / plan.sets.coerceAtLeast(1)).coerceIn(0f, 1f)
    val complete = completed >= plan.sets
    val logWeight = effectiveLogWeight(selectedWeightKg, loading)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$completed / ${plan.sets} sets")
            }
            Text(
                "${plan.sets} × ${plan.reps} @ ${formatPlanWeight(plan)} kg",
                style = MaterialTheme.typography.bodyLarge
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

            if (plan.isRange) {
                Text("Choose today's working weight", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weightChoices(plan).forEach { weight ->
                        FilterChip(
                            selected = abs(selectedWeightKg - weight) < 0.01,
                            onClick = { onWeightSelected(weight) },
                            label = { Text("${formatKg(weight)} kg") }
                        )
                    }
                }
            }

            PlateLoadingSection(loading)

            Text(
                "${formatKg(actualTonnageKg)} kg logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onLog,
                enabled = canLog,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!canLog) {
                    Text("Locked · ${formatCooldown(cooldownRemainingMillis)}")
                } else {
                    val prefix = if (complete) "✓ Complete · + Extra set" else "+ Log set"
                    Text("$prefix · ${plan.reps} reps @ ${formatKg(logWeight)} kg")
                }
            }
        }
    }
}

@Composable
private fun PlateLoadingSection(loading: PlateLoading) {
    if (!loading.configured) {
        Text(
            "Plate calculator: add your available plates under Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val perSide = if (loading.platesPerSide.isEmpty()) {
        "none — bar only"
    } else {
        loading.platesPerSide.joinToString(" + ") { item ->
            if (item.countPerSide == 1) "${formatKg(item.weightKg)} kg"
            else "${item.countPerSide}×${formatKg(item.weightKg)} kg"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!loading.exact) {
            val sign = if (loading.differenceKg >= 0.0) "+" else ""
            Text(
                "Closest load: ${formatKg(loading.actualWeightKg)} kg ($sign${formatKg(loading.differenceKg)} kg)",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                "Plate loading · ${formatKg(loading.actualWeightKg)} kg total",
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "Bar: ${formatKg(loading.barWeightKg)} kg · Plates / side: $perSide",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun effectiveLogWeight(selectedWeightKg: Double, loading: PlateLoading): Double =
    if (loading.configured) loading.actualWeightKg else selectedWeightKg

@Composable
private fun VolumeRow(label: String, actual: Double, target: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatKg(actual)} / ${formatKg(target)} kg", fontWeight = FontWeight.SemiBold)
    }
}

internal fun formatPlanWeight(plan: PlannedExercise): String =
    if (plan.isRange) "${formatKg(plan.minWeightKg)}–${formatKg(plan.maxWeightKg)}"
    else formatKg(plan.minWeightKg)

private fun weightChoices(plan: PlannedExercise): List<Double> {
    if (!plan.isRange) return listOf(plan.minWeightKg)
    val values = mutableListOf<Double>()
    var value = plan.minWeightKg
    while (value <= plan.maxWeightKg + 0.01) {
        values += value
        value += 2.5
    }
    if (abs(values.last() - plan.maxWeightKg) > 0.01) values += plan.maxWeightKg
    return values.distinct()
}

internal fun formatKg(value: Double): String =
    if (abs(value - value.roundToInt()) < 0.001) value.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.2f", value).trimEnd('0').trimEnd(',').trimEnd('.')


internal fun formatCooldown(milliseconds: Long): String {
    val totalSeconds = ((milliseconds.coerceAtLeast(0L) + 999L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
