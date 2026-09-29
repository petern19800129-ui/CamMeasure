package com.petern.gtgstrength.data

data class LogTimerDecision(
    val skipOwnCooldownAfterLog: Boolean,
    val startCrossExerciseGapAfterLog: Boolean
)

object LogTimerPolicy {
    /**
     * Determines which timers should start after the next successful planned set.
     *
     * When "skip after final planned set" is enabled, the exercise's own timer
     * is suppressed when this log reaches its daily target. The cross-exercise
     * gap is retained only while the other exercise still has planned sets left.
     */
    fun afterNextPlannedSet(
        skipAfterFinalPlannedSet: Boolean,
        completedSetsBefore: Int,
        plannedSets: Int,
        otherCompletedSetsBefore: Int,
        otherPlannedSets: Int
    ): LogTimerDecision {
        val reachesFinalPlannedSet =
            plannedSets > 0 && completedSetsBefore + 1 >= plannedSets

        if (!skipAfterFinalPlannedSet || !reachesFinalPlannedSet) {
            return LogTimerDecision(
                skipOwnCooldownAfterLog = false,
                startCrossExerciseGapAfterLog = true
            )
        }

        val otherExerciseStillHasSets =
            otherPlannedSets > 0 && otherCompletedSetsBefore < otherPlannedSets

        return LogTimerDecision(
            skipOwnCooldownAfterLog = true,
            startCrossExerciseGapAfterLog = otherExerciseStillHasSets
        )
    }
}
