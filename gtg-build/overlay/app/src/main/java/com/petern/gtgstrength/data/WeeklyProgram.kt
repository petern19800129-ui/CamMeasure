package com.petern.gtgstrength.data

import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject

data class PlannedExercise(
    val sets: Int,
    val reps: Int,
    val minWeightKg: Double,
    val maxWeightKg: Double = minWeightKg
) {
    val isRange: Boolean get() = maxWeightKg > minWeightKg + 0.001
    fun normalized(): PlannedExercise {
        val low = minWeightKg.coerceIn(0.0, 2000.0)
        val high = maxWeightKg.coerceIn(low, 2000.0)
        return copy(
            sets = sets.coerceIn(1, 20),
            reps = reps.coerceIn(1, 50),
            minWeightKg = low,
            maxWeightKg = high
        )
    }
}

data class DayPlan(
    val dayOfWeek: DayOfWeek,
    val deadlift: PlannedExercise,
    val rdl: PlannedExercise
)

data class WeeklyProgram(val days: List<DayPlan>) {
    fun forDay(day: DayOfWeek): DayPlan =
        days.firstOrNull { it.dayOfWeek == day }
            ?: default().days.first { it.dayOfWeek == day }

    val deadliftWeeklySets: Int get() = days.sumOf { it.deadlift.sets }
    val rdlWeeklySets: Int get() = days.sumOf { it.rdl.sets }

    fun replacing(dayPlan: DayPlan): WeeklyProgram = WeeklyProgram(
        days = DayOfWeek.entries.map { day ->
            if (day == dayPlan.dayOfWeek) dayPlan else forDay(day)
        }
    )

    companion object {
        fun default(): WeeklyProgram = WeeklyProgram(
            listOf(
                DayPlan(DayOfWeek.MONDAY, PlannedExercise(4, 1, 40.0), PlannedExercise(2, 6, 25.0)),
                DayPlan(DayOfWeek.TUESDAY, PlannedExercise(3, 1, 35.0), PlannedExercise(1, 8, 25.0)),
                DayPlan(DayOfWeek.WEDNESDAY, PlannedExercise(4, 1, 45.0), PlannedExercise(2, 6, 30.0)),
                DayPlan(DayOfWeek.THURSDAY, PlannedExercise(3, 1, 35.0), PlannedExercise(1, 8, 20.0, 25.0)),
                DayPlan(DayOfWeek.FRIDAY, PlannedExercise(4, 1, 40.0), PlannedExercise(2, 6, 30.0)),
                DayPlan(DayOfWeek.SATURDAY, PlannedExercise(3, 1, 45.0), PlannedExercise(1, 6, 25.0)),
                DayPlan(DayOfWeek.SUNDAY, PlannedExercise(2, 1, 30.0, 35.0), PlannedExercise(1, 8, 20.0))
            )
        )
    }
}

object WeeklyProgramCodec {
    fun encode(program: WeeklyProgram): String {
        val array = JSONArray()
        DayOfWeek.entries.forEach { day ->
            val plan = program.forDay(day)
            array.put(
                JSONObject()
                    .put("day", day.value)
                    .put("deadlift", encodeExercise(plan.deadlift))
                    .put("rdl", encodeExercise(plan.rdl))
            )
        }
        return array.toString()
    }

    fun decode(json: String?): WeeklyProgram {
        if (json.isNullOrBlank()) return WeeklyProgram.default()
        return try {
            val defaults = WeeklyProgram.default()
            val array = JSONArray(json)
            val parsed = mutableMapOf<DayOfWeek, DayPlan>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val dayValue = item.optInt("day", 0)
                val day = DayOfWeek.entries.firstOrNull { it.value == dayValue } ?: continue
                val fallback = defaults.forDay(day)
                parsed[day] = DayPlan(
                    dayOfWeek = day,
                    deadlift = decodeExercise(item.optJSONObject("deadlift"), fallback.deadlift),
                    rdl = decodeExercise(item.optJSONObject("rdl"), fallback.rdl)
                )
            }
            WeeklyProgram(DayOfWeek.entries.map { parsed[it] ?: defaults.forDay(it) })
        } catch (_: Exception) {
            WeeklyProgram.default()
        }
    }

    private fun encodeExercise(plan: PlannedExercise): JSONObject = JSONObject()
        .put("sets", plan.sets)
        .put("reps", plan.reps)
        .put("minWeightKg", plan.minWeightKg)
        .put("maxWeightKg", plan.maxWeightKg)

    private fun decodeExercise(json: JSONObject?, fallback: PlannedExercise): PlannedExercise {
        if (json == null) return fallback
        return PlannedExercise(
            sets = json.optInt("sets", fallback.sets),
            reps = json.optInt("reps", fallback.reps),
            minWeightKg = json.optDouble("minWeightKg", fallback.minWeightKg),
            maxWeightKg = json.optDouble("maxWeightKg", fallback.maxWeightKg)
        ).normalized()
    }
}
