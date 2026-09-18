package com.petern.gtgstrength.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.petern.gtgstrength.domain.Exercise
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.trainingSettingsDataStore by preferencesDataStore(
    name = "training_settings"
)

data class TrainingSettings(
    val deadliftOneRmKg: Float = 70f,
    val rdlOneRmKg: Float = 50f,
    val intensityPercent: Float = 60f,
    // Kept for compatibility with older backups/installations.
    val deadliftTargetSets: Int = 5,
    val deadliftRepsPerSet: Int = 4,
    val rdlTargetSets: Int = 5,
    val rdlRepsPerSet: Int = 4,
    val weeklyProgram: WeeklyProgram = WeeklyProgram.default(),
    val barbellEquipment: BarbellEquipment = BarbellEquipment.default(),
    val keepScreenOn: Boolean = false,
    val deadliftCooldownMinutes: Int = 60,
    val rdlCooldownMinutes: Int = 60,
    // Transient training state. Intentionally not included in backup export.
    val deadliftNextLogAllowedAtMillis: Long = 0L,
    val rdlNextLogAllowedAtMillis: Long = 0L,
    val deadliftCooldownSourceLogTimestamp: Long = 0L,
    val rdlCooldownSourceLogTimestamp: Long = 0L
)

class SettingsRepository(
    private val context: Context
) {
    private object Keys {
        val deadliftOneRmKg = floatPreferencesKey("deadlift_one_rm_kg")
        val rdlOneRmKg = floatPreferencesKey("rdl_one_rm_kg")
        val intensityPercent = floatPreferencesKey("intensity_percent")

        val legacyTargetSets = intPreferencesKey("target_sets")
        val legacyRepsPerSet = intPreferencesKey("reps_per_set")
        val deadliftTargetSets = intPreferencesKey("deadlift_target_sets")
        val deadliftRepsPerSet = intPreferencesKey("deadlift_reps_per_set")
        val rdlTargetSets = intPreferencesKey("rdl_target_sets")
        val rdlRepsPerSet = intPreferencesKey("rdl_reps_per_set")

        val weeklyProgram = stringPreferencesKey("weekly_program_v1")
        val barbellEquipment = stringPreferencesKey("barbell_equipment_v1")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val deadliftCooldownMinutes = intPreferencesKey("deadlift_cooldown_minutes")
        val rdlCooldownMinutes = intPreferencesKey("rdl_cooldown_minutes")

        val deadliftNextLogAllowedAtMillis = longPreferencesKey("deadlift_next_log_allowed_at_millis")
        val rdlNextLogAllowedAtMillis = longPreferencesKey("rdl_next_log_allowed_at_millis")
        val deadliftCooldownSourceLogTimestamp = longPreferencesKey("deadlift_cooldown_source_log_timestamp")
        val rdlCooldownSourceLogTimestamp = longPreferencesKey("rdl_cooldown_source_log_timestamp")
    }

    val settings: Flow<TrainingSettings> = context.trainingSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::preferencesToSettings)

    suspend fun setDeadliftOneRmKg(value: Float) {
        context.trainingSettingsDataStore.edit {
            it[Keys.deadliftOneRmKg] = value.coerceIn(0f, 1000f)
        }
    }

    suspend fun setRdlOneRmKg(value: Float) {
        context.trainingSettingsDataStore.edit {
            it[Keys.rdlOneRmKg] = value.coerceIn(0f, 1000f)
        }
    }

    suspend fun setIntensityPercent(value: Float) {
        context.trainingSettingsDataStore.edit {
            it[Keys.intensityPercent] = value.coerceIn(50f, 70f)
        }
    }

    suspend fun setWeeklyProgram(program: WeeklyProgram) {
        context.trainingSettingsDataStore.edit {
            it[Keys.weeklyProgram] = WeeklyProgramCodec.encode(program)
        }
    }

    suspend fun updateDayPlan(dayPlan: DayPlan) {
        val current = settings.first().weeklyProgram
        setWeeklyProgram(current.replacing(dayPlan))
    }

    suspend fun setBarbellEquipment(equipment: BarbellEquipment) {
        context.trainingSettingsDataStore.edit {
            it[Keys.barbellEquipment] = BarbellEquipmentCodec.encode(equipment)
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.trainingSettingsDataStore.edit {
            it[Keys.keepScreenOn] = enabled
        }
    }

    suspend fun setCooldownMinutes(exercise: Exercise, minutes: Int) {
        val safeMinutes = minutes.coerceIn(0, 720)
        context.trainingSettingsDataStore.edit { preferences ->
            val durationKey = cooldownMinutesKey(exercise)
            val nextKey = nextAllowedKey(exercise)
            val sourceKey = sourceTimestampKey(exercise)
            preferences[durationKey] = safeMinutes

            val sourceTimestamp = preferences[sourceKey] ?: 0L
            if (sourceTimestamp > 0L) {
                preferences[nextKey] = sourceTimestamp + safeMinutes * 60_000L
            }
        }
    }

    /**
     * Atomically reserves the one-hour logging window for one exercise.
     * Deadlift and RDL have independent cooldowns, so one of each may be logged
     * during the same hour.
     */
    suspend fun tryStartLogCooldown(
        exercise: Exercise,
        sourceLogTimestamp: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        var acquired = false
        context.trainingSettingsDataStore.edit { preferences ->
            val nextKey = nextAllowedKey(exercise)
            val sourceKey = sourceTimestampKey(exercise)
            val currentUntil = preferences[nextKey] ?: 0L
            if (currentUntil <= nowMillis) {
                val durationMinutes = preferences[cooldownMinutesKey(exercise)] ?: 60
                preferences[nextKey] = nowMillis + durationMinutes.coerceIn(0, 720) * 60_000L
                preferences[sourceKey] = sourceLogTimestamp
                acquired = true
            }
        }
        return acquired
    }

    /**
     * Clears only the matching exercise cooldown and only when it belongs to the
     * supplied just-created log. This keeps the 3-second Cancel action precise.
     */
    suspend fun clearLogCooldownIfSource(
        exercise: Exercise,
        sourceLogTimestamp: Long
    ) {
        context.trainingSettingsDataStore.edit { preferences ->
            val nextKey = nextAllowedKey(exercise)
            val sourceKey = sourceTimestampKey(exercise)
            val source = preferences[sourceKey] ?: 0L
            if (source == sourceLogTimestamp) {
                preferences[nextKey] = 0L
                preferences[sourceKey] = 0L
            }
        }
    }

    suspend fun cooldownRemainingMillis(
        exercise: Exercise,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val current = settings.first()
        val nextAllowed = when (exercise) {
            Exercise.DEADLIFT -> current.deadliftNextLogAllowedAtMillis
            Exercise.RDL -> current.rdlNextLogAllowedAtMillis
        }
        return (nextAllowed - nowMillis).coerceAtLeast(0L)
    }

    /**
     * Restore the complete settings snapshot in a single DataStore transaction.
     * Cooldown state is intentionally left alone because it is transient.
     */
    suspend fun replaceSettings(value: TrainingSettings) {
        val equipment = value.barbellEquipment.normalized()
        context.trainingSettingsDataStore.edit { preferences ->
            preferences[Keys.deadliftOneRmKg] = value.deadliftOneRmKg.coerceIn(0f, 1000f)
            preferences[Keys.rdlOneRmKg] = value.rdlOneRmKg.coerceIn(0f, 1000f)
            preferences[Keys.intensityPercent] = value.intensityPercent.coerceIn(50f, 70f)
            preferences[Keys.deadliftTargetSets] = value.deadliftTargetSets.coerceIn(1, 20)
            preferences[Keys.deadliftRepsPerSet] = value.deadliftRepsPerSet.coerceIn(1, 50)
            preferences[Keys.rdlTargetSets] = value.rdlTargetSets.coerceIn(1, 20)
            preferences[Keys.rdlRepsPerSet] = value.rdlRepsPerSet.coerceIn(1, 50)
            preferences[Keys.weeklyProgram] = WeeklyProgramCodec.encode(value.weeklyProgram)
            preferences[Keys.barbellEquipment] = BarbellEquipmentCodec.encode(equipment)
            preferences[Keys.keepScreenOn] = value.keepScreenOn
            preferences[Keys.deadliftCooldownMinutes] = value.deadliftCooldownMinutes.coerceIn(0, 720)
            preferences[Keys.rdlCooldownMinutes] = value.rdlCooldownMinutes.coerceIn(0, 720)
        }
    }

    // Legacy setters remain so old backup files can still be restored safely.
    suspend fun setDeadliftTargetSets(value: Int) {
        context.trainingSettingsDataStore.edit {
            it[Keys.deadliftTargetSets] = value.coerceIn(1, 20)
        }
    }

    suspend fun setDeadliftRepsPerSet(value: Int) {
        context.trainingSettingsDataStore.edit {
            it[Keys.deadliftRepsPerSet] = value.coerceIn(1, 50)
        }
    }

    suspend fun setRdlTargetSets(value: Int) {
        context.trainingSettingsDataStore.edit {
            it[Keys.rdlTargetSets] = value.coerceIn(1, 20)
        }
    }

    suspend fun setRdlRepsPerSet(value: Int) {
        context.trainingSettingsDataStore.edit {
            it[Keys.rdlRepsPerSet] = value.coerceIn(1, 50)
        }
    }

    private fun cooldownMinutesKey(exercise: Exercise) = when (exercise) {
        Exercise.DEADLIFT -> Keys.deadliftCooldownMinutes
        Exercise.RDL -> Keys.rdlCooldownMinutes
    }

    private fun nextAllowedKey(exercise: Exercise) = when (exercise) {
        Exercise.DEADLIFT -> Keys.deadliftNextLogAllowedAtMillis
        Exercise.RDL -> Keys.rdlNextLogAllowedAtMillis
    }

    private fun sourceTimestampKey(exercise: Exercise) = when (exercise) {
        Exercise.DEADLIFT -> Keys.deadliftCooldownSourceLogTimestamp
        Exercise.RDL -> Keys.rdlCooldownSourceLogTimestamp
    }

    private fun preferencesToSettings(preferences: Preferences): TrainingSettings {
        val legacySets = preferences[Keys.legacyTargetSets] ?: 5
        val legacyReps = preferences[Keys.legacyRepsPerSet] ?: 4
        return TrainingSettings(
            deadliftOneRmKg = preferences[Keys.deadliftOneRmKg] ?: 70f,
            rdlOneRmKg = preferences[Keys.rdlOneRmKg] ?: 50f,
            intensityPercent = preferences[Keys.intensityPercent] ?: 60f,
            deadliftTargetSets = preferences[Keys.deadliftTargetSets] ?: legacySets,
            deadliftRepsPerSet = preferences[Keys.deadliftRepsPerSet] ?: legacyReps,
            rdlTargetSets = preferences[Keys.rdlTargetSets] ?: legacySets,
            rdlRepsPerSet = preferences[Keys.rdlRepsPerSet] ?: legacyReps,
            weeklyProgram = WeeklyProgramCodec.decode(preferences[Keys.weeklyProgram]),
            barbellEquipment = BarbellEquipmentCodec.decode(preferences[Keys.barbellEquipment]),
            keepScreenOn = preferences[Keys.keepScreenOn] ?: false,
            deadliftCooldownMinutes = preferences[Keys.deadliftCooldownMinutes] ?: 60,
            rdlCooldownMinutes = preferences[Keys.rdlCooldownMinutes] ?: 60,
            deadliftNextLogAllowedAtMillis = preferences[Keys.deadliftNextLogAllowedAtMillis] ?: 0L,
            rdlNextLogAllowedAtMillis = preferences[Keys.rdlNextLogAllowedAtMillis] ?: 0L,
            deadliftCooldownSourceLogTimestamp = preferences[Keys.deadliftCooldownSourceLogTimestamp] ?: 0L,
            rdlCooldownSourceLogTimestamp = preferences[Keys.rdlCooldownSourceLogTimestamp] ?: 0L
        )
    }
}
