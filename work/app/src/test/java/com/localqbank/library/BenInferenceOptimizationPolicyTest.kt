package com.localqbank.library

import org.junit.Test
import org.junit.Assert.assertEquals

class BenInferenceOptimizationPolicyTest {
    @Test fun repeatedFailuresDisableNeural() {
        val d = BenInferenceOptimizationPolicy.decide(BenInferenceOptimizationPolicy.Sample(20_000, 100, 2000, 0, 3, true))
        assertEquals(BenInferenceOptimizationPolicy.Action.DISABLE_NEURAL, d.action)
    }

    @Test fun slowColdFastWarmKeepsRuntimeWarm() {
        val d = BenInferenceOptimizationPolicy.decide(BenInferenceOptimizationPolicy.Sample(20_000, 100, 2000, 0, 0, true))
        assertEquals(BenInferenceOptimizationPolicy.Action.KEEP_WARM, d.action)
    }

    @Test fun lowRamTrimsWarmRuntime() {
        val d = BenInferenceOptimizationPolicy.decide(BenInferenceOptimizationPolicy.Sample(20_000, 100, 400, 0, 0, true))
        assertEquals(BenInferenceOptimizationPolicy.Action.TRIM_AFTER_REQUEST, d.action)
    }
}
