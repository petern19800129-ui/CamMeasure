package com.petern.gtgstrength.data

import com.petern.gtgstrength.domain.Exercise
import kotlinx.coroutines.flow.Flow

class TrainingRepository(
    private val dao: TrainingLogDao
) {
    val logs: Flow<List<TrainingLogEntity>> = dao.observeAll()

    /**
     * Logs one set and returns the exact persisted row, including its Room ID.
     * Returning the inserted row lets the UI offer a safe, precise 3-second undo.
     */
    suspend fun logSet(
        exercise: Exercise,
        reps: Int,
        weightKg: Double,
        timestamp: Long = System.currentTimeMillis()
    ): TrainingLogEntity {
        val pending = TrainingLogEntity(
            exercise = exercise.storedName,
            reps = reps.coerceAtLeast(1),
            weightKg = weightKg.coerceAtLeast(0.0),
            timestamp = timestamp
        )
        val id = dao.insert(pending)
        return pending.copy(id = id)
    }

    suspend fun update(log: TrainingLogEntity) {
        dao.update(log)
    }

    suspend fun delete(log: TrainingLogEntity) {
        dao.delete(log)
    }

    /** Replace all saved training history with a decoded backup. */
    suspend fun replaceAll(logs: List<TrainingLogEntity>) {
        dao.deleteAll()
        if (logs.isNotEmpty()) {
            dao.insertAll(logs.map { it.copy(id = 0) })
        }
    }
}
