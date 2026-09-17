package com.localqbank.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenAiRuntimePolicyTest {
    @Test fun disabledAlwaysBlocks() {
        assertFalse(BenAiRuntimeGate.mayStart(false, false, 100))
    }

    @Test fun invalidModelSizeBlocks() {
        assertFalse(BenAiRuntimeGate.mayStart(true, false, 0))
        assertFalse(BenAiRuntimeGate.mayStart(true, false, -1))
    }

    @Test fun conservativeModeBlocksOversizedModel() {
        assertTrue(BenAiRuntimeGate.CONSERVATIVE_MODEL_MB_LIMIT == 1200)
        assertFalse(BenAiRuntimeGate.mayStart(true, true, 1201))
        assertTrue(BenAiRuntimeGate.mayStart(true, true, 1200))
    }

    @Test fun nonConservativeModeAllowsExplicitlyEnabledModel() {
        assertTrue(BenAiRuntimeGate.mayStart(true, false, 5000))
    }
}
