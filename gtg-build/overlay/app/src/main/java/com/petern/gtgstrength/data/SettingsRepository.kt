package com.petern.gtgstrength.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.trainingSettingsDataStore by preferencesDataStore(
    name = "training_settings"
)

data class TrainingSettings(
    val deadliftOneRmKg: Float = 70f,
    val rdlOneRmKg: Float = 50f,
    val intensityPercent: Float = 60f,
    val deadliftTargetSets: Int = 5,
    val deadliftRepsPerSet: Int = 4,
    val rdlTargetSets: Int = 5,
    val rdlRepsPerSet: Int = 4
)

class SettingsRepository(
    private val context: Context
) {
    private object Keys {
        val deadliftOneRmKg = floatPreferencesKey("deadlift_one_rm_kg")
        val rdlOneRmKg = floatPreferencesKey("rdl_one_rm_kg")
        val intensityPercent = floatPreferencesKey("intensity_percent")

        // Legacy shared keys are kept only for migration/fallback so existing
        // installs retain the user's current values after upgrading.
        val legacyTargetSets = intPreferencesKey("target_sets")
        val legacyRepsPerSet = intPreferencesKey("reps_per_set")

        val deadliftTargetSets = intPreferencesKey("deadlift_target_sets")
        val deadliftRepsPerSet = intPreferencesKey("deadlift_reps_per_set")
        val rdlTargetSets = intPreferencesKey("rdl_target_sets")
        val rdlRepsPerSet = intPreferencesKey("rdl_reps_per_set")
    }

    val settings: Flow<TrainingSettings> = context.trainingSettingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(::preferencesToSettings)

    suspend fun setDeadliftOneRmKg(value: Float) {
        context.trainingSettingsDataStore.edit { preferences ->
            preferences[Keys.deadliftOneRmKg] = value.coerceIn(0f, 1000f)
        }
    }

    suspend fun setRdlOneRmKg(value: Float) {
        context.trainingSettingsDataStore.edit { preferences ->
            preferences[Keys.rdlOneRmKg] = value.coerceIn(0f, 1000f)
        }
    }

    suspend fun setIntensityPercent(value: Float) {
        context.trainingSettingsDataStore.edit { preferences ->
            preferences[Keys.intensityPercent] = value.coerceIn(50f, 70f)
        }
    }

    suspend fun setDeadliftTargetSets(value: Int) {
        context.trainingSettingsDataStore.edit { preferences ->
            preferences[Keys.deadliftTargetSets] = value.coerceIn(1, 20)
        }
    }

    suspend fun setDeadliftRepsPerSet(value: Int) {
        context.trainingSettingsDataStore.edit { preferences ->
            preferences[Keys.deadliftRepsPerSet] = value.coerceIn(3, 5)
        }
    }

    suspend fun setRdlTargetSets(value: Int) {
        context.trainingSettingsDataStore.edit { preferences ->
            preferences[Keys.rdlTargetSets] = value.coerceIn(1, 20)
        }
    }

    suspend fun setRdlRepsPerSet(value: Int) {
        context.trainingSettingsDataStore.edit { preferences ->
            preferences[Keys.rdlRepsPerSet] = value.coerceIn(3, 5)
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
            rdlRepsPerSet = preferences[Keys.rdlRepsPerSet] ?: legacyReps
        )
    }
}
