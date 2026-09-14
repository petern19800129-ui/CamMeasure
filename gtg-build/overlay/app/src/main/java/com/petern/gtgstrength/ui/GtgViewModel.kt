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
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


data class GtgUiState(
    val settings: TrainingSettings = TrainingSettings(),
    val deadliftTarget: ExerciseTarget = TrainingCalculator.calculate(70f, 60f, 4, 5),
    val rdlTarget: ExerciseTarget = TrainingCalculator.calculate(50f, 60f, 4, 5),
    val deadliftSetsToday: Int = 0,
    val rdlSetsToday: Int = 0,
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

    // Re-emits every minute so "today" rolls over correctly at midnight even
    // when the user leaves the app open and no database row changes.
    private val dayRefresh = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val uiState = combine(
        settingsRepository.settings,
        trainingRepository.logs,
        dayRefresh
    ) { settings, logs, _ ->
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

        GtgUiState(
            settings = settings,
            deadliftTarget = deadliftTarget,
            rdlTarget = rdlTarget,
            deadliftSetsToday = todayLogs.count { it.exercise == Exercise.DEADLIFT.storedName },
            rdlSetsToday = todayLogs.count { it.exercise == Exercise.RDL.storedName },
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

    fun quickLog(exercise: Exercise) {
        val state = uiState.value
        val target = when (exercise) {
            Exercise.DEADLIFT -> state.deadliftTarget
            Exercise.RDL -> state.rdlTarget
        }

        viewModelScope.launch {
            trainingRepository.logSet(
                exercise = exercise,
                reps = target.repsPerSet,
                weightKg = target.workingWeightKg
            )
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
