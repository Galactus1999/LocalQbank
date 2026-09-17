package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextualSrsShadowPolicyTest {
    @Test fun repeatedLapsesPreferConservativeShadowAction() {
        val r = ContextualSrsShadowPolicy().recommend(ContextualSrsShadowPolicy.Context(1, 1.5f, 2, 4, 3))
        assertEquals(ContextualSrsShadowPolicy.Action.CONSERVATIVE, r.action)
    }
    @Test fun matureStableCardCanPreferAccelerationInShadowOnly() {
        val r = ContextualSrsShadowPolicy().recommend(ContextualSrsShadowPolicy.Context(14, 2.9f, 6, 0, 0))
        assertEquals(ContextualSrsShadowPolicy.Action.ACCELERATED, r.action)
    }
}
