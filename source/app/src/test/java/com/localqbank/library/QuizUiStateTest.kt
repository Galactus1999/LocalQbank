package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizUiStateTest {
    @Test fun resetForNewSessionClearsTransientAnswerState() {
        val state = QuizUiState().apply {
            practiceAnsweredKey = "q-1"
            examRemainingMs = 42_000L
            examAnswers["q-1"] = true
            currentQuestion = null
        }

        state.resetForNewSession()

        assertEquals(null, state.practiceAnsweredKey)
        assertEquals(0L, state.examRemainingMs)
        assertTrue(state.examAnswers.isEmpty())
    }

    @Test fun sessionStateStartsWithSafeDefaults() {
        val state = QuizUiState()

        assertEquals("QBank", state.title)
        assertEquals(0, state.questionCount)
        assertEquals(0, state.position)
        assertTrue(state.collectionQuestionIds.isEmpty())
        assertTrue(state.examAnswers.isEmpty())
    }
}
