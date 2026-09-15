package com.petern.gtgstrength.data

import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

data class PlateStock(
    val weightKg: Double,
    val count: Int
)

data class BarbellEquipment(
    val barWeightKg: Double = 20.0,
    val plates: List<PlateStock> = defaultPlateRows()
) {
    fun normalized(): BarbellEquipment {
        val merged = plates
            .map {
                PlateStock(
                    weightKg = it.weightKg.coerceIn(0.01, 100.0),
                    count = it.count.coerceIn(0, 100)
                )
            }
            .groupBy { (it.weightKg * 100.0).roundToInt() }
            .map { (units, rows) ->
                PlateStock(
                    weightKg = units / 100.0,
                    count = rows.sumOf { it.count }.coerceAtMost(100)
                )
            }
            .sortedByDescending { it.weightKg }

        return BarbellEquipment(
            barWeightKg = barWeightKg.coerceIn(0.1, 100.0),
            plates = merged
        )
    }

    companion object {
        fun default(): BarbellEquipment = BarbellEquipment(
            barWeightKg = 20.0,
            plates = defaultPlateRows()
        )

        private fun defaultPlateRows(): List<PlateStock> =
            listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)
                .map { PlateStock(weightKg = it, count = 0) }
    }
}

data class PlateLoadItem(
    val weightKg: Double,
    val countPerSide: Int
)

data class PlateLoading(
    val targetWeightKg: Double,
    val actualWeightKg: Double,
    val barWeightKg: Double,
    val platesPerSide: List<PlateLoadItem>,
    val exact: Boolean,
    val configured: Boolean
) {
    val differenceKg: Double get() = actualWeightKg - targetWeightKg
}

object PlateCalculator {
    private const val SCALE = 100

    fun calculate(targetWeightKg: Double, equipment: BarbellEquipment): PlateLoading {
        val safeTarget = targetWeightKg.coerceAtLeast(0.0)
        val setup = equipment.normalized()
        val bar = setup.barWeightKg

        if (safeTarget <= bar + 0.001) {
            return PlateLoading(
                targetWeightKg = safeTarget,
                actualWeightKg = bar,
                barWeightKg = bar,
                platesPerSide = emptyList(),
                exact = abs(safeTarget - bar) < 0.01,
                configured = true
            )
        }

        val usable = setup.plates
            .filter { it.weightKg > 0.0 && it.count >= 2 }
            .map { stock -> stock to (stock.count / 2) }

        if (usable.isEmpty()) {
            return PlateLoading(
                targetWeightKg = safeTarget,
                actualWeightKg = safeTarget,
                barWeightKg = bar,
                platesPerSide = emptyList(),
                exact = false,
                configured = false
            )
        }

        val targetPerSideUnits = (((safeTarget - bar) / 2.0) * SCALE).roundToInt().coerceAtLeast(0)
        val weights = usable.map { (it.first.weightKg * SCALE).roundToInt().coerceAtLeast(1) }
        val maxSingleUnits = weights.maxOrNull() ?: 1
        val cap = targetPerSideUnits + maxSingleUnits

        val combinations = mutableMapOf<Int, IntArray>()
        combinations[0] = IntArray(usable.size)

        usable.indices.forEach { index ->
            val plateUnits = weights[index]
            val maxPairs = usable[index].second
            val snapshot = combinations.entries.map { it.key to it.value.copyOf() }

            snapshot.forEach { (baseSum, baseCounts) ->
                for (quantity in 1..maxPairs) {
                    val newSum = baseSum + plateUnits * quantity
                    if (newSum > cap) break
                    if (newSum !in combinations) {
                        val newCounts = baseCounts.copyOf()
                        newCounts[index] += quantity
                        combinations[newSum] = newCounts
                    }
                }
            }
        }

        var bestSum = 0
        var bestCounts = combinations.getValue(0)
        var bestDifference = Int.MAX_VALUE
        var bestOverPenalty = Int.MAX_VALUE
        var bestPlateCount = Int.MAX_VALUE

        combinations.forEach { (sum, counts) ->
            val difference = abs(sum - targetPerSideUnits)
            val overPenalty = if (sum > targetPerSideUnits) 1 else 0
            val plateCount = counts.sum()
            val better = difference < bestDifference ||
                (difference == bestDifference && overPenalty < bestOverPenalty) ||
                (difference == bestDifference && overPenalty == bestOverPenalty && plateCount < bestPlateCount)

            if (better) {
                bestSum = sum
                bestCounts = counts
                bestDifference = difference
                bestOverPenalty = overPenalty
                bestPlateCount = plateCount
            }
        }

        val perSide = mutableListOf<PlateLoadItem>()
        for (index in bestCounts.indices) {
            val count = bestCounts[index]
            if (count > 0) {
                perSide += PlateLoadItem(
                    weightKg = usable[index].first.weightKg,
                    countPerSide = count
                )
            }
        }

        val actual = bar + 2.0 * (bestSum.toDouble() / SCALE)
        return PlateLoading(
            targetWeightKg = safeTarget,
            actualWeightKg = actual,
            barWeightKg = bar,
            platesPerSide = perSide,
            exact = bestSum == targetPerSideUnits,
            configured = true
        )
    }
}

object BarbellEquipmentCodec {
    fun encode(equipment: BarbellEquipment): String {
        val setup = equipment.normalized()
        val plates = JSONArray()
        setup.plates.forEach { plate ->
            plates.put(
                JSONObject()
                    .put("weightKg", plate.weightKg)
                    .put("count", plate.count)
            )
        }
        return JSONObject()
            .put("barWeightKg", setup.barWeightKg)
            .put("plates", plates)
            .toString()
    }

    fun decode(json: String?): BarbellEquipment {
        if (json.isNullOrBlank()) return BarbellEquipment.default()
        return try {
            val root = JSONObject(json)
            val rows = mutableListOf<PlateStock>()
            val plates = root.optJSONArray("plates") ?: JSONArray()
            for (index in 0 until plates.length()) {
                val item = plates.optJSONObject(index) ?: continue
                val weight = item.optDouble("weightKg", -1.0)
                val count = item.optInt("count", 0)
                if (weight > 0.0) rows += PlateStock(weight, count)
            }
            val parsed = BarbellEquipment(
                barWeightKg = root.optDouble("barWeightKg", 20.0),
                plates = if (rows.isEmpty()) BarbellEquipment.default().plates else rows
            )
            parsed.normalized()
        } catch (_: Exception) {
            BarbellEquipment.default()
        }
    }
}
