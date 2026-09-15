package com.petern.gtgstrength.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorTest {

    @Test
    fun exactBalancedLoadUsesAvailablePairs() {
        val equipment = BarbellEquipment(
            barWeightKg = 20.0,
            plates = listOf(
                PlateStock(10.0, 2),
                PlateStock(5.0, 2)
            )
        )

        val result = PlateCalculator.calculate(50.0, equipment)

        assertTrue(result.configured)
        assertTrue(result.exact)
        assertEquals(50.0, result.actualWeightKg, 0.001)
        assertEquals(2, result.platesPerSide.size)
    }

    @Test
    fun nearestLoadIsReturnedWhenExactTargetIsImpossible() {
        val equipment = BarbellEquipment(
            barWeightKg = 20.0,
            plates = listOf(PlateStock(15.0, 2))
        )

        val result = PlateCalculator.calculate(40.0, equipment)

        assertTrue(result.configured)
        assertFalse(result.exact)
        assertEquals(50.0, result.actualWeightKg, 0.001)
    }

    @Test
    fun equalDifferencePrefersLighterLoad() {
        val equipment = BarbellEquipment(
            barWeightKg = 20.0,
            plates = listOf(PlateStock(10.0, 2))
        )

        val result = PlateCalculator.calculate(30.0, equipment)

        assertFalse(result.exact)
        assertEquals(20.0, result.actualWeightKg, 0.001)
    }

    @Test
    fun targetEqualToBarNeedsNoPlates() {
        val result = PlateCalculator.calculate(20.0, BarbellEquipment.default())

        assertTrue(result.configured)
        assertTrue(result.exact)
        assertEquals(20.0, result.actualWeightKg, 0.001)
        assertTrue(result.platesPerSide.isEmpty())
    }
}
