package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the SM-2 scheduling behavior extracted from ProgressStore.setAnswer, so future
 * refactors of QBankDb.kt can't silently change a user's revision schedule.
 */
class SpacedRepetitionSchedulerTest {

    private val fixedNow = 1_700_000_000_000L
    private val dayMs = SpacedRepetitionScheduler.DAY_MS

    @Test
    fun firstCorrectAnswer_schedulesOneDayOut() {
        val result = SpacedRepetitionScheduler.schedule(
            SpacedRepetitionScheduler.ScheduleInput(correct = true, easeFactor = 2.5f, repetitions = 0, previousIntervalDays = 0f),
            now = fixedNow
        )
        assertEquals(1, result.repetitions)
        assertEquals(1f, result.intervalDays)
        assertEquals(fixedNow + dayMs, result.nextDueAt)
    }

    @Test
    fun secondCorrectAnswer_schedulesSixDaysOut() {
        val result = SpacedRepetitionScheduler.schedule(
            SpacedRepetitionScheduler.ScheduleInput(correct = true, easeFactor = 2.5f, repetitions = 1, previousIntervalDays = 1f),
            now = fixedNow
        )
        assertEquals(2, result.repetitions)
        assertEquals(6f, result.intervalDays)
    }

    @Test
    fun thirdCorrectAnswer_multipliesPreviousIntervalByEaseFactor() {
        val result = SpacedRepetitionScheduler.schedule(
            SpacedRepetitionScheduler.ScheduleInput(correct = true, easeFactor = 2.6f, repetitions = 2, previousIntervalDays = 6f),
            now = fixedNow
        )
        assertEquals(3, result.repetitions)
        assertEquals(6f * 2.6f, result.intervalDays)
    }

    @Test
    fun wrongAnswer_resetsRepsAndSchedulesTomorrow_regardlessOfStreak() {
        val result = SpacedRepetitionScheduler.schedule(
            SpacedRepetitionScheduler.ScheduleInput(correct = false, easeFactor = 2.8f, repetitions = 5, previousIntervalDays = 40f),
            now = fixedNow
        )
        assertEquals(0, result.repetitions)
        assertEquals(1f, result.intervalDays)
        assertEquals(fixedNow + dayMs, result.nextDueAt)
    }

    @Test
    fun wrongAnswer_stillLowersEaseFactor() {
        val result = SpacedRepetitionScheduler.schedule(
            SpacedRepetitionScheduler.ScheduleInput(correct = false, easeFactor = 2.5f, repetitions = 3, previousIntervalDays = 10f),
            now = fixedNow
        )
        assertTrue("ease factor should drop below the starting 2.5", result.easeFactor < 2.5f)
    }

    @Test
    fun easeFactor_neverDropsBelowFloor() {
        var ef = 1.3f
        // Repeated misses should clamp at the 1.3 floor, never go negative or below it.
        repeat(10) {
            val result = SpacedRepetitionScheduler.schedule(
                SpacedRepetitionScheduler.ScheduleInput(correct = false, easeFactor = ef, repetitions = 0, previousIntervalDays = 0f),
                now = fixedNow
            )
            ef = result.easeFactor
            assertTrue(ef >= 1.3f)
        }
    }

    @Test
    fun correctAnswer_increasesEaseFactor() {
        val result = SpacedRepetitionScheduler.schedule(
            SpacedRepetitionScheduler.ScheduleInput(correct = true, easeFactor = 2.5f, repetitions = 1, previousIntervalDays = 1f),
            now = fixedNow
        )
        assertTrue("a correct answer should nudge ease factor up from 2.5", result.easeFactor > 2.5f)
    }
}
