package com.petern.gtgstrength.data

import java.time.DayOfWeek
import kotlin.math.round
import org.json.JSONArray
import org.json.JSONObject

data class PlannedExercise(
    val sets: Int,
    val reps: Int,
    val minWeightKg: Double,
    val maxWeightKg: Double = minWeightKg
) {
    val isRange: Boolean get() = maxWeightKg > minWeightKg + 0.001
    val isRest: Boolean get() = sets <= 0

    fun normalized(): PlannedExercise {
        val safeSets = sets.coerceIn(0, 20)
        if (safeSets == 0) {
            return PlannedExercise(
                sets = 0,
                reps = 0,
                minWeightKg = 0.0,
                maxWeightKg = 0.0
            )
        }

        val low = minWeightKg.coerceIn(0.0, 2000.0)
        val high = maxWeightKg.coerceIn(low, 2000.0)
        return copy(
            sets = safeSets,
            reps = reps.coerceIn(1, 50),
            minWeightKg = low,
            maxWeightKg = high
        )
    }

    fun increasedByPercent(percent: Double): PlannedExercise {
        if (isRest) return this
        val factor = 1.0 + percent.coerceIn(0.0, 100.0) / 100.0
        fun scaled(value: Double): Double = round(value * factor * 100.0) / 100.0
        return copy(
            minWeightKg = scaled(minWeightKg),
            maxWeightKg = scaled(maxWeightKg)
        ).normalized()
    }
}

data class DayPlan(
    val dayOfWeek: DayOfWeek,
    val deadlift: PlannedExercise,
    val rdl: PlannedExercise
) {
    val isRestDay: Boolean get() = deadlift.isRest && rdl.isRest
}

data class WeeklyProgram(val days: List<DayPlan>) {
    fun forDay(day: DayOfWeek): DayPlan {
        if (day == DayOfWeek.SATURDAY) return restDay()
        return days.firstOrNull { it.dayOfWeek == day }
            ?: default().days.first { it.dayOfWeek == day }
    }

    val orderedDays: List<DayPlan>
        get() = WEEK_ORDER.map(::forDay)

    val deadliftWeeklySets: Int get() = WEEK_ORDER.sumOf { forDay(it).deadlift.sets }
    val rdlWeeklySets: Int get() = WEEK_ORDER.sumOf { forDay(it).rdl.sets }

    fun replacing(dayPlan: DayPlan): WeeklyProgram = WeeklyProgram(
        days = WEEK_ORDER.map { day ->
            when {
                day == DayOfWeek.SATURDAY -> restDay()
                day == dayPlan.dayOfWeek -> dayPlan.copy(
                    deadlift = dayPlan.deadlift.normalized(),
                    rdl = dayPlan.rdl.normalized()
                )
                else -> forDay(day)
            }
        }
    )

    fun increaseDeadliftByPercent(percent: Double): WeeklyProgram = WeeklyProgram(
        WEEK_ORDER.map { day ->
            if (day == DayOfWeek.SATURDAY) restDay()
            else forDay(day).let { it.copy(deadlift = it.deadlift.increasedByPercent(percent)) }
        }
    )

    fun increaseRdlByPercent(percent: Double): WeeklyProgram = WeeklyProgram(
        WEEK_ORDER.map { day ->
            if (day == DayOfWeek.SATURDAY) restDay()
            else forDay(day).let { it.copy(rdl = it.rdl.increasedByPercent(percent)) }
        }
    )

    companion object {
        val WEEK_ORDER: List<DayOfWeek> = listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
        )

        fun restDay(): DayPlan = DayPlan(
            dayOfWeek = DayOfWeek.SATURDAY,
            deadlift = PlannedExercise(0, 0, 0.0),
            rdl = PlannedExercise(0, 0, 0.0)
        )

        fun default(): WeeklyProgram = WeeklyProgram(
            listOf(
                DayPlan(DayOfWeek.SUNDAY, PlannedExercise(2, 1, 30.0, 35.0), PlannedExercise(1, 8, 20.0)),
                DayPlan(DayOfWeek.MONDAY, PlannedExercise(4, 1, 40.0), PlannedExercise(2, 6, 25.0)),
                DayPlan(DayOfWeek.TUESDAY, PlannedExercise(3, 1, 35.0), PlannedExercise(1, 8, 25.0)),
                DayPlan(DayOfWeek.WEDNESDAY, PlannedExercise(4, 1, 45.0), PlannedExercise(2, 6, 30.0)),
                DayPlan(DayOfWeek.THURSDAY, PlannedExercise(3, 1, 35.0), PlannedExercise(1, 8, 20.0, 25.0)),
                DayPlan(DayOfWeek.FRIDAY, PlannedExercise(4, 1, 40.0), PlannedExercise(2, 6, 30.0)),
                restDay()
            )
        )
    }
}

object WeeklyProgramCodec {
    fun encode(program: WeeklyProgram): String {
        val array = JSONArray()
        WeeklyProgram.WEEK_ORDER.forEach { day ->
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
                if (day == DayOfWeek.SATURDAY) continue
                val fallback = defaults.forDay(day)
                parsed[day] = DayPlan(
                    dayOfWeek = day,
                    deadlift = decodeExercise(item.optJSONObject("deadlift"), fallback.deadlift),
                    rdl = decodeExercise(item.optJSONObject("rdl"), fallback.rdl)
                )
            }
            WeeklyProgram(
                WeeklyProgram.WEEK_ORDER.map { day ->
                    if (day == DayOfWeek.SATURDAY) WeeklyProgram.restDay()
                    else parsed[day] ?: defaults.forDay(day)
                }
            )
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
