package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises FakeProgressRepository against the ProgressRepository contract to confirm it's a
 * valid stand-in for ProgressStore in tests - the actual payoff of extracting the interface.
 * Any class written against ProgressRepository (like StudyStateRepository) can now be unit
 * tested with this fake instead of needing a real Context/SharedPreferences.
 */
class ProgressRepositoryContractTest {

    @Test
    fun unattemptedQuestion_isAlwaysDue() {
        val repo = FakeProgressRepository()
        assertTrue(repo.isDue("q1"))
    }

    @Test
    fun correctAnswer_recordsStatusAndSchedulesFutureReview() {
        val repo = FakeProgressRepository()
        repo.now = 1_000_000L
        repo.setAnswer("q1", "A", "correct")

        assertEquals("correct", repo.status("q1"))
        assertEquals("A", repo.selected("q1"))
        assertEquals(1, repo.attempts("q1"))
        assertFalse("just answered, shouldn't be due again yet", repo.isDue("q1"))
    }

    @Test
    fun questionBecomesDueOnceScheduledTimeArrives() {
        val repo = FakeProgressRepository()
        repo.now = 1_000_000L
        repo.setAnswer("q1", "A", "correct")
        val nextDue = repo.nextDue("q1")

        repo.now = nextDue - 1
        assertFalse(repo.isDue("q1"))

        repo.now = nextDue
        assertTrue(repo.isDue("q1"))
    }

    @Test
    fun wrongAnswer_resetsRepetitionsAndComesBackTomorrow() {
        val repo = FakeProgressRepository()
        repo.now = 0L
        repo.setAnswer("q1", "A", "correct")
        repo.setAnswer("q1", "A", "correct")
        assertTrue(repo.repetitions("q1") >= 2)

        repo.setAnswer("q1", "B", "wrong")
        assertEquals(0, repo.repetitions("q1"))
        assertEquals(1f, repo.intervalDays("q1"))
    }

    @Test
    fun bookmarkAndReview_roundTrip() {
        val repo = FakeProgressRepository()
        repo.setBookmark("q1", "flag")
        repo.setReview("q1", true)
        assertEquals("flag", repo.bookmark("q1"))
        assertTrue(repo.review("q1"))
    }

    @Test
    fun clear_removesAllStateForAQuestion() {
        val repo = FakeProgressRepository()
        repo.setAnswer("q1", "A", "correct")
        repo.setBookmark("q1", "flag")
        repo.clear("q1")

        assertEquals(null, repo.status("q1"))
        assertEquals(null, repo.bookmark("q1"))
        assertEquals(0, repo.attempts("q1"))
    }
}
