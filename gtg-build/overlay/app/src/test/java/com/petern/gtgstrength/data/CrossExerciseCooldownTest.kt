package com.petern.gtgstrength.data

import com.petern.gtgstrength.domain.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossExerciseCooldownTest {
    private val loggedAt = 1_700_000_000_000L

    @Test
    fun defaultCrossGapIsDisabled() {
        val settings = TrainingSettings(
            deadliftNextLogAllowedAtMillis = loggedAt + 60_000L,
            rdlCooldownSourceLogTimestamp = loggedAt
        )

        assertEquals(
            loggedAt + 60_000L,
            settings.nextLogAllowedAtMillis(Exercise.DEADLIFT)
        )
    }

    @Test
    fun deadliftLogBlocksOnlyRdlForConfiguredGap() {
        val settings = TrainingSettings(
            crossExerciseCooldownMinutes = 30,
            deadliftNextLogAllowedAtMillis = loggedAt + 60 * 60_000L,
            deadliftCooldownSourceLogTimestamp = loggedAt
        )

        assertEquals(
            loggedAt + 30 * 60_000L,
            settings.nextLogAllowedAtMillis(Exercise.RDL)
        )
        assertEquals(
            loggedAt + 60 * 60_000L,
            settings.nextLogAllowedAtMillis(Exercise.DEADLIFT)
        )
    }

    @Test
    fun rdlLogBlocksDeadliftEvenWhenItsOwnTimerIsDisabled() {
        val settings = TrainingSettings(
            crossExerciseCooldownMinutes = 45,
            rdlCooldownMinutes = 0,
            rdlNextLogAllowedAtMillis = 0L,
            rdlCooldownSourceLogTimestamp = loggedAt
        )

        assertEquals(
            loggedAt + 45 * 60_000L,
            settings.nextLogAllowedAtMillis(Exercise.DEADLIFT)
        )
        assertEquals(0L, settings.nextLogAllowedAtMillis(Exercise.RDL))
    }

    @Test
    fun longerRemainingTimerAlwaysWins() {
        val settings = TrainingSettings(
            crossExerciseCooldownMinutes = 120,
            deadliftNextLogAllowedAtMillis = loggedAt + 60 * 60_000L,
            rdlCooldownSourceLogTimestamp = loggedAt,
            rdlNextLogAllowedAtMillis = loggedAt + 180 * 60_000L,
            deadliftCooldownSourceLogTimestamp = loggedAt
        )

        assertEquals(
            loggedAt + 120 * 60_000L,
            settings.nextLogAllowedAtMillis(Exercise.DEADLIFT)
        )
        assertEquals(
            loggedAt + 180 * 60_000L,
            settings.nextLogAllowedAtMillis(Exercise.RDL)
        )
    }

    @Test
    fun disablingCrossGapUnlocksOppositeExercise() {
        val settings = TrainingSettings(
            crossExerciseCooldownMinutes = 0,
            deadliftCooldownSourceLogTimestamp = loggedAt
        )

        assertEquals(0L, settings.nextLogAllowedAtMillis(Exercise.RDL))
    }
}
