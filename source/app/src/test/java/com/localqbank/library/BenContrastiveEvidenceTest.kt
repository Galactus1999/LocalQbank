package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenContrastiveEvidenceTest {
    @Test fun boundedDropsWrongRolesAndCapsEachEvidenceClass() {
        val bundle = BenContrastiveEvidence.Bundle(
            question = List(7) { BenContrastiveEvidence.Item(BenContrastiveEvidence.Role.QUESTION_EVIDENCE, it.toLong(), "q") },
            distractors = List(7) { BenContrastiveEvidence.Item(BenContrastiveEvidence.Role.DISTRACTOR_EVIDENCE, it.toLong(), "d") },
            answers = List(7) { BenContrastiveEvidence.Item(BenContrastiveEvidence.Role.ANSWER_EVIDENCE, it.toLong(), "a") },
        )
        val bounded = bundle.bounded(2, 3, 1)
        assertEquals(2, bounded.question.size)
        assertEquals(3, bounded.distractors.size)
        assertEquals(1, bounded.answers.size)
        assertTrue(bounded.all().all { it.role == BenContrastiveEvidence.Role.QUESTION_EVIDENCE || it.role == BenContrastiveEvidence.Role.DISTRACTOR_EVIDENCE || it.role == BenContrastiveEvidence.Role.ANSWER_EVIDENCE })
    }

    @Test fun negativeCapsBecomeZero() {
        val item = BenContrastiveEvidence.Item(BenContrastiveEvidence.Role.ANSWER_EVIDENCE, 1, "x")
        val bounded = BenContrastiveEvidence.Bundle(answers = listOf(item)).bounded(-1, -1, -1)
        assertTrue(bounded.all().isEmpty())
    }
}
