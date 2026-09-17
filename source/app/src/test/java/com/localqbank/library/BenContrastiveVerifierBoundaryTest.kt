package com.localqbank.library

import org.junit.Assert.assertFalse
import org.junit.Test

class BenContrastiveVerifierBoundaryTest {
    @Test
    fun distractorEvidenceIsNotPromotedToVerifierAuthority() {
        val bundle = BenContrastiveEvidencePlanner.plan(
            "What is the dose?",
            listOf(
                BenContrastiveEvidencePlanner.Candidate(1L, "authoritative dose is 5 mg", 0.9),
                BenContrastiveEvidencePlanner.Candidate(2L, "distractor dose is 999 mg", 0.1)
            ),
            maxAnswer = 1, maxDistractor = 1
        )
        val verifierEvidence = bundle.answers.mapIndexed { i, item ->
            BenAnswerVerifier.EvidenceItem(i + 1, item.sourceId, item.text)
        }
        val plan = BenCognitiveArchitecture.Plan(
            query = "What is the dose?", intent = "management", concepts = listOf("dose"),
            domains = emptySet(), tools = emptyList(), confidence = 70
        )
        val result = BenAnswerVerifier().verify(
            "What is the dose?", "The dose is 999 mg [1]", plan, verifierEvidence
        )
        assertFalse(result.acceptable)
    }
}
