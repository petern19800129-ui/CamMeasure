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
    fun codecMigratesOldSaturdayTrainingToRestDay() {
        val legacyLikeProgram = """
            [
              {"day":6,"deadlift":{"sets":3,"reps":1,"minWeightKg":45,"maxWeightKg":45},"rdl":{"sets":1,"reps":6,"minWeightKg":25,"maxWeightKg":25}}
            ]
        """.trimIndent()

        val restored = WeeklyProgramCodec.decode(legacyLikeProgram)
        assertTrue(restored.forDay(DayOfWeek.SATURDAY).isRestDay)
    }
}
