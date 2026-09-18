package com.localqbank.library

/**
 * Pure SM-2 style spaced-repetition scheduler.
 *
 * Extracted from [ProgressStore.setAnswer] (previously inline in QBankDb.kt) so the scheduling
 * math can be unit-tested without any Android / SharedPreferences dependency. This is a
 * behavior-preserving extraction: the formula, rounding, and edge cases below are copied
 * verbatim from the original implementation. No scheduling outcome should change for any
 * existing user's progress data.
 *
 * Design notes (carried over from the original inline comments):
 * - Quality is binary, not the 0-5 recall grade SM-2 normally uses, because that's what this
 *   app's UI actually captures (correct vs. wrong). We map to the two grades that matter most.
 * - A miss resets the streak and brings the question back tomorrow, regardless of how well it
 *   was doing before - that's the whole point of spaced repetition catching weak spots.
 * - Ease factor has a floor of 1.3, matching the standard SM-2 algorithm.
 */
object SpacedRepetitionScheduler {

    const val DAY_MS = 86_400_000L
    private const val MIN_EASE_FACTOR = 1.3f
    private const val DEFAULT_EASE_FACTOR = 2.5f

    /** Current schedule state for a single question, plus whether the latest attempt was correct. */
    data class ScheduleInput(
        val correct: Boolean,
        val easeFactor: Float = DEFAULT_EASE_FACTOR,
        val repetitions: Int = 0,
        val previousIntervalDays: Float = 0f
    )

    /** The updated schedule state to persist after an attempt. */
    data class ScheduleResult(
        val easeFactor: Float,
        val repetitions: Int,
        val intervalDays: Float,
        val nextDueAt: Long
    )

    /**
     * Computes the next SM-2 state given the current state and answer correctness.
     * [now] is injectable for deterministic testing; defaults to the real clock.
     */
    fun schedule(input: ScheduleInput, now: Long = System.currentTimeMillis()): ScheduleResult {
        val quality = if (input.correct) 5 else 2

        var reps = input.repetitions
        val intervalDays: Float

        if (quality < 3) {
            reps = 0
            intervalDays = 1f
        } else {
            reps += 1
            intervalDays = when (reps) {
                1 -> 1f
                2 -> 6f
                else -> (if (input.previousIntervalDays > 0f) input.previousIntervalDays else 6f) * input.easeFactor
            }
        }

        val newEaseFactor = (input.easeFactor + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f)))
            .coerceAtLeast(MIN_EASE_FACTOR)

        val nextDueAt = now + (intervalDays * DAY_MS).toLong()

        return ScheduleResult(
            easeFactor = newEaseFactor,
            repetitions = reps,
            intervalDays = intervalDays,
            nextDueAt = nextDueAt
        )
    }
}
