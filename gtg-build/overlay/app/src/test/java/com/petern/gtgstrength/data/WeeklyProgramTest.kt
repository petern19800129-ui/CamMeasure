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
}
