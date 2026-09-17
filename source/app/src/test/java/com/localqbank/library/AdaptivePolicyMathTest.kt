package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePolicyMathTest {
    @Test fun userModelStartsColdBeforeTwentyAnswers() {
        assertEquals("cold", AdaptivePolicyMath.userModel(19, 0.99f))
    }

    @Test fun userModelClassifiesStrongAndWeakAtDocumentedThresholds() {
        assertEquals("strong", AdaptivePolicyMath.userModel(20, 0.80f))
        assertEquals("weak", AdaptivePolicyMath.userModel(20, 0.599f))
        assertEquals("steady", AdaptivePolicyMath.userModel(20, 0.60f))
    }

    @Test fun confidenceCapsAtHundred() {
        assertEquals(0, AdaptivePolicyMath.confidence(0, 0))
        assertEquals(25, AdaptivePolicyMath.confidence(10, 10))
        assertEquals(100, AdaptivePolicyMath.confidence(1000, 1000))
    }

    @Test fun navigationRewardUsesExactLatencyBands() {
        assertEquals(0.5, AdaptivePolicyMath.navigationReward(0), 0.0)
        assertEquals(1.0, AdaptivePolicyMath.navigationReward(40), 0.0)
        assertEquals(0.75, AdaptivePolicyMath.navigationReward(41), 0.0)
        assertEquals(0.75, AdaptivePolicyMath.navigationReward(120), 0.0)
        assertEquals(0.35, AdaptivePolicyMath.navigationReward(121), 0.0)
        assertEquals(0.35, AdaptivePolicyMath.navigationReward(300), 0.0)
        assertEquals(0.0, AdaptivePolicyMath.navigationReward(301), 0.0)
    }
}
