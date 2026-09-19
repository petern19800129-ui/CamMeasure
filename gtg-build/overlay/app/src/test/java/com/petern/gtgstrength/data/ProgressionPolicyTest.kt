package com.petern.gtgstrength.data

import com.petern.gtgstrength.domain.Exercise
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionPolicyTest {
    private val zone = ZoneId.of("UTC")
    private val program = WeeklyProgram.default()
    private val today = LocalDate.of(2026, 9, 19) // Saturday
    private val currentWeek = LocalDate.of(2026, 9, 13)
    private val previousWeek = currentWeek.minusWeeks(1)

    @Test
    fun twoConsecutiveFullWeeksUnlockProgression() {
        val logs = fullWeek(previousWeek, Exercise.DEADLIFT) +
            fullWeek(currentWeek, Exercise.DEADLIFT)

        val result = evaluate(logs)

        assertTrue(result.isReady)
        assertEquals(2, result.consecutiveFullWeeks)
        assertEquals(100, result.latestCompletedWeekPercent)
    }

    @Test
    fun ninetyPercentLatestWeekResetsStreakToZero() {
        val logs = fullWeek(previousWeek, Exercise.DEADLIFT) +
            weekWithLoggedSetCount(currentWeek, Exercise.DEADLIFT, 18)

        val result = evaluate(logs)

        assertFalse(result.isReady)
        assertEquals(0, result.consecutiveFullWeeks)
        assertEquals(90, result.latestCompletedWeekPercent)
    }

    @Test
    fun fullWeekAfterIncompleteWeekCountsAsOneOfTwo() {
        val logs = weekWithLoggedSetCount(previousWeek, Exercise.DEADLIFT, 18) +
            fullWeek(currentWeek, Exercise.DEADLIFT)

        val result = evaluate(logs)

        assertFalse(result.isReady)
        assertEquals(1, result.consecutiveFullWeeks)
        assertEquals(100, result.latestCompletedWeekPercent)
    }

    @Test
    fun extraSetsOnOneDayCannotHideMissedSetOnAnotherDay() {
        val logs = fullWeek(currentWeek, Exercise.DEADLIFT).toMutableList()
        val sundayStart = currentWeek.atStartOfDay(zone).toInstant().toEpochMilli()
        val sundayEnd = currentWeek.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val removedIndex = logs.indexOfFirst { it.timestamp in sundayStart until sundayEnd }
        logs.removeAt(removedIndex)

        val mondayNoon = currentWeek.plusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        logs += ProgressionLog(Exercise.DEADLIFT.storedName, mondayNoon + 30_000L)

        val result = evaluate(logs)

        assertFalse(result.isReady)
        assertEquals(0, result.consecutiveFullWeeks)
        assertEquals(95, result.latestCompletedWeekPercent)
    }

    @Test
    fun progressionAppliedThisWeekStartsQualificationOver() {
        val logs = fullWeek(previousWeek, Exercise.DEADLIFT) +
            fullWeek(currentWeek, Exercise.DEADLIFT)
        val progressionAt = currentWeek
            .plusDays(6)
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val result = ProgressionPolicy.evaluate(
            program = program,
            logs = logs,
            exercise = Exercise.DEADLIFT,
            today = today,
            zone = zone,
            lastProgressionAtMillis = progressionAt
        )

        assertFalse(result.isReady)
        assertEquals(0, result.consecutiveFullWeeks)
        assertEquals(null, result.latestCompletedWeekPercent)
    }

    private fun evaluate(logs: List<ProgressionLog>): ProgressionReadiness =
        ProgressionPolicy.evaluate(
            program = program,
            logs = logs,
            exercise = Exercise.DEADLIFT,
            today = today,
            zone = zone,
            lastProgressionAtMillis = 0L
        )

    private fun fullWeek(
        weekStart: LocalDate,
        exercise: Exercise
    ): List<ProgressionLog> {
        val result = mutableListOf<ProgressionLog>()
        for (offset in 0L..6L) {
            val date = weekStart.plusDays(offset)
            val dayPlan = program.forDay(date.dayOfWeek)
            val targetSets = when (exercise) {
                Exercise.DEADLIFT -> dayPlan.deadlift.sets
                Exercise.RDL -> dayPlan.rdl.sets
            }
            repeat(targetSets) { index ->
                val timestamp = date
                    .atTime(12, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli() + index * 1_000L
                result += ProgressionLog(exercise.storedName, timestamp)
            }
        }
        return result
    }

    private fun weekWithLoggedSetCount(
        weekStart: LocalDate,
        exercise: Exercise,
        count: Int
    ): List<ProgressionLog> = fullWeek(weekStart, exercise).take(count)
}
