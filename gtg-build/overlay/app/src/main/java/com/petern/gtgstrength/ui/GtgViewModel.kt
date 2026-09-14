package com.petern.gtgstrength.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petern.gtgstrength.data.BackupCodec
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

    // Re-emits every minute so date/week summaries roll over correctly even
    // when the app stays open and no database row changes.
    private val dateRefresh = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    // Allows the user to explicitly rebuild all derived progress summaries.
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
     * The UI can then offer a 3-second Cancel action that deletes only this row.
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

    fun updateLog(
        log: TrainingLogEntity,
        reps: Int,
        weightKg: Double
    ) {
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
        }
    }

    fun createBackupJson(): String = BackupCodec.encode(
        settings = uiState.value.settings,
        logs = uiState.value.logs
    )

    fun restoreBackupJson(json: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val backup = BackupCodec.decode(json)
                settingsRepository.replaceSettings(backup.settings)
                trainingRepository.replaceAll(backup.logs)
                manualRefresh.value += 1
                onResult("Backup restored: ${backup.logs.size} saved sets loaded.")
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
