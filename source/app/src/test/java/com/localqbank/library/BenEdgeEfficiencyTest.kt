package com.localqbank.library

import org.junit.Assert.*
import org.junit.Test

class BenEdgeEfficiencyTest {
    @Test fun contextWindowIsBoundedAndPreservesEvidenceOrder() {
        val w = BenContextWindowPolicy.build(
            query = "mammography screening",
            examContext = "screening age",
            evidence = listOf("[1] first evidence", "[2] second evidence", "[3] third evidence"),
            maxChars = 2000
        )
        assertTrue(w.text.contains("[1] first evidence"))
        assertTrue(w.text.contains("[2] second evidence"))
        assertTrue(w.estimatedChars <= 2000)
    }

    @Test fun contextualRetentionPolicyStaysNeutralBeforeMinimumSamples() {
        val policy = BenContextualRetentionPolicy()
        val context = BenContextualRetentionPolicy.Context(.5, 1000.0, .5, .7, 2.0)
        assertEquals(1.0, policy.multiplier(context, BenContextualRetentionPolicy.State(29, 20.0, .69), true), 0.0001)
    }

    @Test fun contextualRetentionPolicyIsDisabledByDefault() {
        val policy = BenContextualRetentionPolicy()
        val context = BenContextualRetentionPolicy.Context(.2, 500.0, .3, .8, 3.0)
        assertEquals(1.0, policy.multiplier(context, BenContextualRetentionPolicy.State(100, 80.0, .8), false), 0.0001)
    }

    @Test fun efficiencyPolicyDoesNotPretendUnsupportedZeroCopyOrSpeculativeSupport() {
        val s = BenInferenceEfficiencyPolicy.snapshot()
        assertFalse(s.speculativeDecoding)
        assertFalse(s.zeroCopyTensorIpc)
        assertTrue(s.runtimeOwnsKvCache)
    }
}
