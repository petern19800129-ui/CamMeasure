package com.petern.gtgstrength.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petern.gtgstrength.data.BarbellEquipment
import com.petern.gtgstrength.data.BarbellEquipmentCodec
import com.petern.gtgstrength.data.DayPlan
import com.petern.gtgstrength.data.ProgressionLog
import com.petern.gtgstrength.data.ProgressionPolicy
import com.petern.gtgstrength.data.ProgressionReadiness
import com.petern.gtgstrength.data.SettingsRepository
import com.petern.gtgstrength.data.TrainingLogEntity
import com.petern.gtgstrength.data.TrainingRepository
import com.petern.gtgstrength.data.TrainingSettings
import com.petern.gtgstrength.data.WeeklyProgramCodec
import com.petern.gtgstrength.domain.Exercise
import com.petern.gtgstrength.domain.TrainingCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ExerciseWeekStats(
    val sets: Int = 0,
    val reps: Int = 0,
    val tonnageKg: Double = 0.0
)

data class WeekStats(
    val deadlift: ExerciseWeekStats = ExerciseWeekStats(),
    val rdl: ExerciseWeekStats = ExerciseWeekStats()
) {
    val totalSets: Int get() = deadlift.sets + rdl.sets
    val totalReps: Int get() = deadlift.reps + rdl.reps
    val totalTonnageKg: Double get() = deadlift.tonnageKg + rdl.tonnageKg
}

data class WeekDayProgress(
    val date: LocalDate,
    val plan: DayPlan,
    val deadliftSets: Int,
    val rdlSets: Int,
    val isToday: Boolean
) {
    val isComplete: Boolean
        get() = plan.isRestDay || (deadliftSets >= plan.deadlift.sets && rdlSets >= plan.rdl.sets)
}

data class GtgUiState(
    val settings: TrainingSettings = TrainingSettings(),
    val todayPlan: DayPlan = TrainingSettings().weeklyProgram.forDay(DayOfWeek.SUNDAY),
    val todayDate: LocalDate = LocalDate.of(2000, 1, 2),
    val deadliftSetsToday: Int = 0,
    val rdlSetsToday: Int = 0,
    val deadliftRepsToday: Int = 0,
    val rdlRepsToday: Int = 0,
    val deadliftTonnageToday: Double = 0.0,
    val rdlTonnageToday: Double = 0.0,
    val suggestedDeadliftWeightKg: Double = 42.0,
    val suggestedRdlWeightKg: Double = 30.0,
    val thisWeek: WeekStats = WeekStats(),
    val lastWeek: WeekStats = WeekStats(),
    val weekDays: List<WeekDayProgress> = emptyList(),
    val logs: List<TrainingLogEntity> = emptyList(),
    val deadliftCooldownRemainingMillis: Long = 0L,
    val rdlCooldownRemainingMillis: Long = 0L,
    val deadliftProgression: ProgressionReadiness = ProgressionReadiness(),
    val rdlProgression: ProgressionReadiness = ProgressionReadiness()
) {
    val plannedWeeklyDeadliftSets: Int get() = settings.weeklyProgram.deadliftWeeklySets
    val plannedWeeklyRdlSets: Int get() = settings.weeklyProgram.rdlWeeklySets
}

