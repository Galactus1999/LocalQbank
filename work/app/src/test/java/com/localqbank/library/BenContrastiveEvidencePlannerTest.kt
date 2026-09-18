package com.localqbank.library

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class BenContrastiveEvidencePlannerTest {
    @Test
    fun plannerSeparatesStrongAndLowerRankedEvidence() {
        val candidates = List(10) { i -> BenContrastiveEvidencePlanner.Candidate(i.toLong(), "candidate $i") }
        val bundle = BenContrastiveEvidencePlanner.plan("question", candidates, maxAnswer = 4, maxDistractor = 4)
        assertEquals(1, bundle.question.size)
        assertEquals(4, bundle.answers.size)
        assertEquals(4, bundle.distractors.size)
        assertTrue(bundle.answers.all { it.role == BenContrastiveEvidence.Role.ANSWER_EVIDENCE })
        assertTrue(bundle.distractors.all { it.role == BenContrastiveEvidence.Role.DISTRACTOR_EVIDENCE })
    }

    @Test
    fun plannerOrdersByScoreDeduplicatesAndRespectsCharacterBudget() {
        val candidates = listOf(
            BenContrastiveEvidencePlanner.Candidate(1L, "low score", 0.1),
            BenContrastiveEvidencePlanner.Candidate(2L, "high score", 0.9),
            BenContrastiveEvidencePlanner.Candidate(2L, "high score duplicate", 0.8),
            BenContrastiveEvidencePlanner.Candidate(3L, "third", 0.5)
        )
        val bundle = BenContrastiveEvidencePlanner.plan("q", candidates, 2, 2, 20)
        assertEquals(listOf(2L, 3L), bundle.answers.map { it.sourceId })
        assertTrue(bundle.totalChars() <= 20)
        assertTrue(bundle.distractors.none { it.sourceId in bundle.answers.map { a -> a.sourceId } })
    }

    @Test
    fun blankQuestionDoesNotCreateSyntheticEvidence() {
        val bundle = BenContrastiveEvidencePlanner.plan("  ", listOf(BenContrastiveEvidencePlanner.Candidate(1L, "candidate")))
        assertTrue(bundle.question.isEmpty())
    }
}
