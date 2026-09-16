package com.petern.gtgstrength.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.petern.gtgstrength.data.PlannedExercise
import com.petern.gtgstrength.data.TrainingLogEntity
import com.petern.gtgstrength.domain.Exercise
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
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
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Today's progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                if (uiState.todayPlan.isRestDay) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("Rest day", fontWeight = FontWeight.Bold)
                            Text("Saturday is your planned day off. No Deadlift or RDL target today.")
                        }
                    }
                } else {
                    ProgressCard(
                        exerciseName = "Deadlift",
                        completed = uiState.deadliftSetsToday,
                        plan = uiState.todayPlan.deadlift,
                        repsLogged = uiState.deadliftRepsToday,
                        tonnageKg = uiState.deadliftTonnageToday
                    )
                    ProgressCard(
                        exerciseName = "Romanian Deadlift",
                        completed = uiState.rdlSetsToday,
                        plan = uiState.todayPlan.rdl,
                        repsLogged = uiState.rdlRepsToday,
                        tonnageKg = uiState.rdlTonnageToday
                    )
                }

                WeeklyProgressSection(uiState)

                Text(
                    "History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "Tap a row to expand: year → month → week → day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            HistoryTree(
                logs = uiState.logs,
                onEdit = { editingLog = it },
                onDelete = onDeleteLog
            )
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
    plan: PlannedExercise,
    repsLogged: Int,
    tonnageKg: Double
) {
    val progress = (completed.toFloat() / plan.sets.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(exerciseName, fontWeight = FontWeight.SemiBold)
                Text("$completed / ${plan.sets} sets")
            }
            Text("Plan: ${plan.sets} × ${plan.reps} @ ${formatPlanWeight(plan)} kg")
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "$repsLogged reps · ${formatKg(tonnageKg)} kg logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (completed >= plan.sets) {
                Text("Daily set target reached", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WeeklyProgressSection(uiState: GtgUiState) {
    Text("Weekly progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        "Week: Sunday–Saturday · Saturday rest day",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    WeekProgressCard(
        "This week",
        uiState.thisWeek,
        uiState.plannedWeeklyDeadliftSets,
        uiState.plannedWeeklyRdlSets
    )
    WeekProgressCard(
        "Last week",
        uiState.lastWeek,
        uiState.plannedWeeklyDeadliftSets,
        uiState.plannedWeeklyRdlSets
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            WeekExerciseProgress("Deadlift", stats.deadlift, deadliftTargetSets)
            WeekExerciseProgress("RDL", stats.rdl, rdlTargetSets)
            Text(
                "Total: ${stats.totalSets} sets · ${stats.totalReps} reps · ${formatKg(stats.totalTonnageKg)} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekExerciseProgress(name: String, stats: ExerciseWeekStats, targetSets: Int) {
    val progress = if (targetSets <= 0) 0f else (stats.sets.toFloat() / targetSets).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text("${stats.sets} / $targetSets sets")
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            "${stats.reps} reps · ${formatKg(stats.tonnageKg)} kg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class HistorySummary(
    val sets: Int,
    val reps: Int,
    val tonnageKg: Double
)

private data class HistoryDayNode(
    val date: LocalDate,
    val logs: List<TrainingLogEntity>
)

private data class HistoryWeekNode(
    val startDate: LocalDate,
    val days: List<HistoryDayNode>
)

private data class HistoryMonthNode(
    val month: YearMonth,
    val weeks: List<HistoryWeekNode>
)

private data class HistoryYearNode(
    val year: Int,
    val months: List<HistoryMonthNode>
)

@Composable
private fun HistoryTree(
    logs: List<TrainingLogEntity>,
    onEdit: (TrainingLogEntity) -> Unit,
    onDelete: (TrainingLogEntity) -> Unit
) {
    if (logs.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                "No sets logged yet. Use Quick Log on the Dashboard to record your first set.",
                modifier = Modifier.padding(18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val tree = remember(logs) { buildHistoryTree(logs) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    fun toggle(key: String) {
        expanded = if (key in expanded) expanded - key else expanded + key
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tree.forEach { year ->
            val yearKey = "year:${year.year}"
            val yearLogs = year.months.flatMap { month ->
                month.weeks.flatMap { week -> week.days.flatMap { it.logs } }
            }
            TreeRow(
                title = year.year.toString(),
                summary = historySummaryText(yearLogs),
                depth = 0,
                expanded = yearKey in expanded,
                onClick = { toggle(yearKey) }
            )

            if (yearKey in expanded) {
                year.months.forEach { month ->
                    val monthKey = "$yearKey/month:${month.month}"
                    val monthLogs = month.weeks.flatMap { week -> week.days.flatMap { it.logs } }
                    TreeRow(
                        title = month.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        summary = historySummaryText(monthLogs),
                        depth = 1,
                        expanded = monthKey in expanded,
                        onClick = { toggle(monthKey) }
                    )

                    if (monthKey in expanded) {
                        month.weeks.forEach { week ->
                            val weekKey = "$monthKey/week:${week.startDate}"
                            val weekLogs = week.days.flatMap { it.logs }
                            TreeRow(
                                title = weekLabel(week.startDate),
                                summary = historySummaryText(weekLogs),
                                depth = 2,
                                expanded = weekKey in expanded,
                                onClick = { toggle(weekKey) }
                            )

                            if (weekKey in expanded) {
                                week.days.forEach { day ->
                                    val dayKey = "$weekKey/day:${day.date}"
                                    TreeRow(
                                        title = dayLabel(day.date),
                                        summary = historySummaryText(day.logs),
                                        depth = 3,
                                        expanded = dayKey in expanded,
                                        onClick = { toggle(dayKey) }
                                    )

                                    if (dayKey in expanded) {
                                        Column(
                                            modifier = Modifier.padding(start = 52.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            day.logs.forEach { log ->
                                                LogCard(
                                                    log = log,
                                                    showDate = false,
                                                    onEdit = { onEdit(log) },
                                                    onDelete = { onDelete(log) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeRow(
    title: String,
    summary: String,
    depth: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            if (expanded) "▾" else "▸",
            modifier = Modifier.width(20.dp),
            fontWeight = FontWeight.Bold
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = if (depth <= 1) FontWeight.Bold else FontWeight.SemiBold)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildHistoryTree(logs: List<TrainingLogEntity>): List<HistoryYearNode> {
    val zone = ZoneId.systemDefault()
    val datedLogs = logs.map { log ->
        Instant.ofEpochMilli(log.timestamp).atZone(zone).toLocalDate() to log
    }

    return datedLogs
        .groupBy { it.first.year }
        .entries
        .sortedByDescending { it.key }
        .map { (year, yearEntries) ->
            val months = yearEntries
                .groupBy { YearMonth.from(it.first) }
                .entries
                .sortedByDescending { it.key }
                .map { (month, monthEntries) ->
                    val weeks = monthEntries
                        .groupBy { (date, _) ->
                            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                        }
                        .entries
                        .sortedByDescending { it.key }
                        .map { (weekStart, weekEntries) ->
                            val days = weekEntries
                                .groupBy { it.first }
                                .entries
                                .sortedByDescending { it.key }
                                .map { (date, dayEntries) ->
                                    HistoryDayNode(
                                        date = date,
                                        logs = dayEntries.map { it.second }.sortedByDescending { it.timestamp }
                                    )
                                }
                            HistoryWeekNode(startDate = weekStart, days = days)
                        }
                    HistoryMonthNode(month = month, weeks = weeks)
                }
            HistoryYearNode(year = year, months = months)
        }
}

private fun historySummaryText(logs: List<TrainingLogEntity>): String {
    val summary = HistorySummary(
        sets = logs.size,
        reps = logs.sumOf { it.reps },
        tonnageKg = logs.sumOf { it.reps * it.weightKg }
    )
    return "${summary.sets} sets · ${summary.reps} reps · ${formatKg(summary.tonnageKg)} kg"
}

private fun weekLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    val locale = Locale.getDefault()
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    return if (start.year == end.year) {
        "Week ${start.format(dayMonth)}–${end.format(dayMonth)}"
    } else {
        val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        "Week ${start.format(full)}–${end.format(full)}"
    }
}

private fun dayLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))

@Composable
private fun LogCard(
    log: TrainingLogEntity,
    showDate: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val exercise = Exercise.fromStoredName(log.exercise)
    val formatter = remember(showDate) {
        DateTimeFormatter.ofPattern(
            if (showDate) "EEE, d MMM yyyy · HH:mm" else "HH:mm",
            Locale.getDefault()
        )
    }
    val formattedTime = remember(log.timestamp, showDate) {
        Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).format(formatter)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(exercise.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${log.reps} reps @ ${formatKg(log.weightKg)} kg")
                Text(formattedTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit set") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete set") }
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
            TextButton(enabled = canSave, onClick = { onSave(reps!!, weight!!) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
