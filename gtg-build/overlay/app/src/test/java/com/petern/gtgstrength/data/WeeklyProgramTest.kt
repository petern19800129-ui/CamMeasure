package com.petern.gtgstrength.data

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyProgramTest {

    @Test
    fun weekOrderStartsSundayAndEndsSaturday() {
        assertEquals(DayOfWeek.SUNDAY, WeeklyProgram.WEEK_ORDER.first())
        assertEquals(DayOfWeek.SATURDAY, WeeklyProgram.WEEK_ORDER.last())
    }

    @Test
    fun saturdayIsAlwaysRestDay() {
        val program = WeeklyProgram.default().replacing(
            DayPlan(
                DayOfWeek.SATURDAY,
                PlannedExercise(5, 5, 100.0),
                PlannedExercise(5, 5, 100.0)
            )
        )

        val saturday = program.forDay(DayOfWeek.SATURDAY)
        assertTrue(saturday.isRestDay)
        assertEquals(0, saturday.deadlift.sets)
        assertEquals(0, saturday.rdl.sets)
    }

    @Test
    fun saturdayDoesNotCountTowardWeeklyTargets() {
        val program = WeeklyProgram.default().replacing(
            DayPlan(
                DayOfWeek.SATURDAY,
                PlannedExercise(10, 10, 100.0),
                PlannedExercise(10, 10, 100.0)
            )
        )

        val expectedDeadlift = WeeklyProgram.WEEK_ORDER
            .filter { it != DayOfWeek.SATURDAY }
            .sumOf { program.forDay(it).deadlift.sets }
        val expectedRdl = WeeklyProgram.WEEK_ORDER
            .filter { it != DayOfWeek.SATURDAY }
            .sumOf { program.forDay(it).rdl.sets }

        assertEquals(expectedDeadlift, program.deadliftWeeklySets)
        assertEquals(expectedRdl, program.rdlWeeklySets)
    }

    @Test
    fun deadliftFivePercentProgressionChangesOnlyDeadliftWeights() {
        val original = WeeklyProgram.default()
        val progressed = original.increaseDeadliftByPercent(5.0)

        assertEquals(42.0, progressed.forDay(DayOfWeek.MONDAY).deadlift.minWeightKg, 0.001)
        assertEquals(25.0, progressed.forDay(DayOfWeek.MONDAY).rdl.minWeightKg, 0.001)
        assertEquals(
            original.forDay(DayOfWeek.MONDAY).deadlift.sets,
            progressed.forDay(DayOfWeek.MONDAY).deadlift.sets
        )
        assertEquals(
            original.forDay(DayOfWeek.MONDAY).deadlift.reps,
            progressed.forDay(DayOfWeek.MONDAY).deadlift.reps
        )
    }

    @Test
    fun progressionPreservesWeightRangesAndRoundsToHundredthKg() {
        val progressed = WeeklyProgram.default().increaseRdlByPercent(5.0)
        val thursday = progressed.forDay(DayOfWeek.THURSDAY).rdl

        assertEquals(21.0, thursday.minWeightKg, 0.001)
        assertEquals(26.25, thursday.maxWeightKg, 0.001)
        assertTrue(thursday.isRange)
    }

    @Test
    fun progressionNeverChangesSaturdayRestDay() {
        val progressed = WeeklyProgram.default()
            .increaseDeadliftByPercent(5.0)
            .increaseRdlByPercent(5.0)

        val saturday = progressed.forDay(DayOfWeek.SATURDAY)
        assertTrue(saturday.isRestDay)
        assertEquals(0, saturday.deadlift.sets)
        assertEquals(0, saturday.rdl.sets)
    }
}
