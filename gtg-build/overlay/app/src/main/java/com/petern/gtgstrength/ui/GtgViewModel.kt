package com.petern.gtgstrength.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petern.gtgstrength.data.SettingsRepository
import com.petern.gtgstrength.data.TrainingLogEntity
import com.petern.gtgstrength.data.TrainingRepository
import com.petern.gtgstrength.data.TrainingSettings
import com.petern.gtgstrength.domain.Exercise
import com.petern.gtgstrength.domain.ExerciseTarget
import com.petern.gtgstrength.domain.TrainingCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

data class GtgUiState(
    val settings: TrainingSettings = TrainingSettings(),
    val deadliftTarget: ExerciseTarget = TrainingCalculator.calculate(70f, 60f, 4, 5),
    val rdlTarget: ExerciseTarget = TrainingCalculator.calculate(50f, 60f, 4, 5),
    val deadliftSetsToday: Int = 0,
    val rdlSetsToday: Int = 0,
    val thisWeek: WeekStats = WeekStats(),
    val lastWeek: WeekStats = WeekStats(),
    val logs: List<TrainingLogEntity> = emptyList()
) {
    val combinedDailyReps: Int
        get() = deadliftTarget.totalReps + rdlTarget.totalReps

    val combinedDailyTonnageKg: Double
        get() = deadliftTarget.totalTonnageKg + rdlTarget.totalTonnageKg
}

class GtgViewModel(
    private val trainingRepository: TrainingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val dateRefresh = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    private val manualRefresh = MutableStateFlow(0L)

    val uiState = combine(
        settingsRepository.settings,
        trainingRepository.logs,
        dateRefresh,
        manualRefresh
    ) { settings, logs, _, _ ->
        val deadliftTarget = TrainingCalculator.calculate(
            oneRepMaxKg = settings.deadliftOneRmKg,
            intensityPercent = settings.intensityPercent,
            repsPerSet = settings.deadliftRepsPerSet,
            targetSets = settings.deadliftTargetSets
        )
        val rdlTarget = TrainingCalculator.calculate(
            oneRepMaxKg = settings.rdlOneRmKg,
            intensityPercent = settings.intensityPercent,
            repsPerSet = settings.rdlRepsPerSet,
            targetSets = settings.rdlTargetSets
        )

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayLogs = logs.filter { it.timestamp in startOfToday until startOfTomorrow }

        val startOfThisWeekDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startOfLastWeekDate = startOfThisWeekDate.minusWeeks(1)
        val startOfNextWeekDate = startOfThisWeekDate.plusWeeks(1)

        val startOfThisWeek = startOfThisWeekDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfLastWeek = startOfLastWeekDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfNextWeek = startOfNextWeekDate.atStartOfDay(zone).toInstant().toEpochMilli()

        GtgUiState(
            settings = settings,
            deadliftTarget = deadliftTarget,
            rdlTarget = rdlTarget,
            deadliftSetsToday = todayLogs.count { it.exercise == Exercise.DEADLIFT.storedName },
            rdlSetsToday = todayLogs.count { it.exercise == Exercise.RDL.storedName },
            thisWeek = calculateWeekStats(logs, startOfThisWeek, startOfNextWeek),
            lastWeek = calculateWeekStats(logs, startOfLastWeek, startOfThisWeek),
            logs = logs
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

    fun setDeadliftTargetSets(value: Int) {
        viewModelScope.launch { settingsRepository.setDeadliftTargetSets(value) }
    }

    fun setDeadliftRepsPerSet(value: Int) {
        viewModelScope.launch { settingsRepository.setDeadliftRepsPerSet(value) }
    }

    fun setRdlTargetSets(value: Int) {
        viewModelScope.launch { settingsRepository.setRdlTargetSets(value) }
    }

    fun setRdlRepsPerSet(value: Int) {
        viewModelScope.launch { settingsRepository.setRdlRepsPerSet(value) }
    }

    /**
     * Logs the set immediately and returns the exact inserted row to the UI.
     * The UI uses that row for the 3-second Cancel/undo action.
     */
    fun quickLog(
        exercise: Exercise,
        onLogged: (TrainingLogEntity) -> Unit
    ) {
        val state = uiState.value
        val target = when (exercise) {
            Exercise.DEADLIFT -> state.deadliftTarget
            Exercise.RDL -> state.rdlTarget
        }

        viewModelScope.launch {
            val logged = trainingRepository.logSet(
                exercise = exercise,
                reps = target.repsPerSet,
                weightKg = target.workingWeightKg
            )
            onLogged(logged)
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
        viewModelScope.launch { trainingRepository.delete(log) }
    }

    fun createBackupJson(): String {
        val state = uiState.value
        val settings = state.settings
        val root = JSONObject()
            .put("app", "GTG Strength")
            .put("version", 1)
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
            )

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
                require(root.optInt("version", -1) == 1) { "Unsupported backup version" }
                val settings = root.getJSONObject("settings")

                settingsRepository.setDeadliftOneRmKg(settings.optDouble("deadliftOneRmKg", 70.0).toFloat())
                settingsRepository.setRdlOneRmKg(settings.optDouble("rdlOneRmKg", 50.0).toFloat())
                settingsRepository.setIntensityPercent(settings.optDouble("intensityPercent", 60.0).toFloat())
                settingsRepository.setDeadliftTargetSets(settings.optInt("deadliftTargetSets", 5))
                settingsRepository.setDeadliftRepsPerSet(settings.optInt("deadliftRepsPerSet", 4))
                settingsRepository.setRdlTargetSets(settings.optInt("rdlTargetSets", 5))
                settingsRepository.setRdlRepsPerSet(settings.optInt("rdlRepsPerSet", 4))

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
                onResult("Backup restored: $restoredCount saved sets loaded.")
            } catch (error: Exception) {
                onResult("Restore failed: ${error.message ?: "invalid backup file"}")
            }
        }
    }

    fun rebuildProgress(onResult: (String) -> Unit) {
        manualRefresh.value += 1
        onResult("Progress rebuilt from ${uiState.value.logs.size} saved sets.")
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
