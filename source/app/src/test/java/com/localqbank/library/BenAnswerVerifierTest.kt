package com.localqbank.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenAnswerVerifierTest {
    private val verifier = BenAnswerVerifier()
    private val plan = BenCognitiveArchitecture.Plan(
        query = "mammography", intent = "screening", concepts = listOf("mammography"),
        domains = emptySet(), tools = emptyList(), confidence = 70
    )
    private val evidence = listOf(
        BenAnswerVerifier.EvidenceItem(1, 101L, "Screening mammography is recommended beginning at age 40 years. Dose information is not applicable.")
    )

    @Test fun supportedCriticalAgeIsAccepted() {
        val r = verifier.verify("mammography", "Mammography screening begins at age 40 years. [1]", plan, evidence)
        assertTrue(r.acceptable)
        assertTrue(r.criticalClaims.all { it.status == BenAnswerVerifier.ClaimStatus.SUPPORTED })
    }

    @Test fun wrongCriticalAgeIsRejected() {
        val r = verifier.verify("mammography", "Mammography screening begins at age 50 years. [1]", plan, evidence)
        assertFalse(r.acceptable)
    }

    @Test fun inventedCitationIsRejected() {
        val r = verifier.verify("mammography", "Mammography screening begins at age 40 years. [7]", plan, evidence)
        assertFalse(r.acceptable)
        assertFalse(r.citationValid)
    }

    @Test fun missingCitationForCriticalNumberIsRejected() {
        val r = verifier.verify("mammography", "Mammography screening begins at age 40 years.", plan, evidence)
        assertFalse(r.acceptable)
    }

    @Test fun explicitDaysWeeksEquivalenceIsAccepted() {
        val e = listOf(BenAnswerVerifier.EvidenceItem(1, 102L, "Treatment duration is 2 weeks for the condition."))
        val p = plan.copy(query = "condition", concepts = listOf("condition"), intent = "management")
        val r = verifier.verify("condition", "Treatment duration is 14 days. [1]", p, e)
        assertTrue(r.acceptable)
    }

    @Test fun explicitMgGramEquivalenceIsAccepted() {
        val e = listOf(BenAnswerVerifier.EvidenceItem(1, 103L, "The dose is 1 g."))
        val p = plan.copy(query = "drug", concepts = listOf("drug"), intent = "management")
        val r = verifier.verify("drug", "The dose is 1000 mg. [1]", p, e)
        assertTrue(r.acceptable)
    }

    @Test fun unsupportedCriticalCutoffIsRejected() {
        val e = listOf(BenAnswerVerifier.EvidenceItem(1, 104L, "The test is useful in suspected disease."))
        val p = plan.copy(query = "test", concepts = listOf("test"), intent = "diagnostic")
        val r = verifier.verify("test", "A cutoff of 20 is diagnostic. [1]", p, e)
        assertFalse(r.acceptable)
    }

    @Test fun ordinaryNonCriticalAnswerCanPass() {
        val r = verifier.verify("mammography", "Mammography is an imaging investigation used in breast screening. [1]", plan, evidence)
        assertTrue(r.acceptable)
    }

    @Test fun criticalRecommendationNeedsNearbyCitation() {
        val r = verifier.verify("mammography", "The drug of choice is Drug X. [1]", plan, evidence)
        assertFalse(r.acceptable)
    }
    @Test fun supportedPercentageWithPunctuationIsAccepted() {
        val e = listOf(BenAnswerVerifier.EvidenceItem(1, 105L, "The test has a sensitivity of 50%."))
        val p = plan.copy(query = "test", concepts = listOf("test"), intent = "diagnostic")
        val r = verifier.verify("test", "The test has a sensitivity of 50%. [1]", p, e)
        assertTrue(r.acceptable)
    }

    @Test fun wrongPercentageIsRejected() {
        val e = listOf(BenAnswerVerifier.EvidenceItem(1, 106L, "The cutoff is 50%."))
        val p = plan.copy(query = "test", concepts = listOf("test"), intent = "diagnostic")
        val r = verifier.verify("test", "The cutoff is 60%. [1]", p, e)
        assertFalse(r.acceptable)
    }

}
