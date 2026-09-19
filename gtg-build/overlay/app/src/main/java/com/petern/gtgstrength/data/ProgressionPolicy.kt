package com.petern.gtgstrength.data

import com.petern.gtgstrength.domain.Exercise
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

private const val REQUIRED_FULL_WEEKS = 2

data class ProgressionLog(
    val exerciseStoredName: String,
    val timestamp: Long
)

data class ProgressionReadiness(
    val consecutiveFullWeeks: Int = 0,
    val latestCompletedWeekPercent: Int? = null,
    val latestCompletedWeekStart: LocalDate? = null
) {
    val requiredFullWeeks: Int get() = REQUIRED_FULL_WEEKS
    val isReady: Boolean get() = consecutiveFullWeeks >= REQUIRED_FULL_WEEKS
}

object ProgressionPolicy {
    fun evaluate(
        program: WeeklyProgram,
        logs: List<ProgressionLog>,
        exercise: Exercise,
        today: LocalDate,
        zone: ZoneId,
        lastProgressionAtMillis: Long
    ): ProgressionReadiness {
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val newestCompletedWeekStart =
            if (today.dayOfWeek == DayOfWeek.SATURDAY) thisWeekStart
            else thisWeekStart.minusWeeks(1)

        val progressionWeekStart = if (lastProgressionAtMillis > 0L) {
            Instant.ofEpochMilli(lastProgressionAtMillis)
                .atZone(zone)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        } else {
            null
        }

        var cursor = newestCompletedWeekStart
        var consecutive = 0
        var latestPercent: Int? = null
        var latestStart: LocalDate? = null

        while (progressionWeekStart == null || cursor.isAfter(progressionWeekStart)) {
            val result = completionForWeek(program, logs, exercise, cursor, zone)
            if (latestPercent == null) {
                latestPercent = result.percent
                latestStart = cursor
            }

            if (!result.full) break
            consecutive += 1
            if (consecutive >= REQUIRED_FULL_WEEKS) break
            cursor = cursor.minusWeeks(1)
        }

        return ProgressionReadiness(
            consecutiveFullWeeks = consecutive,
            latestCompletedWeekPercent = latestPercent,
            latestCompletedWeekStart = latestStart
        )
    }

    private data class WeekCompletion(
        val completedSets: Int,
        val plannedSets: Int
    ) {
        val percent: Int
            get() = if (plannedSets <= 0) 100
            else ((completedSets * 100.0) / plannedSets).roundToInt().coerceIn(0, 100)
        val full: Boolean get() = plannedSets > 0 && completedSets >= plannedSets
    }

    private fun completionForWeek(
        program: WeeklyProgram,
        logs: List<ProgressionLog>,
        exercise: Exercise,
        weekStart: LocalDate,
        zone: ZoneId
    ): WeekCompletion {
        var plannedTotal = 0
        var completedTotal = 0

        for (offset in 0L..6L) {
            val date = weekStart.plusDays(offset)
            val dayPlan = program.forDay(date.dayOfWeek)
            val plannedSets = when (exercise) {
                Exercise.DEADLIFT -> dayPlan.deadlift.sets
                Exercise.RDL -> dayPlan.rdl.sets
            }
            if (plannedSets <= 0) continue

            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val loggedSets = logs.count {
                it.exerciseStoredName == exercise.storedName &&
                    it.timestamp >= start &&
                    it.timestamp < end
            }

            plannedTotal += plannedSets
            // Extra sets on another day cannot compensate for a missed programmed set.
            completedTotal += loggedSets.coerceAtMost(plannedSets)
        }

        return WeekCompletion(
            completedSets = completedTotal,
            plannedSets = plannedTotal
        )
    }
}
