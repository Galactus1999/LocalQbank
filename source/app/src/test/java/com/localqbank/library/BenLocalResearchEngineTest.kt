package com.localqbank.library

import org.junit.Assert.assertTrue
import org.junit.Test

class BenLocalResearchEngineTest {
    @Test fun capabilityIsOfflineAndModelOptional() {
        // Pure contract check: the shipped stability architecture must not require a model.
        val capability = BenLocalResearchEngine.Capability()
        assertTrue(capability.offlineOnly)
        assertTrue(capability.groundedInLocalQBank)
        assertTrue(!capability.modelBackendAvailable)
    }
}
