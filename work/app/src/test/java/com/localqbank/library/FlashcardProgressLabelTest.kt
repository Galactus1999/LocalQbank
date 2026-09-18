package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashcardProgressLabelTest {
    @Test fun counterContinuesBeyondOneHundred() {
        assertEquals("101 / 250", FlashcardProgressLabel.counter(100, 250))
        assertEquals("250 / 250", FlashcardProgressLabel.counter(249, 250))
    }

    @Test fun percentageIsBoundedButCounterIsNot() {
        assertEquals(41, FlashcardProgressLabel.percentage(100, 250))
        assertEquals(100, FlashcardProgressLabel.percentage(249, 250))
    }

    @Test fun emptySetIsSafe() { assertEquals("0 / 0", FlashcardProgressLabel.counter(0, 0)) }

    @Test fun positionCannotExceedDisplayedTotal() {
        assertEquals("250 / 250", FlashcardProgressLabel.counter(999, 250))
        assertEquals(100, FlashcardProgressLabel.percentage(999, 250))
    }
}
