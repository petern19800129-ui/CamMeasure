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
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.trainingSettingsDataStore by preferencesDataStore(
    name = "training_settings"
)

const val LOG_COOLDOWN_MILLIS: Long = 60L * 60L * 1000L

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
    // Transient training state. Intentionally not included in backup export.
    val nextLogAllowedAtMillis: Long = 0L,
    val cooldownSourceLogTimestamp: Long = 0L
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
        val nextLogAllowedAtMillis = longPreferencesKey("next_log_allowed_at_millis")
        val cooldownSourceLogTimestamp = longPreferencesKey("cooldown_source_log_timestamp")
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

    /**
     * Atomically reserves the one-hour logging window.
     * Returns false when another app/widget log is still inside the cooldown.
     */
    suspend fun tryStartLogCooldown(
        sourceLogTimestamp: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        var acquired = false
        context.trainingSettingsDataStore.edit { preferences ->
            val currentUntil = preferences[Keys.nextLogAllowedAtMillis] ?: 0L
            if (currentUntil <= nowMillis) {
                preferences[Keys.nextLogAllowedAtMillis] = nowMillis + LOG_COOLDOWN_MILLIS
                preferences[Keys.cooldownSourceLogTimestamp] = sourceLogTimestamp
                acquired = true
            }
        }
        return acquired
    }

    /**
     * Clears the cooldown only when it belongs to the supplied just-created log.
     * This makes the 3-second Cancel action safe without affecting later logs.
     */
    suspend fun clearLogCooldownIfSource(sourceLogTimestamp: Long) {
        context.trainingSettingsDataStore.edit { preferences ->
            val source = preferences[Keys.cooldownSourceLogTimestamp] ?: 0L
            if (source == sourceLogTimestamp) {
                preferences[Keys.nextLogAllowedAtMillis] = 0L
                preferences[Keys.cooldownSourceLogTimestamp] = 0L
            }
        }
    }

    /**
     * Restore the complete settings snapshot in a single DataStore transaction.
     * This prevents one restored section (notably bar/plate stock) from being lost
     * between several independent preference edits.
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
            nextLogAllowedAtMillis = preferences[Keys.nextLogAllowedAtMillis] ?: 0L,
            cooldownSourceLogTimestamp = preferences[Keys.cooldownSourceLogTimestamp] ?: 0L
        )
    }
}