class GtgViewModel(
    private val trainingRepository: TrainingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val clockRefresh = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val manualRefresh = MutableStateFlow(0L)

    val uiState = combine(
        settingsRepository.settings,
        trainingRepository.logs,
        clockRefresh,
        manualRefresh
    ) { settings, logs, nowMillis, _ ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayPlan = settings.weeklyProgram.forDay(today.dayOfWeek)
        val todayLogs = logsForDate(logs, today, zone)

        val startOfThisWeekDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val startOfLastWeekDate = startOfThisWeekDate.minusWeeks(1)
        val startOfNextWeekDate = startOfThisWeekDate.plusWeeks(1)

        val startOfThisWeek = startOfThisWeekDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfLastWeek = startOfLastWeekDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfNextWeek = startOfNextWeekDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val deadliftToday = todayLogs.filter { it.exercise == Exercise.DEADLIFT.storedName }
        val rdlToday = todayLogs.filter { it.exercise == Exercise.RDL.storedName }

        val suggestedDeadlift = TrainingCalculator.calculate(
            oneRepMaxKg = settings.deadliftOneRmKg,
            intensityPercent = settings.intensityPercent,
            repsPerSet = 1,
            targetSets = 1
        ).workingWeightKg
        val suggestedRdl = TrainingCalculator.calculate(
            oneRepMaxKg = settings.rdlOneRmKg,
            intensityPercent = settings.intensityPercent,
            repsPerSet = 1,
            targetSets = 1
        ).workingWeightKg

        val weekDays = (0L..6L).map { offset ->
            val date = startOfThisWeekDate.plusDays(offset)
            val dayLogs = logsForDate(logs, date, zone)
            WeekDayProgress(
                date = date,
                plan = settings.weeklyProgram.forDay(date.dayOfWeek),
                deadliftSets = dayLogs.count { it.exercise == Exercise.DEADLIFT.storedName },
                rdlSets = dayLogs.count { it.exercise == Exercise.RDL.storedName },
                isToday = date == today
            )
        }

        val progressionLogs = logs.map {
            ProgressionLog(
                exerciseStoredName = it.exercise,
                timestamp = it.timestamp
            )
        }
        val deadliftProgression = ProgressionPolicy.evaluate(
            program = settings.weeklyProgram,
            logs = progressionLogs,
            exercise = Exercise.DEADLIFT,
            today = today,
            zone = zone,
            lastProgressionAtMillis = settings.deadliftLastProgressionAtMillis
        )
        val rdlProgression = ProgressionPolicy.evaluate(
            program = settings.weeklyProgram,
            logs = progressionLogs,
            exercise = Exercise.RDL,
            today = today,
            zone = zone,
            lastProgressionAtMillis = settings.rdlLastProgressionAtMillis
        )

        GtgUiState(
            settings = settings,
            todayPlan = todayPlan,
            todayDate = today,
            deadliftSetsToday = deadliftToday.size,
            rdlSetsToday = rdlToday.size,
            deadliftRepsToday = deadliftToday.sumOf { it.reps },
            rdlRepsToday = rdlToday.sumOf { it.reps },
            deadliftTonnageToday = deadliftToday.sumOf { it.reps * it.weightKg },
            rdlTonnageToday = rdlToday.sumOf { it.reps * it.weightKg },
            suggestedDeadliftWeightKg = suggestedDeadlift,
            suggestedRdlWeightKg = suggestedRdl,
            thisWeek = calculateWeekStats(logs, startOfThisWeek, startOfNextWeek),
            lastWeek = calculateWeekStats(logs, startOfLastWeek, startOfThisWeek),
            weekDays = weekDays,
            logs = logs,
            deadliftCooldownRemainingMillis =
                (settings.nextLogAllowedAtMillis(Exercise.DEADLIFT) - nowMillis).coerceAtLeast(0L),
            rdlCooldownRemainingMillis =
                (settings.nextLogAllowedAtMillis(Exercise.RDL) - nowMillis).coerceAtLeast(0L),
            deadliftProgression = deadliftProgression,
            rdlProgression = rdlProgression
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GtgUiState()
    )

    fun setDeadliftOneRm(value: Float) {
        viewModelScope.launch { settingsRepository.setDeadliftOneRmKg(value) }
    }

    fun setRdlOneRm(value: Float) {
        viewModelScope.launch { settingsRepository.setRdlOneRmKg(value) }
    }

    fun setIntensity(value: Float) {
        viewModelScope.launch { settingsRepository.setIntensityPercent(value) }
    }

    fun updateDayPlan(dayPlan: DayPlan) {
        viewModelScope.launch { settingsRepository.updateDayPlan(dayPlan) }
    }

    fun increaseDeadliftProgramByFivePercent() {
        val state = uiState.value
        if (!state.deadliftProgression.isReady) return
        val updated = state.settings.weeklyProgram.increaseDeadliftByPercent(5.0)
        viewModelScope.launch {
            settingsRepository.applyWeeklyProgression(
                exercise = Exercise.DEADLIFT,
                program = updated
            )
        }
    }

    fun increaseRdlProgramByFivePercent() {
        val state = uiState.value
        if (!state.rdlProgression.isReady) return
        val updated = state.settings.weeklyProgram.increaseRdlByPercent(5.0)
        viewModelScope.launch {
            settingsRepository.applyWeeklyProgression(
                exercise = Exercise.RDL,
                program = updated
            )
        }
    }

    fun setBarbellEquipment(equipment: BarbellEquipment) {
        viewModelScope.launch { settingsRepository.setBarbellEquipment(equipment) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(enabled) }
    }

    fun setCooldownAlarmEnabled(enabled: Boolean, onChanged: () -> Unit = {}) {
        viewModelScope.launch {
            settingsRepository.setCooldownAlarmEnabled(enabled)
            onChanged()
        }
    }

    fun setDeadliftCooldownMinutes(value: Int, onChanged: () -> Unit = {}) {
        viewModelScope.launch {
            settingsRepository.setCooldownMinutes(Exercise.DEADLIFT, value)
            onChanged()
        }
    }

    fun setRdlCooldownMinutes(value: Int, onChanged: () -> Unit = {}) {
        viewModelScope.launch {
            settingsRepository.setCooldownMinutes(Exercise.RDL, value)
            onChanged()
        }
    }

    fun setCrossExerciseCooldownMinutes(value: Int, onChanged: () -> Unit = {}) {
        viewModelScope.launch {
            val logs = trainingRepository.logs.first()
            settingsRepository.setCrossExerciseCooldownMinutes(
                minutes = value,
                lastDeadliftLogAtMillis = logs.filter {
                    it.exercise == Exercise.DEADLIFT.storedName
                }.maxOfOrNull { it.timestamp } ?: 0L,
                lastRdlLogAtMillis = logs.filter {
                    it.exercise == Exercise.RDL.storedName
                }.maxOfOrNull { it.timestamp } ?: 0L
            )
            onChanged()
        }
    }

    fun quickLog(
        exercise: Exercise,
        weightKg: Double,
        onLogged: (TrainingLogEntity) -> Unit,
        onBlocked: (Long) -> Unit
    ) {
        val plan = when (exercise) {
            Exercise.DEADLIFT -> uiState.value.todayPlan.deadlift
            Exercise.RDL -> uiState.value.todayPlan.rdl
        }
        if (plan.sets <= 0 || plan.reps <= 0) return

        val safeWeight = weightKg.coerceIn(0.0, 2000.0)
        viewModelScope.launch {
            val sourceTimestamp = System.currentTimeMillis()
            val acquired = settingsRepository.tryStartLogCooldown(
                exercise = exercise,
                sourceLogTimestamp = sourceTimestamp,
                nowMillis = sourceTimestamp
            )

            if (!acquired) {
                onBlocked(settingsRepository.cooldownRemainingMillis(exercise))
                return@launch
            }

            try {
                val logged = trainingRepository.logSet(
                    exercise = exercise,
                    reps = plan.reps,
                    weightKg = safeWeight,
                    timestamp = sourceTimestamp
                )
                onLogged(logged)
            } catch (error: Exception) {
                val previousLog = trainingRepository.logs.first()
                    .filter { it.exercise == exercise.storedName && it.timestamp < sourceTimestamp }
                    .maxOfOrNull { it.timestamp } ?: 0L
                settingsRepository.clearLogCooldownIfSource(
                    exercise, sourceTimestamp, previousLog
                )
                throw error
            }
        }
    }

    fun undoQuickLog(
        log: TrainingLogEntity,
        onUndone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            trainingRepository.delete(log)
            val exercise = Exercise.fromStoredName(log.exercise)
            val previousLog = trainingRepository.logs.first()
                .filter { it.exercise == exercise.storedName && it.timestamp < log.timestamp }
                .maxOfOrNull { it.timestamp } ?: 0L
            settingsRepository.clearLogCooldownIfSource(
                exercise, log.timestamp, previousLog
            )
            onUndone()
        }
    }

    fun updateLog(log: TrainingLogEntity, reps: Int, weightKg: Double) {
        viewModelScope.launch {
            trainingRepository.update(
                log.copy(
                    reps = reps.coerceAtLeast(1),
                    weightKg = weightKg.coerceAtLeast(0.0)
                )
            )
        }
    }

    fun deleteLog(log: TrainingLogEntity) {
        viewModelScope.launch {
            trainingRepository.delete(log)
            val exercise = Exercise.fromStoredName(log.exercise)
            val previousLog = trainingRepository.logs.first()
                .filter { it.exercise == exercise.storedName && it.timestamp < log.timestamp }
                .maxOfOrNull { it.timestamp } ?: 0L
            settingsRepository.clearLogCooldownIfSource(
                exercise, log.timestamp, previousLog
            )
        }
    }

    fun createBackupJson(): String {
        val state = uiState.value
        val settings = state.settings
        val root = JSONObject()
            .put("app", "GTG Strength")
            .put("version", 9)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "settings",
                JSONObject()
                    .put("deadliftOneRmKg", settings.deadliftOneRmKg.toDouble())
                    .put("rdlOneRmKg", settings.rdlOneRmKg.toDouble())
                    .put("intensityPercent", settings.intensityPercent.toDouble())
                    .put("deadliftTargetSets", settings.deadliftTargetSets)
                    .put("deadliftRepsPerSet", settings.deadliftRepsPerSet)
                    .put("rdlTargetSets", settings.rdlTargetSets)
                    .put("rdlRepsPerSet", settings.rdlRepsPerSet)
                    .put("keepScreenOn", settings.keepScreenOn)
                    .put("deadliftCooldownMinutes", settings.deadliftCooldownMinutes)
                    .put("rdlCooldownMinutes", settings.rdlCooldownMinutes)
                    .put("crossExerciseCooldownMinutes", settings.crossExerciseCooldownMinutes)
                    .put("cooldownAlarmEnabled", settings.cooldownAlarmEnabled)
                    .put("deadliftLastProgressionAtMillis", settings.deadliftLastProgressionAtMillis)
                    .put("rdlLastProgressionAtMillis", settings.rdlLastProgressionAtMillis)
            )
            .put("weeklyProgram", JSONArray(WeeklyProgramCodec.encode(settings.weeklyProgram)))
            .put("barbellEquipment", JSONObject(BarbellEquipmentCodec.encode(settings.barbellEquipment)))

        val logArray = JSONArray()
        state.logs.sortedBy { it.timestamp }.forEach { log ->
            logArray.put(
                JSONObject()
                    .put("exercise", log.exercise)
                    .put("reps", log.reps)
                    .put("weightKg", log.weightKg)
                    .put("timestamp", log.timestamp)
            )
        }
        root.put("logs", logArray)
        return root.toString(2)
    }

    fun restoreBackupJson(json: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(json)
                val version = root.optInt("version", -1)
                require(version in 1..9) { "Unsupported backup version" }
                val saved = root.getJSONObject("settings")
                val current = uiState.value.settings

                val restoredProgram = if (version >= 2 && root.has("weeklyProgram")) {
                    WeeklyProgramCodec.decode(root.getJSONArray("weeklyProgram").toString())
                } else {
                    current.weeklyProgram
                }

                val hasEquipment = version >= 3 && root.has("barbellEquipment")
                val restoredEquipment = if (hasEquipment) {
                    BarbellEquipmentCodec.decode(root.getJSONObject("barbellEquipment").toString())
                } else {
                    current.barbellEquipment
                }

                settingsRepository.replaceSettings(
                    TrainingSettings(
                        deadliftOneRmKg = saved.optDouble("deadliftOneRmKg", current.deadliftOneRmKg.toDouble()).toFloat(),
                        rdlOneRmKg = saved.optDouble("rdlOneRmKg", current.rdlOneRmKg.toDouble()).toFloat(),
                        intensityPercent = saved.optDouble("intensityPercent", current.intensityPercent.toDouble()).toFloat(),
                        deadliftTargetSets = saved.optInt("deadliftTargetSets", current.deadliftTargetSets),
                        deadliftRepsPerSet = saved.optInt("deadliftRepsPerSet", current.deadliftRepsPerSet),
                        rdlTargetSets = saved.optInt("rdlTargetSets", current.rdlTargetSets),
                        rdlRepsPerSet = saved.optInt("rdlRepsPerSet", current.rdlRepsPerSet),
                        weeklyProgram = restoredProgram,
                        barbellEquipment = restoredEquipment,
                        keepScreenOn = saved.optBoolean("keepScreenOn", current.keepScreenOn),
                        deadliftCooldownMinutes = saved.optInt(
                            "deadliftCooldownMinutes",
                            current.deadliftCooldownMinutes
                        ).coerceIn(0, 240),
                        rdlCooldownMinutes = saved.optInt(
                            "rdlCooldownMinutes",
                            current.rdlCooldownMinutes
                        ).coerceIn(0, 240),
                        crossExerciseCooldownMinutes = if (version >= 8) {
                            saved.optInt("crossExerciseCooldownMinutes", 0).coerceIn(0, 240)
                        } else {
                            current.crossExerciseCooldownMinutes
                        },
                        cooldownAlarmEnabled = if (version >= 9) {
                            saved.optBoolean("cooldownAlarmEnabled", false)
                        } else {
                            current.cooldownAlarmEnabled
                        },
                        deadliftLastProgressionAtMillis = if (version >= 7) {
                            saved.optLong("deadliftLastProgressionAtMillis", 0L).coerceAtLeast(0L)
                        } else {
                            0L
                        },
                        rdlLastProgressionAtMillis = if (version >= 7) {
                            saved.optLong("rdlLastProgressionAtMillis", 0L).coerceAtLeast(0L)
                        } else {
                            0L
                        }
                    )
                )

                uiState.value.logs.forEach { trainingRepository.delete(it) }

                val logs = root.optJSONArray("logs") ?: JSONArray()
                var restoredCount = 0
                for (index in 0 until logs.length()) {
                    val item = logs.optJSONObject(index) ?: continue
                    val exercise = when (item.optString("exercise")) {
                        Exercise.DEADLIFT.storedName -> Exercise.DEADLIFT
                        Exercise.RDL.storedName -> Exercise.RDL
                        else -> continue
                    }
                    val reps = item.optInt("reps", 0)
                    val weight = item.optDouble("weightKg", -1.0)
                    val timestamp = item.optLong("timestamp", 0L)
                    if (reps <= 0 || weight < 0.0 || timestamp <= 0L) continue

                    trainingRepository.logSet(
                        exercise = exercise,
                        reps = reps.coerceAtMost(100),
                        weightKg = weight.coerceAtMost(2000.0),
                        timestamp = timestamp
                    )
                    restoredCount += 1
                }

                manualRefresh.value += 1
                val equipmentMessage = if (hasEquipment) " Bar & plate stock restored." else ""
                onResult("Backup restored: $restoredCount saved sets loaded.$equipmentMessage")
            } catch (error: Exception) {
                onResult("Restore failed: ${error.message ?: "invalid backup file"}")
            }
        }
    }

    fun rebuildProgress(onResult: (String) -> Unit) {
        manualRefresh.value += 1
        onResult("Progress rebuilt from ${uiState.value.logs.size} saved sets.")
    }

    private fun logsForDate(
        logs: List<TrainingLogEntity>,
        date: LocalDate,
        zone: ZoneId
    ): List<TrainingLogEntity> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return logs.filter { it.timestamp in start until end }
    }

    private fun calculateWeekStats(
        logs: List<TrainingLogEntity>,
        startInclusive: Long,
        endExclusive: Long
    ): WeekStats {
        fun statsFor(exercise: Exercise): ExerciseWeekStats {
            val matching = logs.filter {
                it.exercise == exercise.storedName && it.timestamp in startInclusive until endExclusive
            }
            return ExerciseWeekStats(
                sets = matching.size,
                reps = matching.sumOf { it.reps },
                tonnageKg = matching.sumOf { it.reps * it.weightKg }
            )
        }

        return WeekStats(
            deadlift = statsFor(Exercise.DEADLIFT),
            rdl = statsFor(Exercise.RDL)
        )
    }

    class Factory(
        private val trainingRepository: TrainingRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GtgViewModel::class.java)) {
                return GtgViewModel(trainingRepository, settingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
