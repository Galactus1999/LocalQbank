package com.localqbank.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BenAiResourceGateTest {
    @Test fun lowMemoryBlocksModel() {
        assertFalse(BenAiResourceGate.mayRun(true, false, 700, 1100, 0, false, true))
    }

    @Test fun thermalPressureBlocksModel() {
        assertFalse(BenAiResourceGate.mayRun(true, false, 500, 2000, 3, false, true))
    }

    @Test fun powerSaveBlocksLargeModel() {
        assertFalse(BenAiResourceGate.mayRun(true, false, 513, 3000, 0, true, true))
    }

    @Test fun backgroundWorkBlocksModel() {
        assertFalse(BenAiResourceGate.mayRun(true, false, 500, 3000, 0, false, false))
    }

    @Test fun healthyForegroundDeviceAllowsModel() {
        assertTrue(BenAiResourceGate.mayRun(true, false, 500, 2000, 0, false, true))
    }

    @Test fun moderateThermalReducesPromptBudget() {
        assertEquals(8000, BenAiResourceGate.promptBudgetChars(0))
        assertEquals(5000, BenAiResourceGate.promptBudgetChars(2))
    }

    @Test fun severeThermalRemovesNeuralPromptBudget() {
        assertEquals(0, BenAiResourceGate.promptBudgetChars(3))
    }
}
