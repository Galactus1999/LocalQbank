package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestListPresentationTest {
    private fun test(count: Int, duration: Int = 0, series: String = "") =
        Test(id = "t1", title = "Anatomy", path = null, count = count, marks = 1.0, duration = duration, timePerQuestion = 0.0, seriesNumber = series)

    @Test fun notStarted_usesResumePositionFromProgress() {
        val p = TestListPresentation.present(0, test(100), ProgressSummary(100, 0, 0, 7), storedPosition = 42)
        assertEquals("1. Anatomy", p.title)
        assertEquals("Not started  •  100 questions", p.subtitle)
        assertEquals(7, p.resumePosition)
        assertTrue(p.showActions)
        assertFalse(p.inProgress)
    }

    @Test fun inProgress_usesStoredPositionAndMarksRowInProgress() {
        val p = TestListPresentation.present(3, test(100, 60, "2"), ProgressSummary(100, 25, 20, 5), storedPosition = 26)
        assertEquals("4. Anatomy", p.title)
        assertEquals("↻ In progress  •  25/100 solved  •  1 min  •  Series 2", p.subtitle)
        assertEquals(26, p.resumePosition)
        assertTrue(p.showActions)
        assertTrue(p.inProgress)
    }

    @Test fun completed_clampsSolvedToTotalForDisplay() {
        val p = TestListPresentation.present(0, test(10), ProgressSummary(10, 99, 0, 0), storedPosition = 8)
        assertEquals("✓ Completed  •  10/10 solved", p.subtitle)
        assertFalse(p.inProgress)
    }

    @Test fun emptyTest_hidesActions() {
        val p = TestListPresentation.present(0, test(0), ProgressSummary(0, 0, 0, 0), storedPosition = 0)
        assertEquals("No questions", p.subtitle)
        assertFalse(p.showActions)
        assertFalse(p.inProgress)
    }
}
