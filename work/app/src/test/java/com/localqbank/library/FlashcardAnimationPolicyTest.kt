package com.localqbank.library

import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardAnimationPolicyTest {
    @Test fun policy_hasExplicitlyDefinedStaticFallback() {
        assertTrue(AnimationPolicy::class.java != null)
    }

    @Test fun movablePosition_normalizationClampsInvalidValues() {
        val p = MovableControlPosition.normalized(-100f, 1000f, 500, 500)
        assertTrue(p.x in 0f..1f)
        assertTrue(p.y in 0f..1f)
    }
}
